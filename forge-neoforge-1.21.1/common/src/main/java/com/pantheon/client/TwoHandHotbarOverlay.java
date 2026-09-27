package com.pantheon.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.pantheon.network.HotbarOwnersPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Draws two-hand mode's single, centered "hotbar slot" - the main-hand item
 * that's always in the center slot ({@link HotbarOwnersPayload#TWO_HAND_ACTIVE_SLOT})
 * while this mode is on, plus the off-hand item to its left when it's holding
 * one - replacing vanilla's normal 9-slot hotbar,
 * which {@link com.pantheon.client.mixin.TwoHandHotbarHideMixin} cancels
 * (including its own off-hand icon) while this mode is active.
 *
 * <p>Built from the same sprites and offsets vanilla's own hotbar uses
 * ({@code net.minecraft.client.gui.Gui#renderItemHotbar}, decompiled to get
 * these exact numbers) rather than a hand-drawn approximation, so a single
 * centered slot reads as "the Minecraft hotbar, one slot" rather than a
 * custom widget: the selection-frame sprite doubles as this slot's border
 * *and* background (it's always "selected" - there's nothing else it could
 * be), and the off-hand sprite/offsets are vanilla's own left-handed variant
 * unconditionally, since the user asked for off-hand specifically on the
 * left regardless of main-hand setting - unlike vanilla, which puts it on
 * whichever side is opposite the main hand.
 */
public final class TwoHandHotbarOverlay implements LayeredDraw.Layer {
	private static final ResourceLocation HOTBAR_SELECTION_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");
	private static final ResourceLocation HOTBAR_OFFHAND_LEFT_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_left");

	// Vanilla's own selection-frame sprite size/offsets (hotbar_selection.png
	// is 24x23; its item sits 4px right and 4px down from its own top-left -
	// see Gui#renderItemHotbar: frame at slot*20+hotbarLeft-1, item at
	// slot*20+hotbarLeft+3, both starting from the same guiHeight-23 row).
	private static final int SLOT_WIDTH = 24;
	private static final int SLOT_HEIGHT = 23;
	private static final int ITEM_OFFSET_X = 4;
	private static final int ITEM_OFFSET_Y = 4;

	// Vanilla's own off-hand sprite size/offset (hotbar_offhand_left.png is
	// 29x24, sitting flush against the main bar's left edge; its item sits
	// 3px right, 4px down from the sprite's own top-left).
	private static final int OFFHAND_SPRITE_WIDTH = 29;
	private static final int OFFHAND_SPRITE_HEIGHT = 24;
	private static final int OFFHAND_ITEM_OFFSET_X = 3;
	private static final int OFFHAND_ITEM_OFFSET_Y = 4;

	private static final int BACKGROUND_COLOR = 0x8B8B8B8B;

	@Override
	public void render(final GuiGraphics graphics, final DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.getConnection() == null || minecraft.screen != null || minecraft.options.hideGui) {
			return;
		}
		if (!PantheonModClient.getLastKnownConfig().twoHandSlotMode) {
			return;
		}

		int frameX = graphics.guiWidth() / 2 - SLOT_WIDTH / 2;
		int frameY = graphics.guiHeight() - SLOT_HEIGHT;

		// The selection sprite's border is transparent in the middle - fill a
		// dark backdrop behind it first, same as every other hotbar slot has,
		// inset a pixel so the sprite's own border art still reads on top.
		graphics.fill(frameX + 1, frameY + 1, frameX + SLOT_WIDTH - 1, frameY + SLOT_HEIGHT - 1, BACKGROUND_COLOR);
		RenderSystem.enableBlend();
		graphics.blitSprite(HOTBAR_SELECTION_SPRITE, frameX, frameY, SLOT_WIDTH, SLOT_HEIGHT);

		ItemStack offhand = minecraft.player.getOffhandItem();
		int offhandX = frameX - OFFHAND_SPRITE_WIDTH;
		if (!offhand.isEmpty()) {
			graphics.blitSprite(HOTBAR_OFFHAND_LEFT_SPRITE, offhandX, frameY, OFFHAND_SPRITE_WIDTH, OFFHAND_SPRITE_HEIGHT);
		}
		RenderSystem.disableBlend();

		ItemStack main = minecraft.player.getInventory().getItem(HotbarOwnersPayload.TWO_HAND_ACTIVE_SLOT);
		if (!main.isEmpty()) {
			int itemX = frameX + ITEM_OFFSET_X;
			int itemY = frameY + ITEM_OFFSET_Y;
			graphics.renderItem(main, itemX, itemY);
			graphics.renderItemDecorations(minecraft.font, main, itemX, itemY);
		}

		if (!offhand.isEmpty()) {
			int itemX = offhandX + OFFHAND_ITEM_OFFSET_X;
			int itemY = frameY + OFFHAND_ITEM_OFFSET_Y;
			graphics.renderItem(offhand, itemX, itemY);
			graphics.renderItemDecorations(minecraft.font, offhand, itemX, itemY);
		}
	}
}
