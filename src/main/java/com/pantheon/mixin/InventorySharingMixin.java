package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.PantheonInventoryAccess;
import com.pantheon.TeamManager;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;

/**
 * Every ServerPlayer's Inventory is wired to point at their resolved team's
 * backing item list (see {@link TeamManager}), so putting an item in slot N
 * as one player puts it there for every other member of that team. The
 * "selected" hotbar index stays per-instance (each player can still aim a
 * different one of the 9 shared slots as their own held item).
 */
@Mixin(Inventory.class)
public abstract class InventorySharingMixin implements PantheonInventoryAccess {
	@Shadow
	@Final
	public Player player;

	@Shadow
	@Final
	@Mutable
	private NonNullList<ItemStack> items;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void pantheon$shareItems(final Player player, final EntityEquipment equipment, final CallbackInfo ci) {
		if (player instanceof ServerPlayer serverPlayer) {
			this.items = TeamManager.teamOf(serverPlayer).items;
		}
	}

	@Override
	public void pantheon$setItems(final NonNullList<ItemStack> items) {
		this.items = items;
	}

	/**
	 * {@code load()} starts with {@code this.items.clear()} before repopulating
	 * from the joining player's own saved data - for a ServerPlayer that would
	 * wipe the *shared* list (everyone on their team's items) down to just
	 * whatever this one player personally had saved from their last session.
	 * The shared inventory persists with the world instead (see
	 * {@link com.pantheon.TeamContentsSavedData}) and must never be touched by
	 * a single player's save file, so this just skips loading entirely for
	 * ServerPlayers - their current items stay whatever the shared inventory
	 * already has.
	 */
	@Inject(method = "load", at = @At("HEAD"), cancellable = true)
	private void pantheon$skipLoadForServerPlayers(final ValueInput.TypedInputList<ItemStackWithSlot> list, final CallbackInfo ci) {
		if (this.player instanceof ServerPlayer) {
			ci.cancel();
		}
	}
}
