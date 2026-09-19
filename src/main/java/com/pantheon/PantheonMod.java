package com.pantheon;

import com.pantheon.network.PantheonNetworking;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
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
			TeamManager.load(server);
			// A death that drops/clears the shared inventory would empty it for every
			// player at once, not just the one who died - keepInventory is required.
			server.getGameRules().set(GameRules.KEEP_INVENTORY, Boolean.TRUE, server);
		});

		// Shared items live in the world's saved data rather than any one player's
		// file - make sure they go out with every world save (autosave, pause, stop).
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> TeamManager.markDirty(server));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			PantheonNetworking.sendConfigTo(handler.player);
			// The joining player's own hotbar selection is loaded from their personal
			// save data and may already be "owned" by someone else who's mid-session -
			// move them to a free slot instead of contending for an occupied one.
			HotbarOwnership.resolveSlotConflict(server, handler.player);
			// Their Inventory already points at the shared list (see InventorySharingMixin),
			// but a fresh menu's diff-against-nothing might take a tick to catch up -
			// force it immediately so they see the current shared contents right away.
			handler.player.inventoryMenu.broadcastFullState();
			HotbarOwnership.broadcast(server);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HotbarOwnership.broadcast(server));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			this.tickCounter++;
			enforceKeepInventory(server);
			HotbarOwnership.enforceTwoHandMode(server);
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

	/**
	 * {@code keepInventory} is forced on at startup, but nothing stops a later
	 * {@code /gamerule keepInventory false} (by an op who forgot why it was on,
	 * or just doesn't know) from turning it back off mid-session. With it off,
	 * a shared-health-pool death would let vanilla's own per-entity drop run
	 * for every dying teammate against the *same* shared item list - dropping
	 * every item once per teammate instead of once for the whole event - on
	 * top of {@link SharedStats#tick}'s own explicit one-time drop, duplicating
	 * the team's entire inventory. Reasserting it every tick, before any death
	 * handling this tick can happen, closes that off without having to hook
	 * the gamerule command itself.
	 */
	private static void enforceKeepInventory(final MinecraftServer server) {
		if (!Boolean.TRUE.equals(server.getGameRules().get(GameRules.KEEP_INVENTORY))) {
			server.getGameRules().set(GameRules.KEEP_INVENTORY, Boolean.TRUE, server);
			LOGGER.warn("[PantheonMod] keepInventory was turned off - Pantheon requires it, forcing it back on.");
		}
	}
}
