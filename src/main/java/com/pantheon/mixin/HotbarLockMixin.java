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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pantheon.HotbarOwnership;
import com.pantheon.PantheonConfig;
import com.pantheon.SlotLocks;
import com.pantheon.Team;
import com.pantheon.TeamManager;
import com.pantheon.network.HotbarOwnersPayload;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps a hotbar slot untouchable by whoever it's locked against, in three
 * layers:
 *
 * <ol>
 *   <li>If the click is directly on a locked slot - hovering it and pressing
 *   Q (drop), F (swap hands), a number key, or clicking it outright - the
 *   whole click is cancelled before vanilla does anything. This has to
 *   happen <em>before</em>, not after: dropping spawns an item entity in the
 *   world and swap-hands writes into the offhand slot, neither of which a
 *   same-tick revert could undo without leaving a duplicated item behind.
 *   Runs on both sides ({@link SlotLocks}) so the client's own prediction
 *   agrees with the server's verdict.</li>
 *   <li>Shift-click ({@code moveItemStackTo}) sees a locked slot as empty,
 *   and {@code HotbarSlotLockMixin} makes it refuse placement - so a
 *   shift-clicked stack (from a chest, a crafting result, a furnace) skips
 *   straight past someone else's slot, even one holding a matching stack it
 *   could otherwise have merged into, to the next slot that's actually
 *   yours to fill.</li>
 *   <li>As a last-resort backstop, <em>every</em> slot in the open menu -
 *   chest, crafting grid and result included, not just the shared
 *   inventory - plus the cursor are snapshotted before the click and all
 *   restored if a locked slot ends up changed anyway. Restoring only the
 *   player's own inventory (as this used to) after vanilla had already
 *   taken the item out of the chest or crafting result is exactly how items
 *   used to vanish.</li>
 * </ol>
 *
 * <p>What counts as "locked" depends on which of two mutually-exclusive
 * modes is on (see {@link PantheonConfig#normalize}): with
 * {@link PantheonConfig#enableHotbarOwnership}, a slot is locked to everyone
 * except whichever online teammate currently owns it ({@link HotbarOwnership});
 * with {@link PantheonConfig#twoHandSlotMode}, every slot but the one active
 * slot ({@link HotbarOwnersPayload#TWO_HAND_ACTIVE_SLOT}) is locked to
 * <em>everyone</em>. The actual rule lives in {@link HotbarOwnership#isLocked}.
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
	private ItemStack[] pantheon$beforeSlots;

	@Unique
	private ItemStack pantheon$beforeCarried;

	@Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
	private void pantheon$capture(final int slotId, final int button, final ContainerInput input, final Player player, final CallbackInfo ci) {
		this.pantheon$before = null;
		this.pantheon$beforeSlots = null;

		boolean[] locked = SlotLocks.lockedMask(player);
		if (locked == null) {
			return;
		}

		int hoveredHotbarSlot = this.pantheon$hotbarIndexOf(slotId, player);
		boolean touchesLockedSlotDirectly = SlotLocks.isLocked(locked, hoveredHotbarSlot)
			|| (input == ContainerInput.SWAP && SlotLocks.isLocked(locked, button));

		if (touchesLockedSlotDirectly) {
			ci.cancel();
			return;
		}

		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		Team team = TeamManager.teamOf(serverPlayer);
		ItemStack[] snapshot = new ItemStack[team.items.size()];
		for (int i = 0; i < snapshot.length; i++) {
			snapshot[i] = team.items.get(i).copy();
		}
		ItemStack[] slotSnapshot = new ItemStack[this.slots.size()];
		for (int i = 0; i < slotSnapshot.length; i++) {
			slotSnapshot[i] = this.slots.get(i).getItem().copy();
		}
		this.pantheon$before = snapshot;
		this.pantheon$beforeSlots = slotSnapshot;
		this.pantheon$beforeCarried = this.getCarried().copy();
	}

	@Inject(method = "clicked", at = @At("RETURN"))
	private void pantheon$revert(final int slotId, final int button, final ContainerInput input, final Player player, final CallbackInfo ci) {
		ItemStack[] before = this.pantheon$before;
		ItemStack[] beforeSlots = this.pantheon$beforeSlots;
		this.pantheon$before = null;
		this.pantheon$beforeSlots = null;
		if (before == null || beforeSlots == null || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		PantheonConfig config = PantheonConfig.get();
		List<UUID> owners = config.enableHotbarOwnership ? HotbarOwnership.currentOwnersFor(serverPlayer) : null;
		Team team = TeamManager.teamOf(serverPlayer);

		boolean violated = false;
		for (int i = 0; i < before.length; i++) {
			if (HotbarOwnership.isLocked(i, owners, serverPlayer, config) && !ItemStack.matches(before[i], team.items.get(i))) {
				violated = true;
				break;
			}
		}

		if (!violated) {
			return;
		}

		for (int i = 0; i < before.length; i++) {
			team.items.set(i, before[i]);
		}
		// Everything outside the shared inventory the click could have taken
		// from - chest, crafting grid/result, furnace, ... Inventory-backed
		// slots were already restored above through team.items.
		for (int i = 0; i < beforeSlots.length && i < this.slots.size(); i++) {
			Slot slot = this.slots.get(i);
			if (slot.container == serverPlayer.getInventory()) {
				continue;
			}
			if (!ItemStack.matches(beforeSlots[i], slot.getItem())) {
				slot.set(beforeSlots[i]);
			}
		}
		this.setCarried(this.pantheon$beforeCarried);
		this.broadcastFullState();
	}

	/**
	 * Shift-click's merge pass only ever checks "same item, room left" - never
	 * {@code Slot#mayPlace} - so without this a stack shift-clicked out of a
	 * chest would top up the matching stack in someone else's owned slot.
	 * Reporting a locked slot as empty skips the merge, and the fill pass's
	 * own {@code mayPlace} check (see {@code HotbarSlotLockMixin}) then
	 * refuses it as a destination too.
	 */
	@WrapOperation(method = "moveItemStackTo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack pantheon$hideLockedSlot(final Slot slot, final Operation<ItemStack> original) {
		if (SlotLocks.isLocked(slot)) {
			return ItemStack.EMPTY;
		}
		return original.call(slot);
	}

	/** Maps a menu-local slot id to the shared hotbar index (0-8) it refers to, or -1 if it isn't one. */
	@Unique
	private int pantheon$hotbarIndexOf(final int slotId, final Player player) {
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
}
