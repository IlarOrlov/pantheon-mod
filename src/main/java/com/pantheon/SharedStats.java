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
 * reaching 0 kills every online player at once (going through the normal
 * death pipeline via {@link net.minecraft.world.entity.Entity#kill}, so the
 * forced {@code keepInventory} rule still prevents any item drops).
 */
public final class SharedStats {
	private SharedStats() {
	}

	private static Float sharedHealth;
	private static final Map<UUID, Float> lastSyncedHealth = new HashMap<>();

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

		for (ServerPlayer player : online) {
			Float previous = lastSyncedHealth.get(player.getUUID());
			if (previous != null && previous.floatValue() != player.getHealth()) {
				sharedHealth = player.getHealth();
			}
		}
		if (sharedHealth == null) {
			sharedHealth = online.get(0).getHealth();
		}

		for (ServerPlayer player : online) {
			lastSyncedHealth.put(player.getUUID(), sharedHealth);
			if (!player.isAlive()) {
				continue;
			}
			if (sharedHealth <= 0f) {
				player.kill((ServerLevel) player.level());
			} else if (player.getHealth() != sharedHealth) {
				player.setHealth(Math.min(sharedHealth, player.getMaxHealth()));
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
