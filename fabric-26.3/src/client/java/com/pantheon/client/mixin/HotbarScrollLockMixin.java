package com.pantheon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.pantheon.client.PantheonModClient;

import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Inventory;

/**
 * Scrolling past a hotbar slot that's locked to another online player skips
 * straight over it to the next free slot in the same direction, rather than
 * landing on it (or just refusing to scroll at all).
 * {@link com.pantheon.mixin.HotbarSelectionCapMixin} enforces the same rule
 * server-side as a backstop.
 */
@Mixin(MouseHandler.class)
public abstract class HotbarScrollLockMixin {
	@Redirect(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
	private void pantheon$scrollToFreeSlot(final Inventory inventory, final int slot) {
		if (!PantheonModClient.isLockedToSomeoneElse(slot)) {
			inventory.setSelectedSlot(slot);
			return;
		}

		int size = Inventory.getSelectionSize();
		int current = inventory.getSelectedSlot();
		int forwardSteps = ((slot - current) % size + size) % size;
		int direction = forwardSteps <= size / 2 ? 1 : -1;

		int candidate = slot;
		for (int i = 0; i < size; i++) {
			if (!PantheonModClient.isLockedToSomeoneElse(candidate)) {
				inventory.setSelectedSlot(candidate);
				return;
			}
			candidate = ((candidate + direction) % size + size) % size;
		}
		// every slot is locked - leave the selection where it was
	}
}
