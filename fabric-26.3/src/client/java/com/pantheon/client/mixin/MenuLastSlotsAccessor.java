package com.pantheon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Exposes the menu's listener snapshot so server-sent contents can be marked as already seen. */
@Mixin(AbstractContainerMenu.class)
public interface MenuLastSlotsAccessor {
	@Accessor("lastSlots")
	NonNullList<ItemStack> pantheon$getLastSlots();
}
