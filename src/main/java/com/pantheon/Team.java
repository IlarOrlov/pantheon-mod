package com.pantheon;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.world.effect.MobEffect;
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

	// SharedStats effect tracking - see that class for what these mean.
	final Map<UUID, Map<Holder<MobEffect>, Integer>> lastMemberEffectDurations = new HashMap<>();
	final Map<Holder<MobEffect>, Set<UUID>> effectCureExclusions = new HashMap<>();

	/** The shared hunger pool's current food level, or {@code null} if shared hunger has never synced (or is off). Package-visible fields stay {@link SharedStats}'s to write; this is the read-only view other packages (mixins) need. */
	public Integer sharedFoodLevel() {
		return this.sharedFood;
	}

	public Float sharedSaturationLevel() {
		return this.sharedSaturation;
	}

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
