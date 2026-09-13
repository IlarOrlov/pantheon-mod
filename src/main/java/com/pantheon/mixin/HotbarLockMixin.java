package com.pantheon.mixin;

import java.util.List;
import java.util.UUID;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.HotbarOwnership;
import com.pantheon.SharedInventory;
import com.pantheon.PantheonConfig;
import com.pantheon.network.HotbarOwnersPayload;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps a hotbar slot locked to {@link HotbarOwnership} untouchable by
 * anyone else, in two layers:
 *
 * <ol>
 *   <li>If the click is directly on a locked slot - hovering it and pressing
 *   Q (drop), F (swap hands), a number key, or clicking it outright - the
 *   whole click is cancelled before vanilla does anything. This has to
 *   happen <em>before</em>, not after: dropping spawns an item entity in the
 *   world and swap-hands writes into the offhand slot, neither of which a
 *   same-tick revert could undo without leaving a duplicated item behind.</li>
 *   <li>As a backstop for indirect landings vanilla picks internally - e.g.
 *   shift-clicking a stack in from a chest, where the destination hotbar
 *   slot isn't a parameter we can check up front - the 9 hotbar slots and
 *   the cursor are snapshotted before the click and restored if a slot
 *   locked to someone else ends up changed anyway.</li>
 * </ol>
 */
@Mixin(AbstractContainerMenu.class)
public abstract class HotbarLockMixin {
	@Shadow
	@Final
	public NonNullList<Slot> slots;

	@Shadow
	public abstract ItemStack getCarried();

	@Shadow
	public abstract void setCarried(ItemStack stack);

	@Shadow
	public abstract void broadcastFullState();

	@Unique
	private ItemStack[] pantheon$before;

	@Unique
	private ItemStack pantheon$beforeCarried;

	@Unique
	private UUID pantheon$clicker;

	@Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
	private void pantheon$capture(final int slotId, final int button, final ContainerInput input, final Player player, final CallbackInfo ci) {
		this.pantheon$before = null;

		if (!(player instanceof ServerPlayer serverPlayer) || !PantheonConfig.get().enableHotbarOwnership) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		List<UUID> owners = HotbarOwnership.currentOwners(level.getServer());

		int hoveredHotbarSlot = this.pantheon$hotbarIndexOf(slotId, serverPlayer);
		boolean touchesLockedSlotDirectly = this.pantheon$isLockedToSomeoneElse(hoveredHotbarSlot, owners, serverPlayer)
			|| (input == ContainerInput.SWAP && this.pantheon$isLockedToSomeoneElse(button, owners, serverPlayer));

		if (touchesLockedSlotDirectly) {
			ci.cancel();
			return;
		}

		this.pantheon$clicker = serverPlayer.getUUID();

		ItemStack[] snapshot = new ItemStack[HotbarOwnersPayload.SLOT_COUNT];
		for (int i = 0; i < snapshot.length; i++) {
			snapshot[i] = SharedInventory.ITEMS.get(i).copy();
		}
		this.pantheon$before = snapshot;
		this.pantheon$beforeCarried = this.getCarried().copy();
	}

	@Inject(method = "clicked", at = @At("RETURN"))
	private void pantheon$revert(final int slotId, final int button, final ContainerInput input, final Player player, final CallbackInfo ci) {
		ItemStack[] before = this.pantheon$before;
		this.pantheon$before = null;
		if (before == null || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		List<UUID> owners = HotbarOwnership.currentOwners(level.getServer());

		boolean violated = false;
		for (int i = 0; i < before.length; i++) {
			if (this.pantheon$isLockedToSomeoneElse(i, owners, serverPlayer) && !ItemStack.matches(before[i], SharedInventory.ITEMS.get(i))) {
				violated = true;
				break;
			}
		}

		if (!violated) {
			return;
		}

		for (int i = 0; i < before.length; i++) {
			SharedInventory.ITEMS.set(i, before[i]);
		}
		this.setCarried(this.pantheon$beforeCarried);
		this.broadcastFullState();
	}

	/** Maps a menu-local slot id to the shared hotbar index (0-8) it refers to, or -1 if it isn't one. */
	@Unique
	private int pantheon$hotbarIndexOf(final int slotId, final ServerPlayer player) {
		if (slotId < 0 || slotId >= this.slots.size()) {
			return -1;
		}
		Slot slot = this.slots.get(slotId);
		if (slot.container != player.getInventory()) {
			return -1;
		}
		int index = slot.getContainerSlot();
		return (index >= 0 && index < HotbarOwnersPayload.SLOT_COUNT) ? index : -1;
	}

	@Unique
	private boolean pantheon$isLockedToSomeoneElse(final int hotbarIndex, final List<UUID> owners, final ServerPlayer player) {
		if (hotbarIndex < 0 || hotbarIndex >= HotbarOwnersPayload.SLOT_COUNT) {
			return false;
		}
		UUID owner = owners.get(hotbarIndex);
		return !owner.equals(HotbarOwnersPayload.NO_OWNER) && !owner.equals(player.getUUID());
	}
}
