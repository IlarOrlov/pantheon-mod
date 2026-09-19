package com.pantheon.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Draws two-hand mode's single, centered "hotbar slot" - the main-hand item
 * that's always in slot 0 while this mode is on - replacing vanilla's normal
 * 9-slot hotbar, which {@link com.pantheon.client.mixin.TwoHandHotbarHideMixin}
 * cancels while this mode is active. The off-hand slot isn't drawn here -
 * it's not part of the hotbar row at all, so it isn't affected by any of
 * this in the first place.
 */
public final class TwoHandHotbarOverlay implements HudElement {
	private static final int SLOT_SIZE = 20;
	private static final int ICON_INSET = 2;
	private static final int BACKGROUND_COLOR = 0x8B8B8B8B;
	private static final int BORDER_COLOR = 0xFFFFFFFF;

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.getConnection() == null || minecraft.gui.screen() != null) {
			return;
		}
		if (!PantheonModClient.getLastKnownConfig().twoHandSlotMode) {
			return;
		}

		int x = graphics.guiWidth() / 2 - SLOT_SIZE / 2;
		int y = graphics.guiHeight() - SLOT_SIZE - 1;

		graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, BACKGROUND_COLOR);
		HotbarOwnerOverlay.drawFrame(graphics, x, y, SLOT_SIZE, SLOT_SIZE, BORDER_COLOR);

		ItemStack stack = minecraft.player.getInventory().getItem(0);
		if (!stack.isEmpty()) {
			int itemX = x + ICON_INSET;
			int itemY = y + ICON_INSET;
			graphics.item(stack, itemX, itemY);
			graphics.itemDecorations(minecraft.font, stack, itemX, itemY);
		}
	}
}
