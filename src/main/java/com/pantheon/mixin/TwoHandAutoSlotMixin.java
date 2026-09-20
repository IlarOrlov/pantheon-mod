package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pantheon.PantheonConfig;
import com.pantheon.network.HotbarOwnersPayload;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * {@link com.pantheon.mixin.TwoHandSlotMayPlaceMixin} closes the shift-click
 * path (which goes through a {@code Slot}), but "pick block" (middle-click a
 * block/entity to grab a matching item, or conjure a fresh one in Creative)
 * and ordinary item pickup off the ground both write straight into the raw
 * {@link Inventory} data - {@code getFreeSlot} and
 * {@code getSuitableHotbarSlot} scan {@code items} directly by index,
 * entirely bypassing {@code Slot} and everything built on top of it.
 * {@code getFreeSlot} in particular scans from index 0, so with every
 * blanked hotbar slot sitting empty (indices 0-8 come before the 27-slot
 * main inventory in that same list) it would keep finding one of those
 * before ever reaching the main inventory at all.
 *
 * <p>Overrides both to treat every hotbar slot but the active one as
 * unavailable while two-hand mode is on: {@code getFreeSlot} skips them
 * entirely (falling through to the active slot if it's empty, otherwise the
 * main inventory - never dead space, never a locked slot),
 * {@code getSuitableHotbarSlot} always answers with the active slot itself,
 * since it's the only one two-hand mode ever considers "the hotbar" to
 * begin with.
 */
@Mixin(Inventory.class)
public abstract class TwoHandAutoSlotMixin {
	@Shadow
	public abstract ItemStack getItem(int slot);

	@Shadow
	public abstract int getContainerSize();

	@Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
	private void pantheon$skipBlankedSlots(final CallbackInfoReturnable<Integer> cir) {
		if (!PantheonConfig.get().twoHandSlotMode) {
			return;
		}
		for (int i = 0; i < this.getContainerSize(); i++) {
			if (i < HotbarOwnersPayload.SLOT_COUNT && i != HotbarOwnersPayload.TWO_HAND_ACTIVE_SLOT) {
				continue;
			}
			if (this.getItem(i).isEmpty()) {
				cir.setReturnValue(i);
				return;
			}
		}
		cir.setReturnValue(-1);
	}

	@Inject(method = "getSuitableHotbarSlot", at = @At("HEAD"), cancellable = true)
	private void pantheon$onlyActiveSlotIsSuitable(final CallbackInfoReturnable<Integer> cir) {
		if (PantheonConfig.get().twoHandSlotMode) {
			cir.setReturnValue(HotbarOwnersPayload.TWO_HAND_ACTIVE_SLOT);
		}
	}
}
