package com.pantheon.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.pantheon.PantheonConfig;
import com.pantheon.PantheonMod;
import com.pantheon.client.mixin.ContainerScreenHoveredSlotAccessor;
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
import net.minecraft.world.inventory.Slot;

public class PantheonModClient implements ClientModInitializer {
	private static volatile List<UUID> hotbarOwners = emptyOwners();
	private static volatile PantheonConfig lastKnownConfig = new PantheonConfig();

	private static KeyMapping openSettingsKey;
	private static KeyMapping requestSlotKey;

	@Override
	public void onInitializeClient() {
		PantheonClientConfig.load();

		ClientPlayNetworking.registerGlobalReceiver(HotbarOwnersPayload.TYPE, (payload, context) -> hotbarOwners = payload.owners());
		ClientPlayNetworking.registerGlobalReceiver(SyncConfigPayload.TYPE, (payload, context) -> lastKnownConfig = payload.toConfig());

		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, PantheonMod.id("hotbar_owners"), new HotbarOwnerOverlay());
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, PantheonMod.id("low_health_warning"), new LowHealthOverlay());

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
		});
	}

	/**
	 * If the local player is currently hovering a hotbar slot (in their own
	 * inventory row of whatever container screen is open) that's locked to
	 * another online player, asks the server to nudge that player about it.
	 */
	private static void requestHoveredSlot(final Minecraft client) {
		if (!(client.gui.screen() instanceof AbstractContainerScreen<?> containerScreen) || client.player == null) {
			return;
		}
		Slot hovered = ((ContainerScreenHoveredSlotAccessor) containerScreen).pantheon$getHoveredSlot();
		if (hovered == null || hovered.container != client.player.getInventory()) {
			return;
		}
		int index = hovered.getContainerSlot();
		if (isLockedToSomeoneElse(index)) {
			ClientPlayNetworking.send(new RequestSlotPayload(index));
		}
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

	/** Whether {@code slot} is currently locked to some other online player (not us, not unowned). */
	public static boolean isLockedToSomeoneElse(final int slot) {
		if (slot < 0 || slot >= hotbarOwners.size()) {
			return false;
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
	 */
	public static UUID effectiveOwner(final int slot) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null
			&& lastKnownConfig.enableHotbarOwnership
			&& slot == minecraft.player.getInventory().getSelectedSlot()) {
			return minecraft.player.getUUID();
		}
		return (slot >= 0 && slot < hotbarOwners.size()) ? hotbarOwners.get(slot) : HotbarOwnersPayload.NO_OWNER;
	}

	private static List<UUID> emptyOwners() {
		List<UUID> owners = new ArrayList<>(HotbarOwnersPayload.SLOT_COUNT);
		for (int i = 0; i < HotbarOwnersPayload.SLOT_COUNT; i++) {
			owners.add(HotbarOwnersPayload.NO_OWNER);
		}
		return owners;
	}
}
