package com.pantheon.mixin;

import com.pantheon.TeamManager;

import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Points a new ServerPlayer's Inventory at its team's shared lists (see
 * {@link InventorySharingMixin}) as soon as the player exists with its real
 * UUID - before its save data is loaded, and before a respawned player's
 * contents are copied over from its old self.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerInventorySharingMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void pantheon$shareInventory(final CallbackInfo ci) {
		TeamManager.shareInventory((ServerPlayer) (Object) this);
	}
}
