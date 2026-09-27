package com.pantheon;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/**
 * Lets code outside the mixin package (namely {@link TeamManager}) live-repoint
 * an already-constructed {@code Inventory} at a different team's backing
 * lists, for when a connected player switches teams mid-session or armor/offhand
 * sharing is toggled. Implemented by {@link com.pantheon.mixin.InventorySharingMixin}.
 */
public interface PantheonInventoryAccess {
	/**
	 * Points the inventory at {@code items}, and at {@code armor}/{@code offhand}
	 * too if given - {@code null} for either means "this player's own, unshared
	 * list" instead.
	 */
	void pantheon$share(NonNullList<ItemStack> items, NonNullList<ItemStack> armor, NonNullList<ItemStack> offhand);
}
