package com.pantheon.forge;

import com.pantheon.PantheonMod;
import com.pantheon.client.PantheonModClient;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Forge client half: HUD layers, key mappings and client events. Only ever
 * touched when running on a client, so a dedicated server never loads it.
 */
final class PantheonForgeClient {
	private PantheonForgeClient() {
	}

	static void init(final IEventBus modBus) {
		PantheonModClient.init();

		modBus.addListener(PantheonForgeClient::registerLayers);
		modBus.addListener((RegisterKeyMappingsEvent event) -> PantheonModClient.keyMappings().forEach(event::register));

		MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post event) -> PantheonModClient.onClientTickEnd(Minecraft.getInstance()));
		MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> PantheonModClient.onDisconnect());
	}

	/** Chained so they draw in this order right above the hotbar, like the Fabric build. */
	private static void registerLayers(final AddGuiOverlayLayersEvent event) {
		ForgeLayeredDraw draw = event.getLayeredDraw();
		ResourceLocation stack = ForgeLayeredDraw.PRE_SLEEP_STACK;
		ResourceLocation owners = PantheonMod.id("hotbar_owners");
		ResourceLocation lowHealth = PantheonMod.id("low_health_warning");
		draw.addAbove(stack, owners, ForgeLayeredDraw.HOTBAR, PantheonModClient.HOTBAR_OWNERS_LAYER);
		draw.addAbove(stack, lowHealth, owners, PantheonModClient.LOW_HEALTH_LAYER);
		draw.addAbove(stack, PantheonMod.id("two_hand_hotbar"), lowHealth, PantheonModClient.TWO_HAND_HOTBAR_LAYER);
		draw.addBelow(stack, PantheonMod.id("location_marks"), ForgeLayeredDraw.CROSSHAIR, PantheonModClient.LOCATION_MARKS_LAYER);
	}
}
