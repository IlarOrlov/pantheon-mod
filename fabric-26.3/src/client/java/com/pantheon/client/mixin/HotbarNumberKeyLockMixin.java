package com.pantheon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.pantheon.client.PantheonModClient;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;

/**
 * Pressing a hotbar number key (1-9) for a slot locked to another online
 * player should do nothing, same as scrolling onto it
 * ({@link HotbarScrollLockMixin}) - the local player simply can't select
 * that slot.
 */
@Mixin(Minecraft.class)
public abstract class HotbarNumberKeyLockMixin {
	@Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
	private void pantheon$blockLockedNumberKey(final Inventory inventory, final int slot) {
		if (!PantheonModClient.isLockedToSomeoneElse(slot)) {
			inventory.setSelectedSlot(slot);
		}
	}
}
