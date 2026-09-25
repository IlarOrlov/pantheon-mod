package com.pantheon.mixin;

import java.util.Arrays;
import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.MenuPushTimes;

import net.minecraft.core.NonNullList;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Records when each slot was last sent to the client - see {@link MenuPushTimes}. */
@Mixin(AbstractContainerMenu.class)
public abstract class MenuPushTimesMixin implements MenuPushTimes {
	@Shadow
	@Final
	public NonNullList<Slot> slots;

	@Shadow
	@Final
	private NonNullList<RemoteSlot> remoteSlots;

	@Unique
	private long[] pantheon$pushTimes = new long[0];

	@Override
	public long pantheon$lastPushMillis(final int slot) {
		return slot >= 0 && slot < this.pantheon$pushTimes.length ? this.pantheon$pushTimes[slot] : 0L;
	}

	@Override
	public void pantheon$setLastPushMillis(final int slot, final long millis) {
		if (slot < 0) {
			return;
		}
		if (slot >= this.pantheon$pushTimes.length) {
			this.pantheon$pushTimes = Arrays.copyOf(this.pantheon$pushTimes, Math.max(slot + 1, this.slots.size()));
		}
		this.pantheon$pushTimes[slot] = millis;
	}

	@Inject(
		method = "synchronizeSlotToRemote",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/ContainerSynchronizer;sendSlotChange(Lnet/minecraft/world/inventory/AbstractContainerMenu;ILnet/minecraft/world/item/ItemStack;)V")
	)
	private void pantheon$recordSlotPush(final int slot, final ItemStack current, final Supplier<ItemStack> currentCopy, final CallbackInfo ci) {
		this.pantheon$setLastPushMillis(slot, Util.getMillis());
	}

	/** A full resend only counts as a push for the slots it actually changes on the client. */
	@Inject(method = "sendAllDataToRemote", at = @At("HEAD"))
	private void pantheon$recordFullPush(final CallbackInfo ci) {
		long now = Util.getMillis();
		for (int i = 0; i < this.slots.size(); i++) {
			if (!this.remoteSlots.get(i).matches(this.slots.get(i).getItem())) {
				this.pantheon$setLastPushMillis(i, now);
			}
		}
	}
}
