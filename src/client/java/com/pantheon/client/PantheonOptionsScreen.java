package com.pantheon.client;

import java.util.function.Consumer;

import com.pantheon.PantheonConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Lets whoever opens it (bound to the "Open Pantheon Settings" key, unbound
 * by default) view and change the server's shared settings. The server only
 * actually applies the change if the sender is the singleplayer host or an
 * operator ({@link com.pantheon.network.PantheonNetworking#canConfigure}); a
 * request from anyone else is dropped with a chat message telling them so, so
 * this screen stays safe to open (read-only) for everyone. The one exception
 * is the "This device only" section at the bottom, which is purely local and
 * always takes effect for whoever's looking at it, regardless of permission.
 */
public final class PantheonOptionsScreen extends Screen {
	private static final int ROW_WIDTH = 300;
	private static final int MIN_SCROLL_HEIGHT = 130;
	private static final Component SECTION_COLOR = Component.empty();

	private final Screen parent;
	private final PantheonConfig working;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private ScrollableLayout scrollArea;

	public PantheonOptionsScreen(final Screen parent, final PantheonConfig initial) {
		super(Component.literal("Pantheon Settings"));
		this.parent = parent;
		this.working = initial.copy();
	}

	@Override
	protected void init() {
		this.layout.addTitleHeader(this.title, this.font);

		LinearLayout outer = this.layout.addToContents(LinearLayout.vertical());

		LinearLayout content = LinearLayout.vertical().spacing(6);
		content.defaultCellSetting().alignHorizontallyCenter();

		content.addChild(sectionLabel("What's shared"));
		content.addChild(toggleRow("Armor", this.working.syncArmor, v -> this.working.syncArmor = v));
		content.addChild(toggleRow("Offhand", this.working.syncOffhand, v -> this.working.syncOffhand = v));

		content.addChild(spacer());
		content.addChild(sectionLabel("Hotbar ownership"));
		CycleButton<Boolean> ownershipToggle = toggleRow("Item bar slot ownership (9 players maximum)",
			this.working.enableHotbarOwnership, v -> this.working.enableHotbarOwnership = v);
		ownershipToggle.active = !this.working.twoHandSlotMode;
		content.addChild(ownershipToggle);
		content.addChild(toggleRow("Two-hand mode",
			this.working.twoHandSlotMode, v -> {
				this.working.twoHandSlotMode = v;
				if (v) {
					this.working.enableHotbarOwnership = false;
					ownershipToggle.setValue(false);
				}
				ownershipToggle.active = !v;
			}));

		content.addChild(spacer());
		content.addChild(sectionLabel("Shared vitals"));
		content.addChild(toggleRow("Share health", this.working.syncHealth, v -> this.working.syncHealth = v));
		content.addChild(toggleRow("Share hunger", this.working.syncHunger, v -> this.working.syncHunger = v));
		content.addChild(toggleRow("Share XP", this.working.syncExperience, v -> this.working.syncExperience = v));
		content.addChild(toggleRow("Share potion effects", this.working.syncEffects, v -> this.working.syncEffects = v));

		content.addChild(spacer());
		content.addChild(sectionLabel("Teams"));
		content.addChild(toggleRow("Split into teams", this.working.teamsEnabled, v -> this.working.teamsEnabled = v));

		content.addChild(spacer());
		content.addChild(sectionLabel("Jokes"));
		content.addChild(toggleRow("Crude jokes",
			this.working.crudeHumor, v -> this.working.crudeHumor = v));

		content.addChild(spacer());
		content.addChild(sectionLabel("This device only"));
		content.addChild(toggleRow("Low-health screen warning",
			PantheonClientConfig.get().lowHealthWarningEnabled, PantheonClientConfig::setLowHealthWarningEnabled));

		this.scrollArea = new ScrollableLayout(this.minecraft, content, MIN_SCROLL_HEIGHT);
		this.scrollArea.setMinWidth(ROW_WIDTH + 20);
		outer.addChild(this.scrollArea);

		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.literal("Save"), button -> {
			PantheonModClient.sendConfigUpdate(this.working);
			this.onClose();
		}).width(140).build());
		footer.addChild(Button.builder(Component.literal("Cancel"), button -> this.onClose()).width(140).build());

		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.scrollArea.setMaxHeight(MIN_SCROLL_HEIGHT);
		this.layout.arrangeElements();
		int spaceBelowScrollArea = this.height - this.layout.getFooterHeight() - this.scrollArea.getRectangle().bottom();
		this.scrollArea.setMaxHeight(this.scrollArea.getHeight() + spaceBelowScrollArea);
	}

	private StringWidget sectionLabel(final String text) {
		return new StringWidget(Component.literal(text).withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW), this.font);
	}

	private StringWidget spacer() {
		return new StringWidget(SECTION_COLOR, this.font);
	}

	private CycleButton<Boolean> toggleRow(final String label, final boolean initial, final Consumer<Boolean> onChange) {
		return CycleButton.onOffBuilder(initial)
			.create(0, 0, ROW_WIDTH, 20, Component.literal(label), (button, value) -> onChange.accept(value));
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
