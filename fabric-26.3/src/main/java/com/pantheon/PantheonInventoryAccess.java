package com.pantheon;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/**
 * Lets code outside the mixin package (namely {@link TeamManager}) live-repoint
 * an already-constructed {@code Inventory} at a different team's backing item
 * list, for when a connected player switches teams mid-session. Implemented by
 * {@link com.pantheon.mixin.InventorySharingMixin}.
 */
public interface PantheonInventoryAccess {
	void pantheon$setItems(NonNullList<ItemStack> items);
}
