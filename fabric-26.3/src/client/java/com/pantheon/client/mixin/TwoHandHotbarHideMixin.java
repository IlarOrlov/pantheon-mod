package com.pantheon.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.client.PantheonModClient;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;

/**
 * Two-hand mode replaces vanilla's normal 9-slot item hotbar with a single
 * centered slot ({@link com.pantheon.client.TwoHandHotbarOverlay}) - this
 * cancels vanilla's own render of it so the two don't draw on top of each
 * other. Only the item row itself is cancelled; health/food/XP/selected-item
 * name etc. are separate calls from {@code extractHotbarAndDecorations} and
 * keep rendering normally.
 */
@Mixin(Hud.class)
public abstract class TwoHandHotbarHideMixin {
	@Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
	private void pantheon$hideVanillaHotbar(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker, final CallbackInfo ci) {
		if (PantheonModClient.getLastKnownConfig().twoHandSlotMode) {
			ci.cancel();
		}
	}
}
