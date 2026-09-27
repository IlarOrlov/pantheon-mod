package com.pantheon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/** Exposes the protected {@code hoveredSlot} field so the "request slot" ping can tell which slot is under the cursor. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenHoveredSlotAccessor {
	@Accessor("hoveredSlot")
	Slot pantheon$getHoveredSlot();
}
