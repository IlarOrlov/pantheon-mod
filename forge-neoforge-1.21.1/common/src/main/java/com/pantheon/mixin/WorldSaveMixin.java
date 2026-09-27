package com.pantheon.mixin;

import com.pantheon.PantheonMod;

import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every world save (autosave, pause, {@code /save-all}, stop) goes through
 * {@code saveAllChunks} - flag the shared team contents dirty right before
 * it, so they're written out alongside everything else. Neither loader has an
 * event for this exact moment.
 */
@Mixin(MinecraftServer.class)
public abstract class WorldSaveMixin {
	@Inject(method = "saveAllChunks", at = @At("HEAD"))
	private void pantheon$beforeSave(final boolean suppressLog, final boolean flush, final boolean forced, final CallbackInfoReturnable<Boolean> cir) {
		PantheonMod.onBeforeSave((MinecraftServer) (Object) this);
	}
}
