package com.pantheon.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pantheon.PantheonConfig;
import com.pantheon.PantheonMod;
import com.pantheon.SlotLocks;
import com.pantheon.client.mixin.ContainerScreenHoveredSlotAccessor;
import com.pantheon.client.mixin.KeyMappingKeyAccessor;
import com.pantheon.network.ForceHotbarSlotPayload;
import com.pantheon.network.HotbarOwnersPayload;
import com.pantheon.network.LocationMarkPayload;
import com.pantheon.network.RequestSlotPayload;
import com.pantheon.network.SyncConfigPayload;
import com.pantheon.network.UpdateConfigPayload;

import com.pantheon.platform.PantheonPlatform;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

import org.lwjgl.glfw.GLFW;

/**
 * The loader-agnostic half of the client side. Each loader's entrypoint
 * calls {@link #init}, registers {@link #keyMappings()} and the HUD layers
 * below with its own event bus, routes the server's payloads to the
 * {@code on...} handlers (on the client thread) and calls
 * {@link #onClientTickEnd} / {@link #onDisconnect} from its own events.
 */
public final class PantheonModClient {
	private static final String KEY_CATEGORY = "key.category.pantheon.main";

	/** Drawn right above the vanilla hotbar, in this order. */
	public static final LayeredDraw.Layer HOTBAR_OWNERS_LAYER = new HotbarOwnerOverlay();
	public static final LayeredDraw.Layer LOW_HEALTH_LAYER = new LowHealthOverlay();
	public static final LayeredDraw.Layer TWO_HAND_HOTBAR_LAYER = new TwoHandHotbarOverlay();
	/** Drawn right below the crosshair. */
	public static final LayeredDraw.Layer LOCATION_MARKS_LAYER = new LocationMarkOverlay();

	private static volatile List<UUID> hotbarOwners = emptyOwners();
	// Recomputed only when hotbarOwners itself changes (right below), not on
	// every render call - HotbarColors#assignColors does real work (a
	// collision search per colliding teammate) that only ever needs
	// redoing when who owns what has actually changed, not every single
	// frame the HUD or an inventory screen happens to draw the frames.
	private static volatile Map<UUID, Integer> hotbarColors = Map.of();
	private static volatile PantheonConfig lastKnownConfig = new PantheonConfig();

	private static final KeyMapping OPEN_SETTINGS_KEY = new KeyMapping(
		"key.pantheon.open_settings",
		InputConstants.Type.KEYSYM,
		InputConstants.UNKNOWN.getValue(),
		KEY_CATEGORY
	);
	private static final KeyMapping REQUEST_SLOT_KEY = new KeyMapping(
		"key.pantheon.request_slot",
		InputConstants.Type.KEYSYM,
		InputConstants.UNKNOWN.getValue(),
		KEY_CATEGORY
	);
	// Middle-click already places marks (see PickBlockLocationMarkMixin);
	// this is for anyone who'd rather have a dedicated key that always does.
	private static final KeyMapping PLACE_LOCATION_MARK_KEY = new KeyMapping(
		"key.pantheon.place_location_mark",
		InputConstants.Type.KEYSYM,
		InputConstants.UNKNOWN.getValue(),
		KEY_CATEGORY
	);
	private static boolean requestSlotKeyWasPhysicallyDown;

	private PantheonModClient() {
	}

	public static void init() {
		PantheonClientConfig.load();

		// Lets shared code (Slot#mayPlace/#mayPickup, shift-click, the raw
		// Inventory scans) predict locks on this side exactly like the server
		// enforces them - see SlotLocks.
		SlotLocks.clientPredicate = PantheonModClient::isLockedForLocalPlayer;
	}

	/** For the loader to register with its key-mapping event. */
	public static List<KeyMapping> keyMappings() {
		return List.of(OPEN_SETTINGS_KEY, REQUEST_SLOT_KEY, PLACE_LOCATION_MARK_KEY);
	}

	/**
	 * Ownership and settings belong to the server we were on - a vanilla
	 * server joined next must not inherit its locks.
	 */
	public static void onDisconnect() {
		hotbarOwners = emptyOwners();
		hotbarColors = Map.of();
		lastKnownConfig = new PantheonConfig();
		LocationMarkClient.clear();
	}

