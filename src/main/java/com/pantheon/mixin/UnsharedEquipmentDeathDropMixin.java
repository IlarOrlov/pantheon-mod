package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.DeathDropSaves;
import com.pantheon.PantheonConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Pantheon forces {@code keepInventory} on globally to protect the *shared*
 * 36-slot inventory (and, when it's on, shared armor/offhand - see
 * {@code SharedStats#dropSharedInventoryOnce}) from a death that's supposed
 * to survive. That gamerule doesn't distinguish shared from personal gear
 * though: {@code Player#dropEquipment} - which normally drops both the
 * 36-slot inventory *and* every equipped item via {@code Inventory#dropAll}
 * - only runs any of that when {@code keepInventory} is off, so with it
 * forced on, a player's own currently-*unshared* armor/offhand (which isn't
 * covered by the shared-inventory drop at all, since it was never part of
 * that shared list to begin with) never drops on death either - collateral
 * damage from a gamerule that was only ever meant to protect the shared
 * stuff.
 *
 * <p>Runs on every death (whether ordinary, or one of the deaths
 * {@code SharedStats} forces to follow the shared health pool hitting 0),
 * since {@code dropEquipment} is always called exactly once per death via
 * {@code dropAllDeathLoot}, regardless of what triggered it.
 */
@Mixin(Player.class)
public abstract class UnsharedEquipmentDeathDropMixin {
	private static final EquipmentSlot[] ARMOR_AND_OFFHAND_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND
	};

	@Inject(method = "dropEquipment", at = @At("TAIL"))
	private void pantheon$dropUnsharedEquipment(final ServerLevel level, final CallbackInfo ci) {
		if (!(((Object) this) instanceof ServerPlayer player)) {
			return;
		}
		if (!Boolean.TRUE.equals(level.getGameRules().get(GameRules.KEEP_INVENTORY))) {
			// keepInventory is (unexpectedly) off - vanilla's own drop, called
			// earlier in this same method, already handled every equipment
			// slot itself, shared or not. Nothing left for us to do here.
			return;
		}

		PantheonConfig config = PantheonConfig.get();
		for (EquipmentSlot slot : ARMOR_AND_OFFHAND_SLOTS) {
			if (config.isEquipmentSlotShared(slot)) {
				// Shared slots are handled once per shared-death event, not
				// once per player, by SharedStats#dropSharedInventoryOnce.
				continue;
			}
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				player.setItemSlot(slot, ItemStack.EMPTY);
				DeathDropSaves.tagDeathDrop(player.spawnAtLocation(level, stack));
			}
		}
	}
}
