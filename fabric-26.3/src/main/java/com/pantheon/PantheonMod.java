package com.pantheon;

import java.util.List;
import java.util.Map;

import com.pantheon.network.PantheonNetworking;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
			DeathDropSaves.reset();
			PantheonNetworking.clearHeldOverlays();
			LocationMarks.clear();
			TeamManager.load(server);
			// A death that drops/clears the shared inventory would empty it for every
			// player at once, not just the one who died - keepInventory is required.
			server.getGameRules().set(GameRules.KEEP_INVENTORY, Boolean.TRUE, server);
		});

		// Reasserted at the *start* of every tick too, not just the end (see
		// enforceKeepInventory) - narrows, though doesn't fully close, the
		// window between an op flipping the gamerule mid-tick and this mod
		// noticing, since entity ticking (where an ordinary death could run
		// against it) happens between the two.
		ServerTickEvents.START_SERVER_TICK.register(PantheonMod::enforceKeepInventory);

		// Shared items live in the world's saved data rather than any one player's
		// file - make sure they go out with every world save (autosave, pause, stop).
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> TeamManager.markDirty(server));

		// A death moves items out of the shared inventory (saved data) onto the
		// ground (chunk data) - save both together so a crash can't bring back
		// one side without the other. See DeathDropSaves.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity instanceof ServerPlayer player) {
				DeathDropSaves.onPlayerDeath(((ServerLevel) player.level()).getServer());
			}
		});

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
			LocationMarks.sendActiveTo(server, handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HotbarOwnership.broadcast(server));

		// alive=false is an actual death respawn (a fresh entity with no
		// active effects); alive=true is a dimension-change respawn (e.g.
		// leaving the End), which does carry effects over and needs none of
		// this.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				SharedStats.onRespawn(newPlayer);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			this.tickCounter++;
			enforceKeepInventory(server);
			HotbarOwnership.enforceTwoHandMode(server);
			// Computed once and shared: HotbarOwnership.tick and SharedStats.tick
			// both need "who's online, grouped by team" every single tick, and
			// grouping is real work (a fresh map/lists, one teamOf() lookup per
			// online player) - doing it twice, 20 times a second, for identical
			// results is pure waste.
			Map<Team, List<ServerPlayer>> onlineByTeam = TeamManager.groupOnlineByTeam(server);
			HotbarOwnership.tick(onlineByTeam);
			SharedStats.tick(onlineByTeam);
			if (this.tickCounter % RESYNC_INTERVAL_TICKS == 0) {
				HotbarOwnership.broadcast(onlineByTeam);
			}
			PantheonNetworking.tickHeldOverlays(server);
			DeathDropSaves.tick(server);
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
	 * the team's entire inventory.
	 *
	 * <p>Called at both the start and end of every tick (see the two
	 * {@code ServerTickEvents} registrations in {@link #onInitialize}), which
	 * reliably closes that specific duplication case: nothing else runs
	 * between this and {@link SharedStats#tick} within the same
	 * {@code END_SERVER_TICK} handler, so a shared-pool death can never see
	 * the gamerule off. It narrows, but can't fully close, the much rarer
	 * case of an ordinary (non-pool) death from normal combat landing in the
	 * exact same tick as the gamerule being flipped, before either of these
	 * two calls has run again - closing that completely would mean
	 * intercepting the {@code /gamerule} command itself, which felt like more
	 * surface area than this edge case warrants.
	 */
	private static void enforceKeepInventory(final MinecraftServer server) {
		if (!Boolean.TRUE.equals(server.getGameRules().get(GameRules.KEEP_INVENTORY))) {
			server.getGameRules().set(GameRules.KEEP_INVENTORY, Boolean.TRUE, server);
			LOGGER.warn("[PantheonMod] keepInventory was turned off - Pantheon requires it, forcing it back on.");
		}
	}
}
