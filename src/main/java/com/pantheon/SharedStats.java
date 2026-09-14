package com.pantheon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Shares current health and/or hunger across every online player, each
 * independently toggleable via {@link PantheonConfig}. Unlike the shared
 * inventory (a single backing list every player's {@code Inventory} points
 * at), health and hunger aren't simple collections we can alias - they're
 * per-entity synced values - so instead this polls once a server tick:
 * whichever online player's value no longer matches what we last synced them
 * to is treated as the source of a fresh change (damage, healing, eating,
 * a fresh join adopting the existing pool, ...), and that new value is then
 * applied to everyone else.
 *
 * <p>When {@link PantheonConfig#syncHealth} is on, a shared health pool
 * reaching 0 kills every online player at once, through the real death
 * pipeline ({@code setHealth(0)} + {@link net.minecraft.world.entity.LivingEntity#die})
 * so respawning actually works - {@code Entity#kill} looked tempting but only
 * force-removes the entity, without ever presenting a respawn screen. The
 * shared inventory is dropped and cleared exactly once for the whole event
 * (not once per dying player, which would duplicate every item), since
 * {@code keepInventory} being forced on would otherwise just let it survive
 * a death that's supposed to actually end the run.
 */
public final class SharedStats {
	private SharedStats() {
	}

	private static Float sharedHealth;
	private static final Map<UUID, Float> lastSyncedHealth = new HashMap<>();
	private static boolean sharedDeathHandled;

	private static Integer sharedFood;
	private static Float sharedSaturation;
	private static final Map<UUID, Integer> lastSyncedFood = new HashMap<>();
	private static final Map<UUID, Float> lastSyncedSaturation = new HashMap<>();

	public static void tick(final MinecraftServer server) {
		PantheonConfig config = PantheonConfig.get();
		List<ServerPlayer> online = server.getPlayerList().getPlayers();

		if (config.syncHealth) {
			tickHealth(online);
		} else if (sharedHealth != null) {
			sharedHealth = null;
			sharedDeathHandled = false;
			lastSyncedHealth.clear();
		}

		if (config.syncHunger) {
			tickHunger(online);
		} else if (sharedFood != null) {
			sharedFood = null;
			sharedSaturation = null;
			lastSyncedFood.clear();
			lastSyncedSaturation.clear();
		}
	}

	private static void tickHealth(final List<ServerPlayer> online) {
		if (online.isEmpty()) {
			return;
		}
		lastSyncedHealth.keySet().retainAll(uuids(online));

		if (sharedHealth != null && sharedHealth <= 0f) {
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
					sharedHealth = player.getHealth();
					sharedDeathHandled = false;
					break;
				}
			}
		} else {
			ServerPlayer sourceOfChange = null;
			for (ServerPlayer player : online) {
				Float previous = lastSyncedHealth.get(player.getUUID());
				if (previous != null && previous.floatValue() != player.getHealth()) {
					sharedHealth = player.getHealth();
					sourceOfChange = player;
				}
			}
			if (sharedHealth == null) {
				sharedHealth = online.get(0).getHealth();
			}

			if (sharedHealth <= 0f && !sharedDeathHandled) {
				dropSharedInventoryOnce(sourceOfChange != null ? sourceOfChange : online.get(0));
				sharedDeathHandled = true;
			}
		}

		for (ServerPlayer player : online) {
			UUID uuid = player.getUUID();
			float currentHealth = player.getHealth();

			if (!lastSyncedHealth.containsKey(uuid) && currentHealth <= 0f) {
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
				lastSyncedHealth.put(uuid, currentHealth);
				continue;
			}
			lastSyncedHealth.put(uuid, sharedHealth);
			if (sharedHealth <= 0f) {
				player.setHealth(0f);
				player.die(player.damageSources().generic());
			} else if (currentHealth != sharedHealth) {
				player.setHealth(Math.min(sharedHealth, player.getMaxHealth()));
			}
		}
	}

	/** Drops the shared inventory's contents once (not per dying player, to avoid duplicating every item) and clears it. */
	private static void dropSharedInventoryOnce(final ServerPlayer anchor) {
		ServerLevel level = (ServerLevel) anchor.level();
		for (int i = 0; i < SharedInventory.ITEMS.size(); i++) {
			ItemStack stack = SharedInventory.ITEMS.get(i);
			if (!stack.isEmpty()) {
				anchor.spawnAtLocation(level, stack);
				SharedInventory.ITEMS.set(i, ItemStack.EMPTY);
			}
		}
	}

	private static void tickHunger(final List<ServerPlayer> online) {
		if (online.isEmpty()) {
			return;
		}
		lastSyncedFood.keySet().retainAll(uuids(online));
		lastSyncedSaturation.keySet().retainAll(uuids(online));

		for (ServerPlayer player : online) {
			Integer previousFood = lastSyncedFood.get(player.getUUID());
			Float previousSaturation = lastSyncedSaturation.get(player.getUUID());
			int food = player.getFoodData().getFoodLevel();
			float saturation = player.getFoodData().getSaturationLevel();
			if ((previousFood != null && previousFood.intValue() != food)
				|| (previousSaturation != null && previousSaturation.floatValue() != saturation)) {
				sharedFood = food;
				sharedSaturation = saturation;
			}
		}
		if (sharedFood == null) {
			sharedFood = online.get(0).getFoodData().getFoodLevel();
			sharedSaturation = online.get(0).getFoodData().getSaturationLevel();
		}

		for (ServerPlayer player : online) {
			lastSyncedFood.put(player.getUUID(), sharedFood);
			lastSyncedSaturation.put(player.getUUID(), sharedSaturation);
			if (player.getFoodData().getFoodLevel() != sharedFood) {
				player.getFoodData().setFoodLevel(sharedFood);
			}
			if (player.getFoodData().getSaturationLevel() != sharedSaturation) {
				player.getFoodData().setSaturation(sharedSaturation);
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
