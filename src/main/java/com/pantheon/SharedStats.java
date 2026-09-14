package com.pantheon;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * ...), and that new value is then applied to the rest of the team.
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
	private SharedStats() {
	}

	public static void tick(final MinecraftServer server) {
		PantheonConfig config = PantheonConfig.get();

		for (Map.Entry<Team, List<ServerPlayer>> entry : TeamManager.groupOnlineByTeam(server).entrySet()) {
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
				if (previous != null && previous.floatValue() != player.getHealth()) {
					team.sharedHealth = player.getHealth();
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
						player.getGameProfile().name(), sourceOfChange.getGameProfile().name()));
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

	/** Drops the team's shared inventory contents once (not per dying player, to avoid duplicating every item) and clears it. */
	private static void dropSharedInventoryOnce(final Team team, final ServerPlayer anchor) {
		ServerLevel level = (ServerLevel) anchor.level();
		for (int i = 0; i < team.items.size(); i++) {
			ItemStack stack = team.items.get(i);
			if (!stack.isEmpty()) {
				anchor.spawnAtLocation(level, stack);
				team.items.set(i, ItemStack.EMPTY);
			}
		}
	}

	private static void tickHunger(final Team team, final List<ServerPlayer> online) {
		if (online.isEmpty()) {
			return;
		}
		team.lastSyncedFood.keySet().retainAll(uuids(online));
		team.lastSyncedSaturation.keySet().retainAll(uuids(online));

		for (ServerPlayer player : online) {
			Integer previousFood = team.lastSyncedFood.get(player.getUUID());
			Float previousSaturation = team.lastSyncedSaturation.get(player.getUUID());
			int food = player.getFoodData().getFoodLevel();
			float saturation = player.getFoodData().getSaturationLevel();
			if ((previousFood != null && previousFood.intValue() != food)
				|| (previousSaturation != null && previousSaturation.floatValue() != saturation)) {
				team.sharedFood = food;
				team.sharedSaturation = saturation;
			}
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

	private static Set<UUID> uuids(final List<ServerPlayer> online) {
		Set<UUID> uuids = new HashSet<>();
		for (ServerPlayer player : online) {
			uuids.add(player.getUUID());
		}
		return uuids;
	}
}
