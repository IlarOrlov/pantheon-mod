package com.pantheon.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.pantheon.PantheonConfig;
import com.pantheon.PantheonMod;
import com.pantheon.network.HotbarOwnersPayload;
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

public class PantheonModClient implements ClientModInitializer {
	private static volatile List<UUID> hotbarOwners = emptyOwners();
	private static volatile List<Integer> hotbarOwnerColors = emptyColors();
	private static volatile PantheonConfig lastKnownConfig = new PantheonConfig();

	private static KeyMapping openSettingsKey;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(HotbarOwnersPayload.TYPE, (payload, context) -> {
			hotbarOwners = payload.owners();
			hotbarOwnerColors = payload.colors();
		});
		ClientPlayNetworking.registerGlobalReceiver(SyncConfigPayload.TYPE, (payload, context) -> lastKnownConfig = payload.toConfig());

		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, PantheonMod.id("hotbar_owners"), new HotbarOwnerOverlay());

		KeyMapping.Category category = KeyMapping.Category.register(PantheonMod.id("main"));
		openSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.pantheon.open_settings",
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
		});
	}

	public static List<UUID> getHotbarOwners() {
		return hotbarOwners;
	}

	public static List<Integer> getHotbarOwnerColors() {
		return hotbarOwnerColors;
	}

	public static PantheonConfig getLastKnownConfig() {
		return lastKnownConfig;
	}

	public static void sendConfigUpdate(final PantheonConfig config) {
		ClientPlayNetworking.send(UpdateConfigPayload.fromConfig(config));
	}

	private static List<UUID> emptyOwners() {
		List<UUID> owners = new ArrayList<>(HotbarOwnersPayload.SLOT_COUNT);
		for (int i = 0; i < HotbarOwnersPayload.SLOT_COUNT; i++) {
			owners.add(HotbarOwnersPayload.NO_OWNER);
		}
		return owners;
	}

	private static List<Integer> emptyColors() {
		List<Integer> colors = new ArrayList<>(HotbarOwnersPayload.SLOT_COUNT);
		for (int i = 0; i < HotbarOwnersPayload.SLOT_COUNT; i++) {
			colors.add(-1);
		}
		return colors;
	}
}
