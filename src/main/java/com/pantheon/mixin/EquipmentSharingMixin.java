package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.SharedInventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerEquipment;

/**
 * Shares armor/offhand (everything except mainhand, which PlayerEquipment
 * already routes through the player's own Inventory#getSelectedItem) across
 * every ServerPlayer by pointing their equipment map at the same backing map.
 *
 * <p>The backing "items" map is declared on EntityEquipment, not on
 * PlayerEquipment itself, so it is reached through {@link EntityEquipmentAccessor}
 * (applied to EntityEquipment, and therefore inherited here) rather than a
 * direct {@code @Shadow}, which Mixin does not resolve across superclasses.
 */
@Mixin(PlayerEquipment.class)
public abstract class EquipmentSharingMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void pantheon$shareEquipment(final Player player, final CallbackInfo ci) {
		if (player instanceof ServerPlayer) {
			EntityEquipmentAccessor self = (EntityEquipmentAccessor) (Object) this;
			EntityEquipmentAccessor shared = (EntityEquipmentAccessor) SharedInventory.EQUIPMENT;
			self.pantheon$setItems(shared.pantheon$getItems());
		}
	}
}