	public static void onHotbarOwners(final HotbarOwnersPayload payload) {
		hotbarOwners = payload.owners();
		hotbarColors = HotbarColors.assignColors(hotbarOwners);
		Player player = Minecraft.getInstance().player;
		int localSelected = player != null ? player.getInventory().selected : -1;
		PantheonMod.LOGGER.info("[PantheonModClient] received owners={} (our own selected slot locally = {})", payload.owners(), localSelected);
	}

	public static void onSyncConfig(final SyncConfigPayload payload) {
		lastKnownConfig = payload.toConfig();
	}

	/**
	 * The server just forced our selected slot to move (a join/respawn
	 * conflict with a teammate) - selection is otherwise entirely
	 * client-driven, so without this our own view of it would never
	 * learn about a server-side move, and effectiveOwner()'s local
	 * prediction would keep showing our frame on the old slot too.
	 */
	public static void onForceHotbarSlot(final ForceHotbarSlotPayload payload) {
		PantheonMod.LOGGER.info("[PantheonModClient] received ForceHotbarSlotPayload: forced to slot {}", payload.slot());
		Player player = Minecraft.getInstance().player;
		if (player != null) {
			player.getInventory().selected = payload.slot();
		}
	}

	public static void onLocationMark(final LocationMarkPayload payload) {
		LocationMarkClient.onMarkPayload(payload);
	}

	public static void onClientTickEnd(final Minecraft client) {
		while (OPEN_SETTINGS_KEY.consumeClick()) {
			if (client.screen == null) {
				client.setScreen(new PantheonOptionsScreen(null, lastKnownConfig));
			}
		}
		while (REQUEST_SLOT_KEY.consumeClick()) {
			requestHoveredSlot(client);
		}
		while (PLACE_LOCATION_MARK_KEY.consumeClick()) {
			if (client.screen == null) {
				LocationMarkClient.placeMark(client);
			}
		}
		// Backstop for consumeClick(): some inventory screens can eat the raw
		// key event before it reaches KeyMapping's own click-tracking, which
		// would otherwise make the ping key silently do nothing while a
		// container screen has focus - exactly the situation it's meant for.
		// Poll the physical key state directly instead, with our own
		// rising-edge detection so a held key doesn't fire every tick.
		boolean physicallyDown = isRequestSlotKeyPhysicallyDown(client);
		if (physicallyDown && !requestSlotKeyWasPhysicallyDown) {
			requestHoveredSlot(client);
		}
		requestSlotKeyWasPhysicallyDown = physicallyDown;
	}

	private static boolean isRequestSlotKeyPhysicallyDown(final Minecraft client) {
		InputConstants.Key key = ((KeyMappingKeyAccessor) REQUEST_SLOT_KEY).pantheon$getKey();
		if (key.equals(InputConstants.UNKNOWN)) {
			return false;
		}
		long window = client.getWindow().getWindow();
		if (key.getType() == InputConstants.Type.MOUSE) {
			// InputConstants.MOUSE_BUTTON_* are GLFW's own 0-based button numbers.
			return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
		}
		if (key.getType() == InputConstants.Type.KEYSYM) {
			return InputConstants.isKeyDown(window, key.getValue());
		}
		return false;
	}

	/**
	 * If the local player is currently hovering a hotbar slot (in their own
	 * inventory row of whatever container screen is open) that's locked to
	 * another online player, asks the server to nudge that player about it.
	 * Otherwise explains locally why there was nothing to ping, rather than
	 * silently doing nothing - "occupied by an item" and "locked to someone
	 * else" are easy to conflate, and only the latter is pingable.
	 */
	private static void requestHoveredSlot(final Minecraft client) {
		if (client.player == null) {
			return;
		}
		if (!lastKnownConfig.enableHotbarOwnership) {
			client.player.displayClientMessage(Component.literal("Hotbar slot ownership is off - there's nothing to request."), true);
			return;
		}
		if (!(client.screen instanceof AbstractContainerScreen<?> containerScreen)) {
			client.player.displayClientMessage(Component.literal("Hover a locked hotbar slot in an inventory screen first."), true);
			return;
		}
		Slot hovered = ((ContainerScreenHoveredSlotAccessor) containerScreen).pantheon$getHoveredSlot();
		int index = hovered == null || hovered.container != client.player.getInventory() ? -1 : hovered.getContainerSlot();
		if (index < 0 || !isLockedToSomeoneElse(index)) {
			client.player.displayClientMessage(Component.literal("That hotbar slot isn't locked to anyone else."), true);
			return;
		}
		PantheonPlatform.INSTANCE.sendToServer(new RequestSlotPayload(index));
	}

