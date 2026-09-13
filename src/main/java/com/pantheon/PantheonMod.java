package com.pantheon;

import com.pantheon.network.PantheonNetworking;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRules;

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

		PantheonConfig.load();
		PantheonNetworking.register();
		PantheonCommands.register();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			SharedInventory.reset();
			// A death that drops/clears the shared inventory would empty it for every
			// player at once, not just the one who died - keepInventory is required.
			server.getGameRules().set(GameRules.KEEP_INVENTORY, Boolean.TRUE, server);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			PantheonNetworking.sendConfigTo(handler.player);
			// The joining player's own hotbar selection is loaded from their personal
			// save data and may already be "owned" by someone else who's mid-session -
			// move them to a free slot instead of contending for an occupied one.
			HotbarOwnership.resolveJoinConflict(server, handler.player);
			// Their Inventory already points at the shared list (see InventorySharingMixin),
			// but a fresh menu's diff-against-nothing might take a tick to catch up -
			// force it immediately so they see the current shared contents right away.
			handler.player.inventoryMenu.broadcastFullState();
			HotbarOwnership.broadcast(server);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HotbarOwnership.broadcast(server));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			this.tickCounter++;
			HotbarOwnership.tick(server);
			SharedStats.tick(server);
			if (this.tickCounter % RESYNC_INTERVAL_TICKS == 0) {
				HotbarOwnership.broadcast(server);
			}
		});
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
