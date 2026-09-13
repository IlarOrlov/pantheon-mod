package com.pantheon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.pantheon.client.PantheonModClient;

import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Inventory;

/**
 * Scrolling the mouse wheel while holding a hotbar slot that's locked to
 * another online player should just do nothing for that step, rather than
 * letting the local player "hover"/hold that slot as their current item.
 * {@link com.pantheon.mixin.HotbarSelectionCapMixin} enforces the same rule
 * server-side; this is what makes it feel right locally instead of visibly
 * snapping back.
 */
@Mixin(MouseHandler.class)
public abstract class HotbarScrollLockMixin {
	@Redirect(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
	private void pantheon$blockLockedScroll(final Inventory inventory, final int slot) {
		if (!PantheonModClient.isLockedToSomeoneElse(slot)) {
			inventory.setSelectedSlot(slot);
		}
	}
}
