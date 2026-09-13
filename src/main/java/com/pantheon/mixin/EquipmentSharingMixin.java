package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pantheon.SharedInventory;
import com.pantheon.PantheonConfig;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerEquipment;
import net.minecraft.world.item.ItemStack;

/**
 * Redirects armor and offhand reads/writes to {@link SharedInventory#SHARED_EQUIPMENT}
 * whenever {@link PantheonConfig} says that slot type should be shared, so
 * armor/offhand toggle independently and can be flipped live without
 * reconnecting. Mainhand is untouched here - {@link PlayerEquipment} already
 * routes it through the (always-shared) {@link com.pantheon.mixin.InventorySharingMixin}.
 */
@Mixin(PlayerEquipment.class)
public abstract class EquipmentSharingMixin {
	@Shadow
	@Final
	private Player player;

	@Inject(method = "get", at = @At("HEAD"), cancellable = true)
	private void pantheon$get(final EquipmentSlot slot, final CallbackInfoReturnable<ItemStack> cir) {
		if (this.player instanceof ServerPlayer && PantheonConfig.get().isEquipmentSlotShared(slot)) {
			cir.setReturnValue(SharedInventory.SHARED_EQUIPMENT.getOrDefault(slot, ItemStack.EMPTY));
		}
	}

	@Inject(method = "set", at = @At("HEAD"), cancellable = true)
	private void pantheon$set(final EquipmentSlot slot, final ItemStack stack, final CallbackInfoReturnable<ItemStack> cir) {
		if (this.player instanceof ServerPlayer && PantheonConfig.get().isEquipmentSlotShared(slot)) {
			ItemStack previous = SharedInventory.SHARED_EQUIPMENT.put(slot, stack == null ? ItemStack.EMPTY : stack);
			cir.setReturnValue(previous == null ? ItemStack.EMPTY : previous);
		}
	}
}
