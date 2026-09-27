package com.pantheon.client;

import java.util.List;
import java.util.function.Consumer;

import com.pantheon.PantheonConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
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
	/** One widget per row: 20px tall buttons plus the gap between them. */
	private static final int ROW_HEIGHT = 24;
	private static final Component SECTION_COLOR = Component.empty();

	private final Screen parent;
	private final PantheonConfig working;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private RowList list;

	/**
	 * What {@link PantheonConfig#enableHotbarOwnership} should go back to if
	 * "Two-hand mode" is switched off again within this same screen session
	 * (before Save), mirroring {@link PantheonConfig#hotbarOwnershipBeforeTwoHand}
	 * for changes that never leave the client. Seeded from that same field so
	 * a screen opened while two-hand mode is already on restores correctly on
	 * its very first toggle-off, then kept live afterward: every toggle-on
	 * recaptures whatever the ownership toggle is showing at that moment,
	 * since the player may have flipped it manually earlier in this same
	 * session, which {@code hotbarOwnershipBeforeTwoHand} alone wouldn't know
	 * about.
	 */
	private boolean ownershipBeforeTwoHand;

	public PantheonOptionsScreen(final Screen parent, final PantheonConfig initial) {
		super(Component.literal("Pantheon Settings"));
		this.parent = parent;
		this.working = initial.copy();
		this.ownershipBeforeTwoHand = initial.hotbarOwnershipBeforeTwoHand;
	}

	@Override
	protected void init() {
		this.layout.addTitleHeader(this.title, this.font);

		// 1.21.1 has no ScrollableLayout, so the rows live in a selection
		// list instead - same look and scrolling as vanilla's own options.
		this.list = this.layout.addToContents(new RowList(this.minecraft, this.width, this.layout));
		RowList content = this.list;

		content.add(sectionLabel("What's shared"));
		content.add(toggleRow("Armor", this.working.syncArmor, v -> this.working.syncArmor = v));
		content.add(toggleRow("Offhand", this.working.syncOffhand, v -> this.working.syncOffhand = v));

		content.add(spacer());
		content.add(sectionLabel("Hotbar ownership"));
		CycleButton<Boolean> ownershipToggle = toggleRow("Item bar slot ownership (9 players maximum)",
			this.working.enableHotbarOwnership, v -> this.working.enableHotbarOwnership = v);
		ownershipToggle.active = !this.working.twoHandSlotMode;
		content.add(ownershipToggle);
		content.add(toggleRow("Two-hand mode",
			this.working.twoHandSlotMode, v -> {
				this.working.twoHandSlotMode = v;
				if (v) {
					this.ownershipBeforeTwoHand = this.working.enableHotbarOwnership;
					this.working.enableHotbarOwnership = false;
					ownershipToggle.setValue(false);
				} else {
					this.working.enableHotbarOwnership = this.ownershipBeforeTwoHand;
					ownershipToggle.setValue(this.ownershipBeforeTwoHand);
				}
				ownershipToggle.active = !v;
			}));

		content.add(spacer());
		content.add(sectionLabel("Shared vitals"));
		content.add(toggleRow("Share health", this.working.syncHealth, v -> this.working.syncHealth = v));
		content.add(toggleRow("Share hunger", this.working.syncHunger, v -> this.working.syncHunger = v));
		content.add(toggleRow("Share XP", this.working.syncExperience, v -> this.working.syncExperience = v));
		content.add(toggleRow("Share potion effects", this.working.syncEffects, v -> this.working.syncEffects = v));

		content.add(spacer());
		content.add(sectionLabel("Teams"));
		content.add(toggleRow("Split into teams", this.working.teamsEnabled, v -> this.working.teamsEnabled = v));

		content.add(spacer());
		content.add(sectionLabel("Location marks"));
		content.add(toggleRow("Location marks", this.working.locationMarks, v -> this.working.locationMarks = v));
		content.add(new LifetimeSlider());

		content.add(spacer());
		content.add(sectionLabel("Jokes"));
		content.add(toggleRow("Crude jokes",
			this.working.crudeHumor, v -> this.working.crudeHumor = v));

		content.add(spacer());
		content.add(sectionLabel("This device only"));
		content.add(toggleRow("Low-health screen warning",
			PantheonClientConfig.get().lowHealthWarningEnabled, PantheonClientConfig::setLowHealthWarningEnabled));

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
		this.layout.arrangeElements();
		if (this.list != null) {
			this.list.updateSize(this.width, this.layout);
		}
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

	/** "Location mark life time", in {@link PantheonConfig#LOCATION_MARK_LIFETIME_STEP_SECONDS}-second steps. */
	private final class LifetimeSlider extends AbstractSliderButton {
		private static final int MIN = PantheonConfig.LOCATION_MARK_LIFETIME_MIN_SECONDS;
		private static final int MAX = PantheonConfig.LOCATION_MARK_LIFETIME_MAX_SECONDS;

		LifetimeSlider() {
			super(0, 0, ROW_WIDTH, 20, Component.empty(),
				(double) (PantheonOptionsScreen.this.working.locationMarkLifetimeSeconds - MIN) / (MAX - MIN));
			this.updateMessage();
		}

		private int seconds() {
			return PantheonConfig.clampLocationMarkLifetime((int) Math.round(MIN + this.value * (MAX - MIN)));
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.literal("Location mark life time: " + this.seconds() + "s"));
		}

		@Override
		protected void applyValue() {
			PantheonOptionsScreen.this.working.locationMarkLifetimeSeconds = this.seconds();
		}
	}

	/** A scrolling column of widgets, one centered per row. */
	private static final class RowList extends ContainerObjectSelectionList<RowList.Row> {
		RowList(final Minecraft minecraft, final int width, final HeaderAndFooterLayout layout) {
			super(minecraft, width, layout.getContentHeight(), layout.getHeaderHeight(), ROW_HEIGHT);
		}

		void add(final AbstractWidget widget) {
			this.addEntry(new Row(widget));
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH + 10;
		}

		static final class Row extends ContainerObjectSelectionList.Entry<Row> {
			private final AbstractWidget widget;

			Row(final AbstractWidget widget) {
				this.widget = widget;
			}

			@Override
			public void render(final GuiGraphics graphics, final int index, final int top, final int left, final int width,
				final int height, final int mouseX, final int mouseY, final boolean hovering, final float partialTick) {
				this.widget.setPosition(left + (width - this.widget.getWidth()) / 2, top + (height - this.widget.getHeight()) / 2);
				this.widget.render(graphics, mouseX, mouseY, partialTick);
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return List.of(this.widget);
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of(this.widget);
			}
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
