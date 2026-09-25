package com.pantheon.client;

import java.util.UUID;

import org.joml.Vector3fc;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Draws every active location mark ({@link LocationMarkClient}) as a small
 * diamond in its placer's color, with their name and the distance under it.
 * A mark off screen - or behind us - is pinned to the nearest screen edge
 * in its direction, so it can always be found by turning towards it.
 */
public final class LocationMarkOverlay implements HudElement {
	private static final int MARKER_RADIUS = 4;
	private static final int EDGE_MARGIN = 12;
	private static final int OUTLINE_COLOR = 0xFF000000;
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null || minecraft.gui.screen() != null
			|| !PantheonModClient.getLastKnownConfig().locationMarks) {
			return;
		}
		Camera camera = minecraft.gameRenderer.mainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		Identifier dimension = minecraft.level.dimension().identifier();
		Vec3 cameraPos = camera.position();
		Vector3fc forward = camera.forwardVector();
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();

		for (LocationMarkClient.Mark mark : LocationMarkClient.activeMarks()) {
			if (!mark.dimension().equals(dimension)) {
				continue;
			}
			Vec3 target = Vec3.atCenterOf(mark.pos());
			Vec3 offset = target.subtract(cameraPos);
			boolean behind = offset.x * forward.x() + offset.y * forward.y() + offset.z * forward.z() <= 0.0;

			Vec3 ndc = minecraft.gameRenderer.projectPointToScreen(target);
			double nx = ndc.x;
			double ny = ndc.y;
			if (behind) {
				// The projection mirrors points behind the camera - flip back so
				// the edge marker points the way you'd actually have to turn.
				nx = -nx;
				ny = -ny;
			}
			boolean onScreen = !behind && Math.abs(nx) <= 1.0 && Math.abs(ny) <= 1.0;
			if (!onScreen) {
				// Push out along the same direction until it touches the edge.
				double scale = Math.max(Math.abs(nx), Math.abs(ny));
				if (scale < 1.0E-6) {
					nx = 0.0;
					ny = -1.0;
				} else {
					nx /= scale;
					ny /= scale;
				}
			}

			int x = (int) Math.round((nx + 1.0) / 2.0 * width);
			int y = (int) Math.round((1.0 - ny) / 2.0 * height);
			x = Math.clamp(x, EDGE_MARGIN, width - EDGE_MARGIN);
			y = Math.clamp(y, EDGE_MARGIN, height - EDGE_MARGIN * 2);

			drawMarker(graphics, minecraft.font, x, y, colorFor(mark.placer()),
				mark.placerName() + " " + Math.round(offset.length()) + "m");
		}
	}

	private static int colorFor(final UUID placer) {
		Integer color = PantheonModClient.getHotbarColors().get(placer);
		return color != null ? color : HotbarColors.vanillaColorFor(placer);
	}

	private static void drawMarker(final GuiGraphicsExtractor graphics, final Font font, final int x, final int y, final int color, final String label) {
		// A diamond built from horizontal strips, with a one-pixel dark rim so it
		// reads against both sky and terrain.
		for (int dy = -MARKER_RADIUS - 1; dy <= MARKER_RADIUS + 1; dy++) {
			int half = MARKER_RADIUS + 1 - Math.abs(dy);
			graphics.fill(x - half, y + dy, x + half + 1, y + dy + 1, OUTLINE_COLOR);
		}
		for (int dy = -MARKER_RADIUS; dy <= MARKER_RADIUS; dy++) {
			int half = MARKER_RADIUS - Math.abs(dy);
			graphics.fill(x - half, y + dy, x + half + 1, y + dy + 1, color);
		}
		int textWidth = font.width(label);
		int textX = Math.clamp(x - textWidth / 2, 2, graphics.guiWidth() - textWidth - 2);
		graphics.text(font, label, textX, y + MARKER_RADIUS + 3, TEXT_COLOR, true);
	}
}
