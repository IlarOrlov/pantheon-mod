package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.MenuPushTimes;
import com.pantheon.SlotLocks;
import com.pantheon.network.HotbarOwnersPayload;

import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Prediction;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Creative inventory doesn't send clicks - it tells the server outright
 * "slot N now holds this", with no state id to say which version of the slot
 * the client was looking at. Vanilla writes that in and then assumes the
 * client already shows it. With a shared inventory both halves go wrong:
 * <ul>
 * <li>a write that crossed a teammate's change in flight overwrites (deletes)
 *     the teammate's item, and</li>
 * <li>if the server had just pushed that slot, the client ends up showing the
 *     older push while the server holds the write - the two drift apart until
 *     the slot changes again.</li>
 * </ul>
 * So this replaces the valid-slot path: a write over a slot the client hasn't
 * seen the latest of (or over a hotbar slot locked to this player) is refused
 * and the item handed back instead of lost; an accepted write is confirmed
 * back to the client rather than assumed. Drops and invalid packets still go
 * to vanilla.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CreativeSlotSyncMixin {
	/** Slack on top of the round trip for a write to count as crossing a push. */
	@Unique
	private static final long STALE_MARGIN_MILLIS = 100L;
	@Unique
	private static final long MIN_STALE_WINDOW_MILLIS = 150L;

	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
	private void pantheon$handleSlot(final ServerboundSetCreativeModeSlotPacket packet, final CallbackInfo ci) {
		// Packet handlers first run on the network thread just to hop over to
		// the server thread - only act on the real (server-thread) pass.
		if (!((ServerLevel) this.player.level()).getServer().isSameThread()) {
			return;
		}
		int slotNum = packet.slotNum();
		ItemStack incoming = packet.itemStack();
		// Same gates as vanilla's slot path; anything else is vanilla's call.
		if (!this.player.hasInfiniteMaterials()
			|| slotNum < 1 || slotNum > 45
			|| !incoming.isItemEnabled(this.player.level().enabledFeatures())
			|| !incoming.isEmpty() && incoming.getCount() > incoming.getMaxStackSize()) {
			return;
		}
		ci.cancel();

		InventoryMenu menu = this.player.inventoryMenu;
		Slot slot = menu.getSlot(slotNum);
		ItemStack current = slot.getItem();
		if (ItemStack.matches(current, incoming)) {
			// Nothing changes; the client provably shows this now.
			menu.setRemoteSlot(slotNum, incoming);
			menu.broadcastChanges();
			return;
		}

		int hotbarIndex = slotNum - InventoryMenu.USE_ROW_SLOT_START;
		boolean locked = hotbarIndex >= 0 && hotbarIndex < HotbarOwnersPayload.SLOT_COUNT && SlotLocks.isLocked(this.player, hotbarIndex);
		if (locked || !current.isEmpty() && this.pantheon$isStale(menu, slotNum)) {
			// The client shows the refused item there; recording that makes
			// the broadcast below correct it to the real contents.
			menu.setRemoteSlot(slotNum, incoming);
			if (!incoming.isEmpty()) {
				// It may have been carried out of another slot - never lose it.
				this.player.getInventory().placeItemBackInInventory(incoming.copy(), false, Prediction.SERVER_ONLY);
			}
			menu.broadcastChanges();
			return;
		}

		slot.setByPlayer(incoming);
		// No setRemoteSlot: the broadcast confirms the write to the client,
		// which also repairs a push it crossed. That confirmation isn't a
		// teammate's change, so it mustn't make the next write look stale.
		MenuPushTimes pushTimes = (MenuPushTimes) menu;
		long lastPush = pushTimes.pantheon$lastPushMillis(slotNum);
		menu.broadcastChanges();
		pushTimes.pantheon$setLastPushMillis(slotNum, lastPush);
	}

	/** Whether this slot was pushed recently enough that the write may have been sent before the client saw it. */
	@Unique
	private boolean pantheon$isStale(final InventoryMenu menu, final int slotNum) {
		long lastPush = ((MenuPushTimes) menu).pantheon$lastPushMillis(slotNum);
		if (lastPush == 0L) {
			return false;
		}
		long window = Math.max(MIN_STALE_WINDOW_MILLIS, this.player.connection.latency() + STALE_MARGIN_MILLIS);
		return Util.getMillis() - lastPush <= window;
	}
}
