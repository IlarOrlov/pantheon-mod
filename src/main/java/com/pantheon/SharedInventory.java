package com.pantheon;

import java.util.EnumMap;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The backing storage that every ServerPlayer on this server is wired into,
 * via the mixins in {@link com.pantheon.mixin}. There is exactly one of these
 * per running server; it is recreated (and therefore emptied) each time a
 * server starts.
 */
public final class SharedInventory {
	/** 36 main/hotbar slots (index 0-8 is the hotbar), always shared. */
	public static final NonNullList<ItemStack> ITEMS = NonNullList.withSize(36, ItemStack.EMPTY);

	/** Armor + offhand, shared per-slot-type when {@link PantheonConfig} enables it. */
	public static final EnumMap<EquipmentSlot, ItemStack> SHARED_EQUIPMENT = new EnumMap<>(EquipmentSlot.class);

	static {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			SHARED_EQUIPMENT.put(slot, ItemStack.EMPTY);
		}
	}

	private SharedInventory() {
	}

	public static void reset() {
		for (int i = 0; i < ITEMS.size(); i++) {
			ITEMS.set(i, ItemStack.EMPTY);
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			SHARED_EQUIPMENT.put(slot, ItemStack.EMPTY);
		}
	}
}
