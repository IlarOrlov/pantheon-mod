package com.pantheon.client;

import java.util.Map;
import java.util.UUID;

import com.pantheon.network.HotbarOwnersPayload;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws a colored frame around each hotbar slot that {@link PantheonModClient}
 * currently reports as "owned" by another online player - the same color
 * shown around that slot for every other player - so nobody can miss which
 * slots are off-limits to them right now. Purely decorative: the actual lock
 * is enforced server-side by {@code com.pantheon.mixin.HotbarLockMixin}.
 */
public final class HotbarOwnerOverlay implements HudElement {
	private static final int SLOT_SIZE = 20;
	private static final int HOTBAR_WIDTH = SLOT_SIZE * HotbarOwnersPayload.SLOT_COUNT;
	private static final int BORDER_THICKNESS = 2;

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.getConnection() == null || minecraft.gui.screen() != null) {
			// This draws at a fixed spot near the bottom of the whole screen -
			// the normal gameplay hotbar HUD position. Any open screen (inventory,
			// chest, ...) has its own hotbar row drawn elsewhere (and gets its own
			// correctly-positioned frames from HotbarLockFrameMixin), so this fixed
			// HUD position should stay blank rather than show frames that don't
			// line up with anything visible while a screen covers the game.
			return;
		}

		int left = graphics.guiWidth() / 2 - HOTBAR_WIDTH / 2;
		int top = graphics.guiHeight() - SLOT_SIZE - 1;

		Map<UUID, Integer> colors = PantheonModClient.getHotbarColors();
		for (int slot = 0; slot < HotbarOwnersPayload.SLOT_COUNT; slot++) {
			UUID owner = PantheonModClient.effectiveOwner(slot);
			if (owner.equals(HotbarOwnersPayload.NO_OWNER)) {
				continue;
			}

			int x = left + slot * SLOT_SIZE;
			Integer color = colors.get(owner);
			drawFrame(graphics, x, top, SLOT_SIZE, SLOT_SIZE, color != null ? color : HotbarColors.vanillaColorFor(owner));
		}
	}

	static void drawFrame(final GuiGraphicsExtractor graphics, final int x, final int y, final int width, final int height, final int color) {
		graphics.fill(x, y, x + width, y + BORDER_THICKNESS, color);
		graphics.fill(x, y + height - BORDER_THICKNESS, x + width, y + height, color);
		graphics.fill(x, y, x + BORDER_THICKNESS, y + height, color);
		graphics.fill(x + width - BORDER_THICKNESS, y, x + width, y + height, color);
	}
}
