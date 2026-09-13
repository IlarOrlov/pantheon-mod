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
 * Draws a small colored tag (and the owning player's initial) above each
 * hotbar slot that is currently "assigned" to a player. Purely decorative -
 * every slot still works for every player regardless of this overlay.
 */
public final class HotbarOwnerOverlay implements HudElement {
	private static final int[] SLOT_COLORS = {
		0xFFE6194B, 0xFF3CB44B, 0xFFFFE119, 0xFF4363D8, 0xFFF58231,
		0xFF911EB4, 0xFF46F0F0, 0xFFF032E6, 0xFFBFEF45
	};

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.getConnection() == null) {
			return;
		}

		List<UUID> owners = PantheonModClient.getHotbarOwners();
		int screenCenter = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() - 16 - 3;

		for (int slot = 0; slot < owners.size() && slot < HotbarOwnersPayload.SLOT_COUNT; slot++) {
			UUID owner = owners.get(slot);
			if (owner.equals(HotbarOwnersPayload.NO_OWNER)) {
				continue;
			}

			int x = screenCenter - 90 + slot * 20 + 2;
			int color = SLOT_COLORS[slot % SLOT_COLORS.length];

			graphics.fill(x, y - 4, x + 16, y - 1, color);

			PlayerInfo info = minecraft.getConnection().getPlayerInfo(owner);
			if (info != null) {
				String name = info.getProfile().name();
				if (!name.isEmpty()) {
					String initial = name.substring(0, 1).toUpperCase();
					graphics.text(minecraft.font, initial, x + 5, y - 14, 0xFFFFFFFF);
				}
			}
		}
	}
}
