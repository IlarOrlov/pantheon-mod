package com.pantheon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.DeathDropSaves;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Notices a death-dropped item ({@link DeathDropSaves#DEATH_DROP_TAG})
 * actually being picked up - wholly or partly - so the world gets saved
 * with it back in the inventory instead of still lying on the ground.
 */
@Mixin(ItemEntity.class)
public abstract class DeathDropPickupSaveMixin {
	@Unique
	private int pantheon$countBefore = -1;

	@Inject(method = "playerTouch", at = @At("HEAD"))
	private void pantheon$capture(final Player player, final CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		this.pantheon$countBefore = !self.level().isClientSide() && self.entityTags().contains(DeathDropSaves.DEATH_DROP_TAG)
			? self.getItem().getCount()
			: -1;
	}

	@Inject(method = "playerTouch", at = @At("RETURN"))
	private void pantheon$savePickup(final Player player, final CallbackInfo ci) {
		int before = this.pantheon$countBefore;
		this.pantheon$countBefore = -1;
		if (before < 0) {
			return;
		}
		ItemEntity self = (ItemEntity) (Object) this;
		// A full pickup discards the entity and then puts the stack's count
		// back, so "removed" is the signal there; a partial one just shrinks it.
		if (self.isRemoved() || self.getItem().getCount() != before) {
			DeathDropSaves.onDeathDropPickedUp(((ServerLevel) self.level()).getServer());
		}
	}
}
