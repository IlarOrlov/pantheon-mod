package com.pantheon;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * One independent shared-everything group: its own 36-slot inventory, its
 * own armor/offhand map, and its own hotbar-ownership/health/hunger tracking
 * state. Items and equipment persist with the world (see
 * {@link TeamContentsSavedData}); the tracking state below is session-only.
 * When {@link PantheonConfig#teamsEnabled} is off, every player
 * resolves to the single {@link TeamManager#GLOBAL_TEAM_NAME} team, which is
 * exactly the original one-pool-for-the-whole-server behavior.
 */
public final class Team {
	public final String name;

	public final NonNullList<ItemStack> items = NonNullList.withSize(36, ItemStack.EMPTY);
	public final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);

	// HotbarOwnership tracking - see that class for what these mean.
	List<UUID> lastBroadcastOwners;

	// SharedStats health tracking - see that class for what these mean.
	Float sharedHealth;
	final Map<UUID, Float> lastSyncedHealth = new HashMap<>();
	boolean sharedDeathHandled;

	// SharedStats hunger tracking.
	Integer sharedFood;
	Float sharedSaturation;
	final Map<UUID, Integer> lastSyncedFood = new HashMap<>();
	final Map<UUID, Float> lastSyncedSaturation = new HashMap<>();

	// SharedStats experience tracking.
	Integer sharedExperienceLevel;
	Float sharedExperienceProgress;
	final Map<UUID, Integer> lastSyncedExperienceLevel = new HashMap<>();
	final Map<UUID, Float> lastSyncedExperienceProgress = new HashMap<>();

	// "Request the slot" ping cooldowns, keyed by requesting player.
	public final Map<UUID, Long> lastPingTick = new HashMap<>();

	public Team(final String name) {
		this.name = name;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.equipment.put(slot, ItemStack.EMPTY);
		}
	}

	@Override
	public String toString() {
		return this.name;
	}
}
