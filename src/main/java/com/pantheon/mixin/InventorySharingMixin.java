package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.SharedInventory;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;

/**
 * Every ServerPlayer's Inventory is wired to point at the same backing item
 * list, so putting an item in slot N as one player puts it there for everyone.
 * The "selected" hotbar index stays per-instance (each player can still aim
 * a different one of the 9 shared slots as their own held item).
 */
@Mixin(Inventory.class)
public abstract class InventorySharingMixin {
	@Shadow
	@Final
	public Player player;

	@Shadow
	@Final
	@Mutable
	private NonNullList<ItemStack> items;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void pantheon$shareItems(final Player player, final EntityEquipment equipment, final CallbackInfo ci) {
		if (player instanceof ServerPlayer) {
			this.items = SharedInventory.ITEMS;
		}
	}

	/**
	 * {@code load()} starts with {@code this.items.clear()} before repopulating
	 * from the joining player's own saved data - for a ServerPlayer that would
	 * wipe the *shared* list (everyone's items) down to just whatever this one
	 * player personally had saved from their last session. The shared inventory
	 * is memory-only by design (reset once, on server start) and must never be
	 * touched by a single player's save file, so this just skips loading
	 * entirely for ServerPlayers - their current items stay whatever the shared
	 * inventory already has.
	 */
	@Inject(method = "load", at = @At("HEAD"), cancellable = true)
	private void pantheon$skipLoadForServerPlayers(final ValueInput.TypedInputList<ItemStackWithSlot> list, final CallbackInfo ci) {
		if (this.player instanceof ServerPlayer) {
			ci.cancel();
		}
	}
}
