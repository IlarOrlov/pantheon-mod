package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.SharedInventory;
import com.pantheon.PantheonConfig;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Points the survival-inventory 2x2 crafting grid's backing list at
 * {@link SharedInventory#CRAFTING_ITEMS} when {@link PantheonConfig#syncCraftingGrid}
 * is on, so every player is filling in the same crafting grid. Only applies
 * to {@link InventoryMenu}'s grid, never the 3x3 crafting-table grid.
 *
 * <p>Unlike the get/set-based equipment sharing, this decision is made once
 * when the menu is created (on login): toggling the setting takes effect for
 * players who (re)join afterwards.
 */
@Mixin(TransientCraftingContainer.class)
public abstract class CraftingGridSharingMixin {
	@Shadow
	@Final
	@Mutable
	private NonNullList<ItemStack> items;

	@Inject(method = "<init>(Lnet/minecraft/world/inventory/AbstractContainerMenu;II)V", at = @At("TAIL"))
	private void pantheon$shareCraftingGrid(final AbstractContainerMenu menu, final int width, final int height, final CallbackInfo ci) {
		if (menu instanceof InventoryMenu && PantheonConfig.get().syncCraftingGrid) {
			this.items = SharedInventory.CRAFTING_ITEMS;
		}
	}
}
