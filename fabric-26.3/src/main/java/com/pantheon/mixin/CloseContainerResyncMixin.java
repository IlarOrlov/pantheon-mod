package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;

import net.minecraft.server.level.ServerPlayer;

/**
 * Closing a chest hands its sync state for the player's own slots over to the
 * inventory menu, as if everything sent under the chest's window had arrived.
 * But the client drops slot updates for a window it has already closed, and
 * with a shared inventory teammates keep changing those slots right up to the
 * close - so the inventory could silently stay out of date. Resends it whole
 * after any real container closes.
 */
@Mixin(ServerPlayer.class)
public abstract class CloseContainerResyncMixin {
	@Inject(method = "doCloseContainer", at = @At("HEAD"))
	private void pantheon$noteContainer(final CallbackInfo ci, @Share("wasContainer") final LocalBooleanRef wasContainer) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		wasContainer.set(self.containerMenu != self.inventoryMenu);
	}

	@Inject(method = "doCloseContainer", at = @At("TAIL"))
	private void pantheon$resync(final CallbackInfo ci, @Share("wasContainer") final LocalBooleanRef wasContainer) {
		if (wasContainer.get()) {
			((ServerPlayer) (Object) this).inventoryMenu.broadcastFullState();
		}
	}
}
