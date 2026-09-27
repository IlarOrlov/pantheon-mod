package com.pantheon.mixin;

import java.util.List;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.HotbarOwnership;
import com.pantheon.PantheonConfig;
import com.pantheon.network.ForceHotbarSlotPayload;
import com.pantheon.network.HotbarOwnersPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Authoritative backstop for {@code com.pantheon.client.mixin.HotbarScrollLockMixin}
 * / {@code HotbarNumberKeyLockMixin}: even if a client didn't block the
 * selection locally (a modified client, or a stale view of who owns what),
 * the server refuses to select a hotbar slot locked to a different online
 * player.
 *
 * <p>The client has already switched its own selection by the time this
 * packet arrives, so just ignoring it isn't enough: two players pressing the
 * same number key at the same moment both see the slot as free locally, the
 * server accepts whichever packet lands first and refuses the other - and
 * the loser's client would keep showing (and predicting use of) the slot it
 * never actually got, while everyone else sees it on its old one. Every
 * refusal therefore answers with {@link ForceHotbarSlotPayload}, snapping
 * the client back to the slot the server really has it on.
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
		// Packet handlers first run on the network thread just to hop over to
		// the server thread - only judge (and answer) on the real pass, where
		// the other players' selections can't change underneath us.
		if (!((ServerLevel) this.player.level()).getServer().isSameThread()) {
			return;
		}

		if (PantheonConfig.get().twoHandSlotMode && slot != HotbarOwnersPayload.TWO_HAND_ACTIVE_SLOT) {
			this.pantheon$refuse(ci);
			return;
		}

		List<UUID> owners = HotbarOwnership.currentOwnersFor(this.player);
		UUID owner = owners.get(slot);
		if (!owner.equals(HotbarOwnersPayload.NO_OWNER) && !owner.equals(this.player.getUUID())) {
			this.pantheon$refuse(ci);
		}
	}

	@Unique
	private void pantheon$refuse(final CallbackInfo ci) {
		ci.cancel();
		ServerPlayNetworking.send(this.player, new ForceHotbarSlotPayload(this.player.getInventory().getSelectedSlot()));
	}
}
