package com.pantheon;

import java.util.List;
import java.util.Map;

import com.pantheon.network.PantheonNetworking;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-independent lifecycle of the mod. Each loader module forwards its
 * own events to these hooks; the few that have no loader event in common
 * (a player's death, a respawn, a world save) are driven by mixins instead.
 */
public final class PantheonMod {
	public static final String MOD_ID = "pantheon";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Re-broadcast the hotbar-owner tags this often, in case a JOIN/DISCONNECT is ever missed. */
	private static final int RESYNC_INTERVAL_TICKS = 100;

	private static int tickCounter;

	private PantheonMod() {
	}

	public static void init() {
		LOGGER.info("Pantheon initializing - one inventory to share them all");

		PantheonConfig.load();
	}

	/** Once the worlds are loaded, before any player can join. */
	public static void onServerStarting(final MinecraftServer server) {
		DeathDropSaves.reset();
		PantheonNetworking.clearHeldOverlays();
		LocationMarks.clear();
		TeamManager.load(server);
		// A death that drops/clears the shared inventory would empty it for every
		// player at once, not just the one who died - keepInventory is required.
		server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
	}

	/**
	 * Reasserted at the *start* of every tick too, not just the end (see
	 * enforceKeepInventory) - narrows, though doesn't fully close, the
	 * window between an op flipping the gamerule mid-tick and this mod
	 * noticing, since entity ticking (where an ordinary death could run
	 * against it) happens between the two.
	 */
	public static void onServerTickStart(final MinecraftServer server) {
		enforceKeepInventory(server);
	}

	public static void onServerTickEnd(final MinecraftServer server) {
		tickCounter++;
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
		if (tickCounter % RESYNC_INTERVAL_TICKS == 0) {
			HotbarOwnership.broadcast(onlineByTeam);
		}
		PantheonNetworking.tickHeldOverlays(server);
		DeathDropSaves.tick(server);
	}

	/**
	 * Shared items live in the world's saved data rather than any one player's
	 * file - make sure they go out with every world save (autosave, pause, stop).
	 * Called by {@link com.pantheon.mixin.WorldSaveMixin}.
	 */
	public static void onBeforeSave(final MinecraftServer server) {
		TeamManager.markDirty(server);
	}

	/**
	 * A death moves items out of the shared inventory (saved data) onto the
	 * ground (chunk data) - save both together so a crash can't bring back
	 * one side without the other. See DeathDropSaves. Called by
	 * {@link com.pantheon.mixin.PlayerDeathMixin}.
	 */
	public static void onPlayerDeath(final ServerPlayer player) {
		DeathDropSaves.onPlayerDeath(player.server);
	}

	public static void onPlayerJoin(final MinecraftServer server, final ServerPlayer player) {
		PantheonNetworking.sendConfigTo(player);
		// The joining player's own hotbar selection is loaded from their personal
		// save data and may already be "owned" by someone else who's mid-session -
		// move them to a free slot instead of contending for an occupied one.
		HotbarOwnership.resolveSlotConflict(server, player);
		// Their Inventory already points at the shared list (see InventorySharingMixin),
		// but a fresh menu's diff-against-nothing might take a tick to catch up -
		// force it immediately so they see the current shared contents right away.
		player.inventoryMenu.broadcastFullState();
		HotbarOwnership.broadcast(server);
		LocationMarks.sendActiveTo(server, player);
	}

	public static void onPlayerLeave(final MinecraftServer server) {
		HotbarOwnership.broadcast(server);
	}

	/**
	 * {@code keepAllPlayerData=false} is an actual death respawn (a fresh
	 * entity with no active effects); {@code true} is a dimension-change
	 * respawn (e.g. leaving the End), which does carry effects over and needs
	 * none of this. Called by {@link com.pantheon.mixin.HotbarRespawnMixin}.
	 */
	public static void onPlayerRespawn(final ServerPlayer newPlayer, final boolean keepAllPlayerData) {
		if (!keepAllPlayerData) {
			SharedStats.onRespawn(newPlayer);
		}
	}

	public static ResourceLocation id(final String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
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
	 * <p>Called at both the start and end of every tick (see
	 * {@link #onServerTickStart} and {@link #onServerTickEnd}), which
	 * reliably closes that specific duplication case: nothing else runs
	 * between this and {@link SharedStats#tick} within the same end-of-tick
	 * handler, so a shared-pool death can never see the gamerule off. It
	 * narrows, but can't fully close, the much rarer case of an ordinary
	 * (non-pool) death from normal combat landing in the exact same tick as
	 * the gamerule being flipped, before either of these two calls has run
	 * again - closing that completely would mean intercepting the
	 * {@code /gamerule} command itself, which felt like more surface area
	 * than this edge case warrants.
	 */
	private static void enforceKeepInventory(final MinecraftServer server) {
		GameRules.BooleanValue keepInventory = server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
		if (!keepInventory.get()) {
			keepInventory.set(true, server);
			LOGGER.warn("[PantheonMod] keepInventory was turned off - Pantheon requires it, forcing it back on.");
		}
	}
}
