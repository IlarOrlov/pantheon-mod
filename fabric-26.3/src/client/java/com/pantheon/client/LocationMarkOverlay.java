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
 * diamond in its placer's color, with their name and the distance under it,
 * both drawn larger the nearer the mark is.
 * A mark off screen - or behind us - is pinned to the nearest screen edge
 * in its direction, so it can always be found by turning towards it.
 */
public final class LocationMarkOverlay implements HudElement {
	/** The diamond is drawn at this radius and then scaled, so it stays a clean shape at any size. */
	private static final int DRAW_RADIUS = 12;
	private static final int DRAW_RIM = 3;
	/** On-screen diamond radius (GUI pixels) for a mark right next to us, and for one far away. */
	private static final float NEAR_RADIUS = 2.4F;
	private static final float FAR_RADIUS = 1.2F;
	/** Label size relative to normal text, near and far. */
	private static final float NEAR_LABEL_SCALE = 0.6F;
	private static final float FAR_LABEL_SCALE = 0.45F;
	/** Distances (blocks) at and past which a mark is drawn at its near/far size - scaled logarithmically between. */
	private static final double NEAR_DISTANCE = 4.0;
	private static final double FAR_DISTANCE = 256.0;
	private static final int EDGE_MARGIN = 10;
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

			double distance = offset.length();
			float t = (float) Math.clamp(Math.log(distance / NEAR_DISTANCE) / Math.log(FAR_DISTANCE / NEAR_DISTANCE), 0.0, 1.0);
			drawMarker(graphics, minecraft.font, x, y, colorFor(mark.placer()),
				mark.placerName() + " " + Math.round(distance) + "m",
				NEAR_RADIUS + (FAR_RADIUS - NEAR_RADIUS) * t,
				NEAR_LABEL_SCALE + (FAR_LABEL_SCALE - NEAR_LABEL_SCALE) * t);
		}
	}

	private static int colorFor(final UUID placer) {
		Integer color = PantheonModClient.getHotbarColors().get(placer);
		return color != null ? color : HotbarColors.vanillaColorFor(placer);
	}

	private static void drawMarker(final GuiGraphicsExtractor graphics, final Font font, final int x, final int y, final int color,
		final String label, final float radius, final float labelScale) {
		// A diamond built from horizontal strips, with a dark rim so it reads
		// against both sky and terrain - drawn large and scaled down to size.
		float scale = radius / DRAW_RADIUS;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		int outer = DRAW_RADIUS + DRAW_RIM;
		for (int dy = -outer; dy <= outer; dy++) {
			int half = outer - Math.abs(dy);
			graphics.fill(-half, dy, half + 1, dy + 1, OUTLINE_COLOR);
		}
		for (int dy = -DRAW_RADIUS; dy <= DRAW_RADIUS; dy++) {
			int half = DRAW_RADIUS - Math.abs(dy);
			graphics.fill(-half, dy, half + 1, dy + 1, color);
		}
		graphics.pose().popMatrix();
		// The label is drawn scaled down, so it stays readable without
		// covering whatever is being marked.
		float textWidth = font.width(label) * labelScale;
		float textX = Math.clamp(x - textWidth / 2.0F, 2.0F, graphics.guiWidth() - textWidth - 2.0F);
		graphics.pose().pushMatrix();
		graphics.pose().translate(textX, y + radius * outer / DRAW_RADIUS + 2.0F);
		graphics.pose().scale(labelScale, labelScale);
		graphics.text(font, label, 0, 0, TEXT_COLOR, true);
		graphics.pose().popMatrix();
	}
}
