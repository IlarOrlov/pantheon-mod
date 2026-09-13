package com.pantheon;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.item.ItemStack;

/**
 * The single inventory (36 main/hotbar slots + armor/offhand) that every
 * ServerPlayer on this server is wired into, via the mixins in
 * {@link com.pantheon.mixin}. There is exactly one of these per running
 * server; it is recreated (and therefore emptied) each time a server starts.
 */
public final class SharedInventory {
	public static final NonNullList<ItemStack> ITEMS = NonNullList.withSize(36, ItemStack.EMPTY);
	public static final EntityEquipment EQUIPMENT = new EntityEquipment();

	private SharedInventory() {
	}

	public static void reset() {
		for (int i = 0; i < ITEMS.size(); i++) {
			ITEMS.set(i, ItemStack.EMPTY);
		}
		EQUIPMENT.clear();
	}
}
