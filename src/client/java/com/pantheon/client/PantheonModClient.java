package com.pantheon.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.pantheon.PantheonConfig;
import com.pantheon.PantheonMod;
import com.pantheon.client.mixin.ContainerScreenHoveredSlotAccessor;
import com.pantheon.client.mixin.KeyMappingKeyAccessor;
import com.pantheon.network.ForceHotbarSlotPayload;
import com.pantheon.network.HotbarOwnersPayload;
import com.pantheon.network.RequestSlotPayload;
import com.pantheon.network.SyncConfigPayload;
import com.pantheon.network.UpdateConfigPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

public class PantheonModClient implements ClientModInitializer {
	private static volatile List<UUID> hotbarOwners = emptyOwners();
	private static volatile PantheonConfig lastKnownConfig = new PantheonConfig();

	private static KeyMapping openSettingsKey;
	private static KeyMapping requestSlotKey;
	private static boolean requestSlotKeyWasPhysicallyDown;

	@Override
	public void onInitializeClient() {
		PantheonClientConfig.load();

		ClientPlayNetworking.registerGlobalReceiver(HotbarOwnersPayload.TYPE, (payload, context) -> {
			hotbarOwners = payload.owners();
			int localSelected = context.player() != null ? context.player().getInventory().getSelectedSlot() : -1;
			PantheonMod.LOGGER.info("[PantheonModClient] received owners={} (our own selected slot locally = {})", payload.owners(), localSelected);
		});
		ClientPlayNetworking.registerGlobalReceiver(SyncConfigPayload.TYPE, (payload, context) -> lastKnownConfig = payload.toConfig());
		// The server just forced our selected slot to move (a join/respawn
		// conflict with a teammate) - selection is otherwise entirely
		// client-driven, so without this our own view of it would never
		// learn about a server-side move, and effectiveOwner()'s local
		// prediction would keep showing our frame on the old slot too.
		ClientPlayNetworking.registerGlobalReceiver(ForceHotbarSlotPayload.TYPE, (payload, context) -> {
			PantheonMod.LOGGER.info("[PantheonModClient] received ForceHotbarSlotPayload: forced to slot {}", payload.slot());
			if (context.player() != null) {
				context.player().getInventory().setSelectedSlot(payload.slot());
			}
		});

		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, PantheonMod.id("hotbar_owners"), new HotbarOwnerOverlay());
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, PantheonMod.id("low_health_warning"), new LowHealthOverlay());
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, PantheonMod.id("two_hand_hotbar"), new TwoHandHotbarOverlay());

		KeyMapping.Category category = KeyMapping.Category.register(PantheonMod.id("main"));
		openSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.pantheon.open_settings",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			category
		));
		requestSlotKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.pantheon.request_slot",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			category
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openSettingsKey.consumeClick()) {
				if (client.gui.screen() == null) {
					client.setScreenAndShow(new PantheonOptionsScreen(null, lastKnownConfig));
				}
			}
			while (requestSlotKey.consumeClick()) {
				requestHoveredSlot(client);
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
		});
	}

	private static boolean isRequestSlotKeyPhysicallyDown(final Minecraft client) {
		InputConstants.Key key = ((KeyMappingKeyAccessor) requestSlotKey).pantheon$getKey();
		if (key.equals(InputConstants.UNKNOWN)) {
			return false;
		}
		if (key.getType() == InputConstants.Type.MOUSE) {
			long handle = client.getWindow().handle();
			return org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle, key.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
		}
		if (key.getType() == InputConstants.Type.KEYSYM) {
			return InputConstants.isKeyDown(client.getWindow(), key.getValue());
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
			client.player.sendOverlayMessage(Component.literal("Hotbar slot ownership is off - there's nothing to request."));
			return;
		}
		if (!(client.gui.screen() instanceof AbstractContainerScreen<?> containerScreen)) {
			client.player.sendOverlayMessage(Component.literal("Hover a locked hotbar slot in an inventory screen first."));
			return;
		}
		Slot hovered = ((ContainerScreenHoveredSlotAccessor) containerScreen).pantheon$getHoveredSlot();
		int index = hovered == null || hovered.container != client.player.getInventory() ? -1 : hovered.getContainerSlot();
		if (index < 0 || !isLockedToSomeoneElse(index)) {
			client.player.sendOverlayMessage(Component.literal("That hotbar slot isn't locked to anyone else."));
			return;
		}
		ClientPlayNetworking.send(new RequestSlotPayload(index));
	}

	public static List<UUID> getHotbarOwners() {
		return hotbarOwners;
	}

	public static PantheonConfig getLastKnownConfig() {
		return lastKnownConfig;
	}

	public static void sendConfigUpdate(final PantheonConfig config) {
		ClientPlayNetworking.send(UpdateConfigPayload.fromConfig(config));
	}

	/**
	 * Whether {@code slot} is currently locked to some other online player
	 * (not us, not unowned) - or, in two-hand mode, simply not slot 0, since
	 * every slot but the one selectable main-hand slot is effectively "locked"
	 * to nobody being allowed to select it at all. Reusing this single check
	 * lets the scroll-skip and number-key mixins enforce two-hand mode for
	 * free, with no changes of their own.
	 */
	public static boolean isLockedToSomeoneElse(final int slot) {
		if (slot < 0 || slot >= hotbarOwners.size()) {
			return false;
		}
		if (lastKnownConfig.twoHandSlotMode) {
			return slot != 0;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return false;
		}
		UUID owner = hotbarOwners.get(slot);
		return !owner.equals(HotbarOwnersPayload.NO_OWNER) && !owner.equals(minecraft.player.getUUID());
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
		if (slot == minecraft.player.getInventory().getSelectedSlot()) {
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
