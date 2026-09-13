package com.pantheon.client;

import java.util.List;
import java.util.UUID;

import com.pantheon.network.HotbarOwnersPayload;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

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
		if (minecraft.player == null || minecraft.getConnection() == null) {
			return;
		}

		List<UUID> owners = PantheonModClient.getHotbarOwners();
		List<Integer> colors = PantheonModClient.getHotbarOwnerColors();

		int left = graphics.guiWidth() / 2 - HOTBAR_WIDTH / 2;
		int top = graphics.guiHeight() - SLOT_SIZE - 1;

		for (int slot = 0; slot < owners.size() && slot < HotbarOwnersPayload.SLOT_COUNT; slot++) {
			UUID owner = owners.get(slot);
			if (owner.equals(HotbarOwnersPayload.NO_OWNER)) {
				continue;
			}

			int x = left + slot * SLOT_SIZE;
			int color = HotbarColors.colorFor(colors.get(slot));
			drawFrame(graphics, x, top, SLOT_SIZE, SLOT_SIZE, color);

			PlayerInfo info = minecraft.getConnection().getPlayerInfo(owner);
			if (info != null) {
				String name = info.getProfile().name();
				if (!name.isEmpty()) {
					String initial = name.substring(0, 1).toUpperCase();
					graphics.text(minecraft.font, initial, x + 6, top - 10, color);
				}
			}
		}
	}

	static void drawFrame(final GuiGraphicsExtractor graphics, final int x, final int y, final int width, final int height, final int color) {
		graphics.fill(x, y, x + width, y + BORDER_THICKNESS, color);
		graphics.fill(x, y + height - BORDER_THICKNESS, x + width, y + height, color);
		graphics.fill(x, y, x + BORDER_THICKNESS, y + height, color);
		graphics.fill(x + width - BORDER_THICKNESS, y, x + width, y + height, color);
	}
}
