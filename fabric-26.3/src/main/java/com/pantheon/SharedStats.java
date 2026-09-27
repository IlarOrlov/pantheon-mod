package com.pantheon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Shares current health and/or hunger across every online member of the same
 * {@link Team}, each independently toggleable via {@link PantheonConfig}.
 * Unlike the shared inventory (a single backing list every player's
 * {@code Inventory} points at), health and hunger aren't simple collections
 * we can alias - they're per-entity synced values - so instead this polls
 * once a server tick, per team: whichever online teammate's value no longer
 * matches what was last synced to them is treated as the source of a fresh
 * change (damage, healing, eating, a fresh join adopting the existing pool,
 * ...), and that new value is then applied to the rest of the team. The one
 * deliberate exception is a single player individually respawning while the
 * pool never actually hit 0 as a whole - vanilla always hands out full health
 * on respawn regardless of what the pool currently is, so treating that as a
 * fresh change would heal every other, already-damaged teammate back up
 * just because this one player finally respawned. That player adopts the
 * pool's current ("worst") value instead of resetting it to their own.
 *
 * <p>When {@link PantheonConfig#syncHealth} is on, a team's shared health pool
 * reaching 0 kills every online teammate at once, through the real death
 * pipeline ({@code setHealth(0)} + {@link net.minecraft.world.entity.LivingEntity#die})
 * so respawning actually works - {@code Entity#kill} looked tempting but only
 * force-removes the entity, without ever presenting a respawn screen. The
 * teammate whose own damage actually emptied the pool dies with the normal
 * death message; everyone else on the team who goes down purely because the
 * pool did gets a random joke death message instead
 * ({@link FunnyMessages#randomPropagatedDeath}), since nothing actually hit
 * them. The team's shared inventory is dropped and cleared exactly once for
 * the whole event (not once per dying player, which would duplicate every
 * item), since {@code keepInventory} being forced on would otherwise just let
 * it survive a death that's supposed to actually end the run.
 */
public final class SharedStats {
	/** A member's remaining duration on an effect has to be above this to count a disappearance as a deliberate cure rather than it just running out. */
	private static final int CURE_DETECTION_THRESHOLD_TICKS = 3;

	private SharedStats() {
	}

	/**
	 * Drops this player's tracked effect durations so the very next
	 * {@link #tickEffects} doesn't mistake a fresh death-respawn - which
	 * hands out a brand-new entity with no active effects at all, regardless
	 * of what the old one had running - for a deliberate cure of whatever
	 * was active before they died. With no "previous" entry left for their
	 * UUID, the existing not-tracked-yet branch in {@link #tickEffects}
	 * (identical to a fresh join) takes over instead, letting them silently
	 * re-adopt the team's aggregate on the next tick rather than being
	 * excluded from it. Called from {@link PantheonMod}'s
	 * {@code ServerPlayerEvents.AFTER_RESPAWN} listener for an actual death
	 * (not a dimension-change respawn, which does carry effects over).
	 */
	public static void onRespawn(final ServerPlayer player) {
		TeamManager.teamOf(player).lastMemberEffectDurations.remove(player.getUUID());
	}

	public static void tick(final Map<Team, List<ServerPlayer>> onlineByTeam) {
		PantheonConfig config = PantheonConfig.get();

		for (Map.Entry<Team, List<ServerPlayer>> entry : onlineByTeam.entrySet()) {
			Team team = entry.getKey();
			List<ServerPlayer> online = entry.getValue();

			if (config.syncHealth) {
				tickHealth(team, online);
			} else if (team.sharedHealth != null) {
				team.sharedHealth = null;
				team.sharedDeathHandled = false;
				team.lastSyncedHealth.clear();
			}

			if (config.syncHunger) {
				tickHunger(team, online);
			} else if (team.sharedFood != null) {
				team.sharedFood = null;
				team.sharedSaturation = null;
				team.lastSyncedFood.clear();
				team.lastSyncedSaturation.clear();
			}

			if (config.syncExperience) {
				tickExperience(team, online);
			} else if (team.sharedExperienceLevel != null) {
				team.sharedExperienceLevel = null;
				team.sharedExperienceProgress = null;
				team.lastSyncedExperienceLevel.clear();
				team.lastSyncedExperienceProgress.clear();
			}

			if (config.syncEffects) {
				tickEffects(team, online);
			} else if (!team.lastMemberEffectDurations.isEmpty() || !team.effectCureExclusions.isEmpty()) {
				team.lastMemberEffectDurations.clear();
				team.effectCureExclusions.clear();
			}
		}
	}

	private static void tickHealth(final Team team, final List<ServerPlayer> online) {
		if (online.isEmpty()) {
			return;
		}
		team.lastSyncedHealth.keySet().retainAll(uuids(online));

		// Tracks whichever online teammate's own health change is the reason the
		// pool moved this tick (real damage/healing), so a lethal pool value can
		// tell that player apart from everyone else who only dies because the
		// pool does - those get a joke death message instead of a real one.
		ServerPlayer sourceOfChange = null;

		if (team.sharedHealth != null && team.sharedHealth <= 0f) {
			// Settled dead-pool state: everyone who was online got killed for it
			// already, so don't force anyone down further. A respawning player
			// gets a fresh entity instance with no recorded "previous" value -
			// indistinguishable, by that alone, from a brand new joiner who
			// should instead *adopt* the pool - so instead of comparing against
			// history here, just watch for the first player with real positive
			// health again: that's the revival signal, and it becomes the new
			// pool value rather than getting immediately pulled back down to
			// the lethal one. (Entity#isAlive() is just "not removed" - a player
			// sitting on the death screen, not yet respawned, is still "alive"
			// by that definition, so it can't be used to detect this.)
			for (ServerPlayer player : online) {
				if (player.getHealth() > 0f) {
					team.sharedHealth = player.getHealth();
					team.sharedDeathHandled = false;
					break;
				}
			}
		} else {
			for (ServerPlayer player : online) {
				Float previous = team.lastSyncedHealth.get(player.getUUID());
				if (previous == null) {
					continue;
				}
				float currentHealthNow = player.getHealth();
				if (previous.floatValue() <= 0f && currentHealthNow > 0f) {
					// This one player individually respawning, while the
					// team's pool never actually hit 0 as a whole (everyone
					// else stayed alive and may have taken damage since) -
					// a fresh respawn always hands out full health regardless
					// of what the shared pool currently is, so treating that
					// as "the new pool value" would heal every other, already
					// -damaged teammate back up just because this one player
					// finally got around to respawning. They should adopt
					// the pool instead (handled below), not drive it.
					continue;
				}
				if (previous.floatValue() != currentHealthNow) {
					team.sharedHealth = currentHealthNow;
					sourceOfChange = player;
				}
			}
			if (team.sharedHealth == null) {
				team.sharedHealth = online.get(0).getHealth();
			}

			if (team.sharedHealth <= 0f && !team.sharedDeathHandled) {
				ServerPlayer anchor = sourceOfChange != null ? sourceOfChange : online.get(0);
				dropSharedInventoryOnce(team, anchor);
				team.sharedDeathHandled = true;
			}
		}

		for (ServerPlayer player : online) {
			UUID uuid = player.getUUID();
			float currentHealth = player.getHealth();

			if (!team.lastSyncedHealth.containsKey(uuid) && currentHealth <= 0f) {
				// Never tracked before, and reading as already-dead the very
				// first time we see them - a fresh join's health can briefly
				// read as an uninitialized 0 for a tick or two before
				// Minecraft properly sets it, and that's indistinguishable
				// from a real death by value alone. A brand new player is
				// never legitimately already dead, so don't start tracking
				// them (and don't let this poison the pool) until we've seen
				// a real, positive reading from them.
				continue;
			}

			if (currentHealth <= 0f) {
				// Already dead and awaiting their own respawn click - forcing
				// setHealth on them wouldn't actually respawn them, just leave
				// health and death-screen state inconsistent. Leave them alone;
				// they'll fall into the branch above once they do respawn.
				//
				// Track their REAL (still-dead) value here, not sharedHealth -
				// if someone else revived in the meantime, sharedHealth is now
				// a healthy number that doesn't apply to this player yet, and
				// recording it as if it did would make the next tick see their
				// real (still 0) health as a mismatch - "they just died again" -
				// when nothing actually happened to them.
				team.lastSyncedHealth.put(uuid, currentHealth);
				continue;
			}
			team.lastSyncedHealth.put(uuid, team.sharedHealth);
			if (team.sharedHealth <= 0f) {
				boolean isRealCause = player == sourceOfChange;
				player.setHealth(0f);
				player.die(player.damageSources().generic());
				if (!isRealCause && sourceOfChange != null) {
					broadcastToTeam(online, FunnyMessages.randomPropagatedDeath(
						player.getGameProfile().name(), sourceOfChange.getGameProfile().name(), PantheonConfig.get().crudeHumor));
				}
			} else if (currentHealth != team.sharedHealth) {
				player.setHealth(Math.min(team.sharedHealth, player.getMaxHealth()));
			}
		}
	}

	private static void broadcastToTeam(final List<ServerPlayer> online, final Component message) {
		for (ServerPlayer player : online) {
			player.sendSystemMessage(message);
		}
	}

	/**
	 * Drops the team's shared inventory contents once (not per dying player,
	 * to avoid duplicating every item) and clears it - including any currently
	 * shared armor/offhand. Equipment can't rely on vanilla's own
	 * drop-on-death here even setting keepInventory aside: {@code com.pantheon.mixin.EquipmentSharingMixin}
	 * only redirects {@code PlayerEquipment.get/set}, so vanilla's
	 * {@code EntityEquipment.dropAll} - which reads its own private backing
	 * map directly, not through the overridden accessors - would drop whatever
	 * stale, orphaned local items that map holds instead of the actual shared
	 * ones sitting in {@link Team#equipment}.
	 */
	private static void dropSharedInventoryOnce(final Team team, final ServerPlayer anchor) {
		ServerLevel level = (ServerLevel) anchor.level();
		for (int i = 0; i < team.items.size(); i++) {
			ItemStack stack = team.items.get(i);
			if (!stack.isEmpty()) {
				DeathDropSaves.tagDeathDrop(anchor.spawnAtLocation(level, stack));
				team.items.set(i, ItemStack.EMPTY);
			}
		}
		PantheonConfig config = PantheonConfig.get();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!config.isEquipmentSlotShared(slot)) {
				continue;
			}
			ItemStack stack = team.equipment.get(slot);
			if (stack != null && !stack.isEmpty()) {
				DeathDropSaves.tagDeathDrop(anchor.spawnAtLocation(level, stack));
				team.equipment.put(slot, ItemStack.EMPTY);
			}
		}
	}

	/**
	 * Two players eating (or a player eating while another simultaneously
	 * drains from exhaustion) in the very same tick both count as "changed"
	 * here - picking whichever happens to be last in iteration order, as a
	 * single flat assignment would, arbitrarily throws the other one away.
	 * Preferring the higher food level (tying on saturation) instead means a
	 * genuine eat is never silently discarded just because someone else's
	 * value moved the same tick; a natural drain that loses this tie isn't
	 * lost for good, since it reasserts itself again as soon as it next
	 * differs from whatever the pool settles on.
	 */
	private static void tickHunger(final Team team, final List<ServerPlayer> online) {
		if (online.isEmpty()) {
			return;
		}
		team.lastSyncedFood.keySet().retainAll(uuids(online));
		team.lastSyncedSaturation.keySet().retainAll(uuids(online));

		Integer bestFood = null;
		Float bestSaturation = null;
		for (ServerPlayer player : online) {
			Integer previousFood = team.lastSyncedFood.get(player.getUUID());
			Float previousSaturation = team.lastSyncedSaturation.get(player.getUUID());
			int food = player.getFoodData().getFoodLevel();
			float saturation = player.getFoodData().getSaturationLevel();
			boolean changed = (previousFood != null && previousFood.intValue() != food)
				|| (previousSaturation != null && previousSaturation.floatValue() != saturation);
			if (changed && (bestFood == null || food > bestFood || (food == bestFood && saturation > bestSaturation))) {
				bestFood = food;
				bestSaturation = saturation;
			}
		}
		if (bestFood != null) {
			team.sharedFood = bestFood;
			team.sharedSaturation = bestSaturation;
		}
		if (team.sharedFood == null) {
			team.sharedFood = online.get(0).getFoodData().getFoodLevel();
			team.sharedSaturation = online.get(0).getFoodData().getSaturationLevel();
		}

		for (ServerPlayer player : online) {
			team.lastSyncedFood.put(player.getUUID(), team.sharedFood);
			team.lastSyncedSaturation.put(player.getUUID(), team.sharedSaturation);
			if (player.getFoodData().getFoodLevel() != team.sharedFood) {
				player.getFoodData().setFoodLevel(team.sharedFood);
			}
			if (player.getFoodData().getSaturationLevel() != team.sharedSaturation) {
				player.getFoodData().setSaturation(team.sharedSaturation);
			}
		}
	}

	/**
	 * Mirrors {@link #tickHunger} but for experience level + progress-within-level,
	 * with one important difference: unlike hunger/health (which represent a
	 * current *state* that one changed value can simply replace), experience
	 * has a real "spend" action (an anvil, villager trades, ...), so two
	 * teammates' changes landing in the same tick have to be genuinely
	 * <em>composed</em> rather than one picked as "the" new value - picking
	 * either one as a winner (tried and reverted twice: preferring whichever
	 * was numerically higher let a simultaneous small gain fully cancel out
	 * an arbitrarily large spend for free; preferring decreases instead just
	 * meant a genuine simultaneous gain got permanently overwritten back down
	 * along with it, not merely "delayed" as that version's own comment
	 * incorrectly claimed - {@code setExperienceLevels} really does force
	 * every online player, including the one who owns the gain, down to
	 * whatever's picked) silently destroys whichever real event didn't win.
	 *
	 * <p>Instead, each already-tracked player's own delta since last tick
	 * (positive for a gain, negative for a spend) is summed and applied to
	 * the pool as one net adjustment: a spend still always reduces the pool
	 * by exactly what it cost, no matter what else happens to land in the
	 * same tick, so there's nothing to time for free levels; a simultaneous
	 * gain from someone else adds on top rather than getting thrown away.
	 * Level and progress-within-level are combined into one float
	 * ({@code level + progress}) purely so deltas from players sitting at
	 * different levels (and therefore different actual XP-per-level costs)
	 * can be summed at all without reimplementing vanilla's XP curve - this
	 * is an approximation, not a true XP-point delta, but it's only ever
	 * applied to the small, single-tick deltas that prompted it and is
	 * immediately re-observed and corrected from every player's own real
	 * vanilla state next tick, so it doesn't accumulate error over time.
	 */
	private static void tickExperience(final Team team, final List<ServerPlayer> online) {
		if (online.isEmpty()) {
			return;
		}
		team.lastSyncedExperienceLevel.keySet().retainAll(uuids(online));
		team.lastSyncedExperienceProgress.keySet().retainAll(uuids(online));

		if (team.sharedExperienceLevel == null) {
			team.sharedExperienceLevel = online.get(0).experienceLevel;
			team.sharedExperienceProgress = online.get(0).experienceProgress;
		} else {
			float netDelta = 0f;
			for (ServerPlayer player : online) {
				Integer previousLevel = team.lastSyncedExperienceLevel.get(player.getUUID());
				Float previousProgress = team.lastSyncedExperienceProgress.get(player.getUUID());
				if (previousLevel == null || previousProgress == null) {
					// Not tracked yet (a fresh join) - they adopt the pool via
					// the propagation loop below instead of contributing a
					// delta of their own this tick.
					continue;
				}
				float previousValue = previousLevel.floatValue() + previousProgress.floatValue();
				float currentValue = player.experienceLevel + player.experienceProgress;
				netDelta += currentValue - previousValue;
			}
			if (netDelta != 0f) {
				float newValue = Math.max(0f, team.sharedExperienceLevel + team.sharedExperienceProgress + netDelta);
				team.sharedExperienceLevel = (int) newValue;
				team.sharedExperienceProgress = newValue - team.sharedExperienceLevel;
			}
		}

		for (ServerPlayer player : online) {
			if (player.experienceLevel != team.sharedExperienceLevel) {
				player.setExperienceLevels(team.sharedExperienceLevel);
			}
			if (player.experienceProgress != team.sharedExperienceProgress) {
				player.setExperiencePoints(Math.round(team.sharedExperienceProgress * player.getXpNeededForNextLevel()));
			}
			// Recorded from the player's own post-set state, not the target
			// team.sharedExperience* values: setExperiencePoints rounds to a
			// whole XP point, so its actual resulting experienceProgress is
			// very rarely bit-identical to the exact float we asked for.
			// Recording the target anyway would leave a small nonzero delta
			// for every player, every tick, forever - even with nobody
			// gaining or spending XP - since the "current vs previous" check
			// above never contributes it to a real change.
			team.lastSyncedExperienceLevel.put(player.getUUID(), player.experienceLevel);
			team.lastSyncedExperienceProgress.put(player.getUUID(), player.experienceProgress);
		}
	}

	/**
	 * Propagates active potion/status effects (from drinking, splash/lingering
	 * potions, beacons, ...) to the rest of the online team, so the whole team
	 * benefits (or suffers) from whatever any one member picks up. Unlike
	 * health/hunger/XP there's no single scalar to converge on - each effect
	 * type is tracked independently, taking whichever online member currently
	 * has the strongest instance (highest amplifier, then longest remaining
	 * duration) as that effect's "source of truth" for the team this tick, and
	 * handing a copy of it to every other member who doesn't already have an
	 * equal-or-stronger one.
	 *
	 * <p>Deliberately asymmetric for removal: if a member cures a shared
	 * effect early (milk, honey, etc.) while teammates still have it running,
	 * they're recorded in {@link Team#effectCureExclusions} and skipped by
	 * future propagation of that same effect - otherwise the very next tick
	 * would just hand it right back to them, since teammates still show it as
	 * active. That exclusion only lasts until the effect has fully run its
	 * course for the whole team (nobody has it anymore), at which point the
	 * next fresh application of it starts propagating again for everyone.
	 *
	 * <p>Instantaneous effects (instant health/harm) aren't handled here -
	 * they apply and expire within the same tick they're added, almost always
	 * before this poll ever runs, so there's nothing left to observe by the
	 * time it does.
	 */
	private static void tickEffects(final Team team, final List<ServerPlayer> online) {
		if (online.isEmpty()) {
			return;
		}
		team.lastMemberEffectDurations.keySet().retainAll(uuids(online));

		Map<Holder<MobEffect>, MobEffectInstance> aggregate = new HashMap<>();
		for (ServerPlayer player : online) {
			for (MobEffectInstance instance : player.getActiveEffectsMap().values()) {
				MobEffectInstance current = aggregate.get(instance.getEffect());
				if (current == null || isStrongerEffect(instance, current)) {
					aggregate.put(instance.getEffect(), instance);
				}
			}
		}

		for (ServerPlayer player : online) {
			Map<Holder<MobEffect>, Integer> previous = team.lastMemberEffectDurations.get(player.getUUID());
			if (previous == null) {
				continue;
			}
			Map<Holder<MobEffect>, MobEffectInstance> currentEffects = player.getActiveEffectsMap();
			for (Map.Entry<Holder<MobEffect>, Integer> entry : previous.entrySet()) {
				Holder<MobEffect> effect = entry.getKey();
				if (currentEffects.containsKey(effect)) {
					continue;
				}
				// Had a meaningful amount of time left last tick, but it's gone
				// now even though the team aggregate still has it - that's a
				// deliberate cure (milk, etc.), not it just running out.
				if (entry.getValue() > CURE_DETECTION_THRESHOLD_TICKS && aggregate.containsKey(effect)) {
					team.effectCureExclusions.computeIfAbsent(effect, e -> new HashSet<>()).add(player.getUUID());
				}
			}
		}
		// An effect nobody on the team has anymore resets its exclusions -
		// the next time it's applied fresh, everyone's eligible again.
		team.effectCureExclusions.keySet().retainAll(aggregate.keySet());

		for (Map.Entry<Holder<MobEffect>, MobEffectInstance> entry : aggregate.entrySet()) {
			Holder<MobEffect> effect = entry.getKey();
			MobEffectInstance best = entry.getValue();
			Set<UUID> excluded = team.effectCureExclusions.get(effect);
			for (ServerPlayer player : online) {
				if (excluded != null && excluded.contains(player.getUUID())) {
					continue;
				}
				MobEffectInstance existing = player.getActiveEffectsMap().get(effect);
				if (existing != null && !isStrongerEffect(best, existing)) {
					continue;
				}
				if (!player.canBeAffected(best)) {
					continue;
				}
				player.addEffect(new MobEffectInstance(best));
			}
		}

		for (ServerPlayer player : online) {
			Map<Holder<MobEffect>, Integer> snapshot = new HashMap<>();
			for (Map.Entry<Holder<MobEffect>, MobEffectInstance> entry : player.getActiveEffectsMap().entrySet()) {
				snapshot.put(entry.getKey(), entry.getValue().getDuration());
			}
			team.lastMemberEffectDurations.put(player.getUUID(), snapshot);
		}
	}

	/**
	 * Amplifier wins first (a stronger potion always takes priority); a
	 * longer remaining duration only breaks a tie on amplifier - with an
	 * infinite duration (getDuration() == -1, e.g. from a beacon or
	 * /effect ... infinite) always outranking any finite one, since a raw
	 * numeric comparison would otherwise read that -1 sentinel as shorter
	 * than everything instead of longer than everything.
	 */
	private static boolean isStrongerEffect(final MobEffectInstance a, final MobEffectInstance b) {
		if (a.getAmplifier() != b.getAmplifier()) {
			return a.getAmplifier() > b.getAmplifier();
		}
		if (a.isInfiniteDuration() != b.isInfiniteDuration()) {
			return a.isInfiniteDuration();
		}
		return a.getDuration() > b.getDuration();
	}

	private static Set<UUID> uuids(final List<ServerPlayer> online) {
		Set<UUID> uuids = new HashSet<>();
		for (ServerPlayer player : online) {
			uuids.add(player.getUUID());
		}
		return uuids;
	}
}