	public static List<UUID> getHotbarOwners() {
		return hotbarOwners;
	}

	/** The color each currently-online teammate's hotbar-lock frame should be drawn in - see {@link HotbarColors#assignColors}. Recomputed once per broadcast, not per caller. */
	public static Map<UUID, Integer> getHotbarColors() {
		return hotbarColors;
	}

	public static PantheonConfig getLastKnownConfig() {
		return lastKnownConfig;
	}

	public static void sendConfigUpdate(final PantheonConfig config) {
		PantheonPlatform.INSTANCE.sendToServer(UpdateConfigPayload.fromConfig(config));
	}

	/**
	 * Whether {@code slot} is currently locked to some other online player
	 * (not us, not unowned) - or, in two-hand mode, simply not the center
	 * slot ({@link HotbarOwnersPayload#TWO_HAND_ACTIVE_SLOT}), since every
	 * slot but the one selectable main-hand slot is effectively "locked" to
	 * nobody being allowed to select it at all. Reusing this single check
	 * lets the scroll-skip and number-key mixins enforce two-hand mode for
	 * free, with no changes of their own.
	 */
	public static boolean isLockedToSomeoneElse(final int slot) {
		if (slot < 0 || slot >= hotbarOwners.size()) {
			return false;
		}
		if (lastKnownConfig.twoHandSlotMode) {
			return slot != HotbarOwnersPayload.TWO_HAND_ACTIVE_SLOT;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return false;
		}
		UUID owner = hotbarOwners.get(slot);
		return !owner.equals(HotbarOwnersPayload.NO_OWNER) && !owner.equals(minecraft.player.getUUID());
	}

	/**
	 * {@link #isLockedToSomeoneElse} for {@link SlotLocks}: only the local
	 * player's own inventory is ever locked on this side, and never the slot
	 * we have selected ourselves - the server only ever locks a slot to us
	 * when someone else holds it <em>alone</em>, so a stale broadcast still
	 * crediting our own slot to someone else (the round trip right after a
	 * move) mustn't lock us out of it.
	 */
	private static boolean isLockedForLocalPlayer(final Player player, final int slot) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || player != minecraft.player) {
			return false;
		}
		if (!lastKnownConfig.twoHandSlotMode && slot == player.getInventory().selected) {
			return false;
		}
		return isLockedToSomeoneElse(slot);
	}

	/**
	 * The owner to render for {@code slot} right now. Selecting your own
	 * hotbar slot is instant/client-predicted (the vanilla selection outline
	 * moves the moment you scroll or press a number key), but the server
	 * broadcast confirming *we* now own that slot takes a network round trip
	 * - without this, our lock frame would visibly lag a tick or two behind
	 * that outline. Since we already know locally which slot we've selected,
	 * predict our own frame immediately and only defer to the broadcast for
	 * everyone else's slots.
	 *
	 * <p>The broadcast list can also still say <em>we</em> own a slot we've
	 * already scrolled away from, for that same one-round-trip window - left
	 * alone, that shows our frame on both the old and new slot at once, and
	 * scrolling quickly through several slots in a row turns that into a
	 * visible trail of stale frames. Since we know with certainty (no
	 * network latency involved) which slot is and isn't ours right now,
	 * treat any *other* slot the broadcast still credits to us as unowned
	 * instead of trusting that stale entry.
	 */
	public static UUID effectiveOwner(final int slot) {
		Minecraft minecraft = Minecraft.getInstance();
		UUID broadcastOwner = (slot >= 0 && slot < hotbarOwners.size()) ? hotbarOwners.get(slot) : HotbarOwnersPayload.NO_OWNER;

		if (minecraft.player == null || !lastKnownConfig.enableHotbarOwnership) {
			return broadcastOwner;
		}

		UUID self = minecraft.player.getUUID();
		if (slot == minecraft.player.getInventory().selected) {
			return self;
		}
		return broadcastOwner.equals(self) ? HotbarOwnersPayload.NO_OWNER : broadcastOwner;
	}

	private static List<UUID> emptyOwners() {
		List<UUID> owners = new ArrayList<>(HotbarOwnersPayload.SLOT_COUNT);
		for (int i = 0; i < HotbarOwnersPayload.SLOT_COUNT; i++) {
			owners.add(HotbarOwnersPayload.NO_OWNER);
		}
		return owners;
	}
}
