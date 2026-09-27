package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pantheon.SlotLocks;
import com.pantheon.network.HotbarOwnersPayload;

import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * {@code HotbarSlotLockMixin} covers everything that goes through a
 * {@code Slot}, but a whole family of vanilla paths reads and writes the raw
 * {@link Inventory} list by index instead, never asking a slot anything:
 *
 * <ul>
 *   <li>picking an item up off the ground ({@code add} ->
 *   {@code getSlotWithRemainingSpace} / {@code getFreeSlot}) - which is how
 *   a teammate's pickup used to top up, or land in, <em>your</em> owned
 *   slot;</li>
 *   <li>items handed back after closing a crafting grid or anvil
 *   ({@code placeItemBackInInventory}, same two scans);</li>
 *   <li>pick block ({@code findSlotMatchingItem},
 *   {@code getSuitableHotbarSlot}) - which could even select a slot someone
 *   else owns just because it already held a matching item;</li>
 *   <li>the recipe book ({@code fillStackedContents},
 *   {@code findSlotMatchingCraftingIngredient}), which pulled ingredients
 *   straight out of other players' owned slots.</li>
 * </ul>
 *
 * <p>Each is re-implemented to skip every hotbar slot locked to this
 * inventory's own player ({@link SlotLocks}), and is left entirely to
 * vanilla when nothing is locked. Two-hand mode is just the special case
 * where every slot but the center one is locked, so a blanked slot is never
 * a destination either.
 */
@Mixin(Inventory.class)
public abstract class HotbarInventoryLockMixin {
	@Shadow
	@Final
	public Player player;

	@Shadow
	public abstract int getSelectedSlot();

	@Shadow
	public abstract ItemStack getItem(int slot);

	@Shadow
	private boolean hasRemainingSpaceForItem(final ItemStack slotItemStack, final ItemStack newItemStack) {
		throw new AssertionError();
	}

	@Unique
	private NonNullList<ItemStack> pantheon$items() {
		return ((Inventory) (Object) this).getNonEquipmentItems();
	}

	@Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
	private void pantheon$freeSlot(final CallbackInfoReturnable<Integer> cir) {
		boolean[] locked = SlotLocks.lockedMask(this.player);
		if (locked == null) {
			return;
		}
		NonNullList<ItemStack> items = this.pantheon$items();
		for (int i = 0; i < items.size(); i++) {
			if (!SlotLocks.isLocked(locked, i) && items.get(i).isEmpty()) {
				cir.setReturnValue(i);
				return;
			}
		}
		cir.setReturnValue(-1);
	}

	@Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
	private void pantheon$slotWithSpace(final ItemStack newItemStack, final CallbackInfoReturnable<Integer> cir) {
		boolean[] locked = SlotLocks.lockedMask(this.player);
		if (locked == null) {
			return;
		}
		int selected = this.getSelectedSlot();
		if (!SlotLocks.isLocked(locked, selected) && this.hasRemainingSpaceForItem(this.getItem(selected), newItemStack)) {
			cir.setReturnValue(selected);
			return;
		}
		if (this.hasRemainingSpaceForItem(this.getItem(Inventory.SLOT_OFFHAND), newItemStack)) {
			cir.setReturnValue(Inventory.SLOT_OFFHAND);
			return;
		}
		NonNullList<ItemStack> items = this.pantheon$items();
		for (int i = 0; i < items.size(); i++) {
			if (!SlotLocks.isLocked(locked, i) && this.hasRemainingSpaceForItem(items.get(i), newItemStack)) {
				cir.setReturnValue(i);
				return;
			}
		}
		cir.setReturnValue(-1);
	}

	@Inject(method = "findSlotMatchingItem", at = @At("HEAD"), cancellable = true)
	private void pantheon$matchingItem(final ItemStack itemStack, final CallbackInfoReturnable<Integer> cir) {
		boolean[] locked = SlotLocks.lockedMask(this.player);
		if (locked == null) {
			return;
		}
		NonNullList<ItemStack> items = this.pantheon$items();
		for (int i = 0; i < items.size(); i++) {
			ItemStack candidate = items.get(i);
			if (!SlotLocks.isLocked(locked, i) && !candidate.isEmpty() && ItemStack.isSameItemSameComponents(itemStack, candidate)) {
				cir.setReturnValue(i);
				return;
			}
		}
		cir.setReturnValue(-1);
	}

	@Inject(method = "findSlotMatchingCraftingIngredient", at = @At("HEAD"), cancellable = true)
	private void pantheon$matchingIngredient(final Holder<Item> item, final ItemStack existingItem, final CallbackInfoReturnable<Integer> cir) {
		boolean[] locked = SlotLocks.lockedMask(this.player);
		if (locked == null) {
			return;
		}
		NonNullList<ItemStack> items = this.pantheon$items();
		for (int i = 0; i < items.size(); i++) {
			ItemStack candidate = items.get(i);
			if (!SlotLocks.isLocked(locked, i)
				&& !candidate.isEmpty()
				&& candidate.is(item)
				&& Inventory.isUsableForCrafting(candidate)
				&& (existingItem.isEmpty() || ItemStack.isSameItemSameComponents(existingItem, candidate))) {
				cir.setReturnValue(i);
				return;
			}
		}
		cir.setReturnValue(-1);
	}

	@Inject(method = "getSuitableHotbarSlot", at = @At("HEAD"), cancellable = true)
	private void pantheon$suitableHotbarSlot(final CallbackInfoReturnable<Integer> cir) {
		boolean[] locked = SlotLocks.lockedMask(this.player);
		if (locked == null) {
			return;
		}
		NonNullList<ItemStack> items = this.pantheon$items();
		int selected = this.getSelectedSlot();
		for (int offset = 0; offset < HotbarOwnersPayload.SLOT_COUNT; offset++) {
			int index = (selected + offset) % HotbarOwnersPayload.SLOT_COUNT;
			if (!SlotLocks.isLocked(locked, index) && items.get(index).isEmpty()) {
				cir.setReturnValue(index);
				return;
			}
		}
		for (int offset = 0; offset < HotbarOwnersPayload.SLOT_COUNT; offset++) {
			int index = (selected + offset) % HotbarOwnersPayload.SLOT_COUNT;
			if (!SlotLocks.isLocked(locked, index) && !items.get(index).isEnchanted()) {
				cir.setReturnValue(index);
				return;
			}
		}
		cir.setReturnValue(selected);
	}

	@Inject(method = "fillStackedContents", at = @At("HEAD"), cancellable = true)
	private void pantheon$stackedContents(final StackedItemContents contents, final CallbackInfo ci) {
		boolean[] locked = SlotLocks.lockedMask(this.player);
		if (locked == null) {
			return;
		}
		NonNullList<ItemStack> items = this.pantheon$items();
		for (int i = 0; i < items.size(); i++) {
			if (!SlotLocks.isLocked(locked, i)) {
				contents.accountSimpleStack(items.get(i));
			}
		}
		ci.cancel();
	}
}
