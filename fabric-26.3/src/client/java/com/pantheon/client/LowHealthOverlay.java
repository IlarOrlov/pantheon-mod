package com.pantheon.client;

import com.pantheon.FunnyMessages;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/**
 * A red screen tint (with a rotating joke caption) that ramps up the closer
 * the local player's own health gets to 0, toggleable in the settings screen
 * ({@link PantheonClientConfig#lowHealthWarningEnabled}) and purely
 * client-local - it never affects gameplay or gets sent anywhere.
 */
public final class LowHealthOverlay implements HudElement {
	private static final float WARNING_THRESHOLD = 6f;
	private static final int CAPTION_REFRESH_TICKS = 100;

	private static String cachedLine = "";
	private static long lastPickGameTime = Long.MIN_VALUE;

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
		if (!PantheonClientConfig.get().lowHealthWarningEnabled) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.getConnection() == null || minecraft.level == null) {
			return;
		}

		float health = minecraft.player.getHealth();
		if (health <= 0f || health > WARNING_THRESHOLD) {
			return;
		}

		float intensity = 1f - (health / WARNING_THRESHOLD);
		int alpha = 40 + (int) (intensity * 90);
		int tint = ARGB.color(alpha, 140, 0, 0);
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), tint);

		long gameTime = minecraft.level.getGameTime();
		if (cachedLine.isEmpty() || gameTime - lastPickGameTime > CAPTION_REFRESH_TICKS) {
			cachedLine = FunnyMessages.randomLowHealthLine(minecraft.player.getGameProfile().name());
			lastPickGameTime = gameTime;
		}
		graphics.centeredText(minecraft.font, cachedLine, graphics.guiWidth() / 2, 20, 0xFFFFFF);
	}
}
