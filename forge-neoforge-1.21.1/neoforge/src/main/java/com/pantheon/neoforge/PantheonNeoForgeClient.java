package com.pantheon.neoforge;

import com.pantheon.PantheonMod;
import com.pantheon.client.PantheonModClient;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge client entrypoint: HUD layers, key mappings and client events. */
@Mod(value = PantheonMod.MOD_ID, dist = Dist.CLIENT)
public final class PantheonNeoForgeClient {
	public PantheonNeoForgeClient(final IEventBus modBus) {
		PantheonModClient.init();

		modBus.addListener(PantheonNeoForgeClient::registerLayers);
		modBus.addListener((RegisterKeyMappingsEvent event) -> PantheonModClient.keyMappings().forEach(event::register));

		NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> PantheonModClient.onClientTickEnd(Minecraft.getInstance()));
		NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> PantheonModClient.onDisconnect());
	}

	/** Chained so they draw in this order right above the hotbar, like the Fabric build. */
	private static void registerLayers(final RegisterGuiLayersEvent event) {
		ResourceLocation owners = PantheonMod.id("hotbar_owners");
		ResourceLocation lowHealth = PantheonMod.id("low_health_warning");
		event.registerAbove(VanillaGuiLayers.HOTBAR, owners, PantheonModClient.HOTBAR_OWNERS_LAYER);
		event.registerAbove(owners, lowHealth, PantheonModClient.LOW_HEALTH_LAYER);
		event.registerAbove(lowHealth, PantheonMod.id("two_hand_hotbar"), PantheonModClient.TWO_HAND_HOTBAR_LAYER);
		event.registerBelow(VanillaGuiLayers.CROSSHAIR, PantheonMod.id("location_marks"), PantheonModClient.LOCATION_MARKS_LAYER);
	}
}
