package com.pantheon;

import com.pantheon.network.PantheonNetworking;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PantheonMod implements ModInitializer {
	public static final String MOD_ID = "pantheon";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Re-broadcast the hotbar-owner tags this often, in case a JOIN/DISCONNECT is ever missed. */
	private static final int RESYNC_INTERVAL_TICKS = 100;

	private int tickCounter;

	@Override
	public void onInitialize() {
		LOGGER.info("Pantheon initializing - one inventory to share them all");

		PantheonNetworking.register();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> SharedInventory.reset());

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> HotbarAssignment.broadcast(server));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HotbarAssignment.broadcast(server));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			this.tickCounter++;
			if (this.tickCounter % RESYNC_INTERVAL_TICKS == 0) {
				HotbarAssignment.broadcast(server);
			}
		});
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
