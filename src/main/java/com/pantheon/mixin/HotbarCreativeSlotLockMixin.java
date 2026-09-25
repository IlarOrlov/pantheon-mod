package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.SlotLocks;
import com.pantheon.network.HotbarOwnersPayload;

import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * The Creative inventory doesn't send clicks - it tells the server outright
 * "slot N now holds this", written straight into the inventory menu with no
 * {@code mayPlace} anywhere on the way. Refuses that for a hotbar slot
 * locked to this player, and pushes the real contents back so their screen
 * stops showing an item that was never placed.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class HotbarCreativeSlotLockMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
	private void pantheon$blockLockedSlot(final ServerboundSetCreativeModeSlotPacket packet, final CallbackInfo ci) {
		int hotbarIndex = packet.slotNum() - InventoryMenu.USE_ROW_SLOT_START;
		if (hotbarIndex < 0 || hotbarIndex >= HotbarOwnersPayload.SLOT_COUNT) {
			return;
		}
		// Packet handlers first run on the network thread just to hop over to
		// the server thread - only act on the real (server-thread) pass.
		if (!((ServerLevel) this.player.level()).getServer().isSameThread()) {
			return;
		}
		if (SlotLocks.isLocked(this.player, hotbarIndex)) {
			ci.cancel();
			this.player.inventoryMenu.broadcastFullState();
		}
	}
}
