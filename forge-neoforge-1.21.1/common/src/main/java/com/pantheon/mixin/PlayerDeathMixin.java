package com.pantheon.mixin;

import com.pantheon.PantheonMod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After a player has fully died - drops spawned and all. A death a loader
 * event cancelled returns early and never reaches this.
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerDeathMixin {
	@Inject(method = "die", at = @At("TAIL"))
	private void pantheon$afterDeath(final DamageSource cause, final CallbackInfo ci) {
		PantheonMod.onPlayerDeath((ServerPlayer) (Object) this);
	}
}
