package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pantheon.PantheonConfig;
import com.pantheon.network.HotbarOwnersPayload;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Vanilla's own shift-click logic ({@code AbstractContainerMenu#moveItemStackTo},
 * called by every menu's {@code quickMoveStack}) fills empty slots strictly
 * by scanning for the first one where {@code Slot#mayPlace} says yes - with
 * no idea two-hand mode exists, every blanked hotbar slot says yes just
 * like any other empty slot, so shift-clicking a new item from the main
 * inventory would happily land it in slot 0 (the first empty hotbar slot
 * scanning forward) rather than the one active slot in the middle.
 *
 * <p>Says no instead for every hotbar slot but the active one while
 * two-hand mode is on, so {@code moveItemStackTo}'s own scan naturally
 * skips straight past them to the active slot (or, failing that, falls
 * through and leaves the stack right where it started - not a location any
 * of this needs to know about, since vanilla itself already treats "no
 * slot accepted it" as "nothing happened"). Scoped to the player's own
 * inventory ({@link Inventory}) specifically, so an ordinary chest, hopper,
 * or any other {@link Container}'s slots are never affected.
 */
@Mixin(Slot.class)
public abstract class TwoHandSlotMayPlaceMixin {
	@Shadow
	@Final
	public Container container;

	@Shadow
	public abstract int getContainerSlot();

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void pantheon$blockBlankedSlot(final ItemStack stack, final CallbackInfoReturnable<Boolean> cir) {
		if (!PantheonConfig.get().twoHandSlotMode || !(this.container instanceof Inventory)) {
			return;
		}
		int index = this.getContainerSlot();
		if (Inventory.isHotbarSlot(index) && index != HotbarOwnersPayload.TWO_HAND_ACTIVE_SLOT) {
			cir.setReturnValue(false);
		}
	}
}
