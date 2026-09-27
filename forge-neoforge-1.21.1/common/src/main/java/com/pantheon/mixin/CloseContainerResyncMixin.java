package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
	/** Set at the start of a close, read at its end - both always on the server thread, never nested. */
	@Unique
	private boolean pantheon$closingContainer;

	@Inject(method = "doCloseContainer", at = @At("HEAD"))
	private void pantheon$noteContainer(final CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		this.pantheon$closingContainer = self.containerMenu != self.inventoryMenu;
	}

	@Inject(method = "doCloseContainer", at = @At("TAIL"))
	private void pantheon$resync(final CallbackInfo ci) {
		if (this.pantheon$closingContainer) {
			this.pantheon$closingContainer = false;
			((ServerPlayer) (Object) this).inventoryMenu.broadcastFullState();
		}
	}
}
