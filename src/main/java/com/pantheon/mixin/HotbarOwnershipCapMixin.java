package com.pantheon.mixin;

import java.net.SocketAddress;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pantheon.PantheonConfig;

import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;

/**
 * Once hotbar ownership is on, every online player permanently occupies one
 * of the 9 hotbar slots, so the server can't usefully hold more than 9
 * players at a time - reject the 10th+ connection with a clear reason instead
 * of letting them join with no slot to call their own.
 */
@Mixin(PlayerList.class)
public abstract class HotbarOwnershipCapMixin {
	@Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
	private void pantheon$capPlayers(final SocketAddress address, final NameAndId nameAndId, final CallbackInfoReturnable<Component> cir) {
		PlayerList self = (PlayerList) (Object) this;
		if (PantheonConfig.get().enableHotbarOwnership && self.getPlayers().size() >= PantheonConfig.HOTBAR_OWNERSHIP_PLAYER_CAP) {
			cir.setReturnValue(Component.literal(
				"Server full: hotbar-ownership mode supports at most " + PantheonConfig.HOTBAR_OWNERSHIP_PLAYER_CAP + " players."
			));
		}
	}
}
