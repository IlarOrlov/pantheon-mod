package com.pantheon.mixin;

import java.util.List;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.HotbarOwnership;
import com.pantheon.PantheonConfig;
import com.pantheon.network.HotbarOwnersPayload;

import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Authoritative backstop for {@code com.pantheon.client.mixin.HotbarScrollLockMixin}
 * / {@code HotbarNumberKeyLockMixin}: even if a client didn't block the
 * selection locally (a modified client, or a stale view of who owns what),
 * the server refuses to select a hotbar slot locked to a different online
 * player. Silently ignoring the packet leaves the player's held slot
 * unchanged, exactly like the client-side block does.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class HotbarSelectionCapMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
	private void pantheon$blockLockedSelect(final ServerboundSetCarriedItemPacket packet, final CallbackInfo ci) {
		int slot = packet.getSlot();
		if (slot < 0 || slot >= HotbarOwnersPayload.SLOT_COUNT) {
			return;
		}

		if (PantheonConfig.get().twoHandSlotMode && slot != HotbarOwnersPayload.TWO_HAND_ACTIVE_SLOT) {
			ci.cancel();
			return;
		}

		List<UUID> owners = HotbarOwnership.currentOwnersFor(this.player);
		UUID owner = owners.get(slot);
		if (!owner.equals(HotbarOwnersPayload.NO_OWNER) && !owner.equals(this.player.getUUID())) {
			ci.cancel();
		}
	}
}
