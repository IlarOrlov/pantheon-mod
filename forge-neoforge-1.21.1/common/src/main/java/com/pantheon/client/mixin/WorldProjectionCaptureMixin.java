package com.pantheon.client.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.client.WorldProjection;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;

/** Records the matrices each frame's world is drawn with - see {@link WorldProjection}. */
@Mixin(LevelRenderer.class)
public abstract class WorldProjectionCaptureMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void pantheon$captureProjection(final DeltaTracker deltaTracker, final boolean renderBlockOutline, final Camera camera,
		final GameRenderer gameRenderer, final LightTexture lightTexture, final Matrix4f frustumMatrix, final Matrix4f projectionMatrix,
		final CallbackInfo ci) {
		WorldProjection.capture(camera.getPosition(), frustumMatrix, projectionMatrix);
	}
}
