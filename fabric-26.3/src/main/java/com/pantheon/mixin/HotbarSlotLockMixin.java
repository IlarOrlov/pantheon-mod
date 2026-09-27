package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pantheon.SlotLocks;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A hotbar slot locked to the player looking at it (someone else's owned
 * slot, or a blanked two-hand-mode slot) refuses both placing into and
 * taking out of it, at the {@link Slot} level every vanilla click type -
 * and every inventory mod built on them, like Mouse Tweaks - already asks
 * before touching a slot: shift-click destinations, drag-splitting,
 * double-click collecting (PICKUP_ALL), plain clicks, Q-drops.
 *
 * <p>Runs on both sides via {@link SlotLocks}: the server is the authority,
 * the client asks the same question of its last-received ownership so its
 * own prediction of a click lands where the server's real one will instead
 * of flashing an item into a slot and having it vanish.
 */
@Mixin(Slot.class)
public abstract class HotbarSlotLockMixin {
	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void pantheon$blockPlace(final ItemStack stack, final CallbackInfoReturnable<Boolean> cir) {
		if (SlotLocks.isLocked((Slot) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void pantheon$blockPickup(final Player player, final CallbackInfoReturnable<Boolean> cir) {
		if (SlotLocks.isLocked((Slot) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
