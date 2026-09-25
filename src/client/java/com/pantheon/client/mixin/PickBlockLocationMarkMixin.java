package com.pantheon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.client.LocationMarkClient;

import net.minecraft.client.Minecraft;

/**
 * Middle-click (the pick-block key) places a location mark when there's
 * nothing in reach to pick, or while sneaking - otherwise vanilla's pick
 * block runs untouched. See {@link LocationMarkClient#shouldMarkInsteadOfPick}.
 */
@Mixin(Minecraft.class)
public abstract class PickBlockLocationMarkMixin {
	@Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
	private void pantheon$placeLocationMark(final CallbackInfo ci) {
		Minecraft minecraft = (Minecraft) (Object) this;
		if (LocationMarkClient.shouldMarkInsteadOfPick(minecraft)) {
			ci.cancel();
			LocationMarkClient.placeMark(minecraft);
		}
	}
}
