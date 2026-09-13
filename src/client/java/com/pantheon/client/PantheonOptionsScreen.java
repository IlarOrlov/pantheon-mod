package com.pantheon.client;

import com.pantheon.PantheonConfig;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Lets whoever opens it (bound to the "Open Pantheon Settings" key, unbound
 * by default) view and change the server's shared settings. The server only
 * actually applies the change if the sender is the singleplayer host or an
 * operator ({@link com.pantheon.network.PantheonNetworking#canConfigure}); a
 * request from anyone else is dropped with a chat message telling them so, so
 * this screen stays safe to open (read-only) for everyone.
 */
public final class PantheonOptionsScreen extends Screen {
	private final Screen parent;
	private PantheonConfig working;

	private static final int ROW_HEIGHT = 24;
	private static final int WIDGET_WIDTH = 240;

	public PantheonOptionsScreen(final Screen parent, final PantheonConfig initial) {
		super(Component.literal("Pantheon Settings"));
		this.parent = parent;
		this.working = initial.copy();
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int y = this.height / 2 - (ROW_HEIGHT * 5 / 2);

		this.addRenderableWidget(CycleButton.onOffBuilder(this.working.syncCraftingGrid)
			.create(centerX - WIDGET_WIDTH / 2, y, WIDGET_WIDTH, 20, Component.literal("Sync crafting grid"),
				(button, value) -> this.working.syncCraftingGrid = value));
		y += ROW_HEIGHT;

		this.addRenderableWidget(CycleButton.onOffBuilder(this.working.syncArmor)
			.create(centerX - WIDGET_WIDTH / 2, y, WIDGET_WIDTH, 20, Component.literal("Sync armor"),
				(button, value) -> this.working.syncArmor = value));
		y += ROW_HEIGHT;

		this.addRenderableWidget(CycleButton.onOffBuilder(this.working.syncOffhand)
			.create(centerX - WIDGET_WIDTH / 2, y, WIDGET_WIDTH, 20, Component.literal("Sync offhand"),
				(button, value) -> this.working.syncOffhand = value));
		y += ROW_HEIGHT;

		this.addRenderableWidget(CycleButton.onOffBuilder(this.working.enableHotbarOwnership)
			.create(centerX - WIDGET_WIDTH / 2, y, WIDGET_WIDTH, 20, Component.literal("Hotbar slot ownership (caps server at 9 players)"),
				(button, value) -> this.working.enableHotbarOwnership = value));
		y += ROW_HEIGHT * 2;

		this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
			PantheonModClient.sendConfigUpdate(this.working);
			this.onClose();
		}).bounds(centerX - WIDGET_WIDTH / 2, y, WIDGET_WIDTH / 2 - 4, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
			.bounds(centerX + 4, y, WIDGET_WIDTH / 2 - 4, 20).build());
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
