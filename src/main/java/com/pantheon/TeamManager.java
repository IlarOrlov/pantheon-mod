package com.pantheon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Resolves which {@link Team} each player is currently sharing with.
 *
 * <p>When {@link PantheonConfig#teamsEnabled} is off, {@link #teamOf} always
 * returns the one {@value #GLOBAL_TEAM_NAME} team regardless of any team
 * assignment on record - the original one-pool-for-the-whole-server
 * behavior. When it's on, a player resolves to whichever team they were
 * last assigned to via {@code /pantheon team}, or {@value #DEFAULT_TEAM_NAME}
 * if they were never assigned one.
 */
public final class TeamManager {
	public static final String GLOBAL_TEAM_NAME = "global";
	public static final String DEFAULT_TEAM_NAME = "default";

	private static final Map<String, Team> teams = new LinkedHashMap<>();
	private static final Map<UUID, String> assignments = new LinkedHashMap<>();

	static {
		teams.put(GLOBAL_TEAM_NAME, new Team(GLOBAL_TEAM_NAME));
	}

	private TeamManager() {
	}

	public static void resetAll() {
		teams.clear();
		assignments.clear();
		teams.put(GLOBAL_TEAM_NAME, new Team(GLOBAL_TEAM_NAME));
	}

	public static Team teamOf(final UUID player) {
		if (!PantheonConfig.get().teamsEnabled) {
			return teams.get(GLOBAL_TEAM_NAME);
		}
		String name = assignments.getOrDefault(player, DEFAULT_TEAM_NAME);
		return teams.computeIfAbsent(name, Team::new);
	}

	public static Team teamOf(final ServerPlayer player) {
		return teamOf(player.getUUID());
	}

	/** The team name a player would show up under in {@code /pantheon team list}, regardless of teamsEnabled. */
	public static String assignedTeamName(final UUID player) {
		return assignments.getOrDefault(player, DEFAULT_TEAM_NAME);
	}

	public static boolean exists(final String name) {
		return teams.containsKey(name);
	}

	/** Creates an empty team; returns false if one with that name already exists. */
	public static boolean create(final String name) {
		if (teams.containsKey(name)) {
			return false;
		}
		teams.put(name, new Team(name));
		return true;
	}

	/** Deletes a team (never the global one) and un-assigns anyone on it, who then fall back to the default team. */
	public static boolean delete(final String name) {
		if (GLOBAL_TEAM_NAME.equals(name) || !teams.containsKey(name)) {
			return false;
		}
		teams.remove(name);
		assignments.values().removeIf(name::equals);
		return true;
	}

	public static void assign(final UUID player, final String teamName) {
		assignments.put(player, teamName);
	}

	public static void unassign(final UUID player) {
		assignments.remove(player);
	}

	public static List<Team> allTeams() {
		return new ArrayList<>(teams.values());
	}

	public static List<ServerPlayer> onlineMembersOf(final Team team, final MinecraftServer server) {
		List<ServerPlayer> members = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (teamOf(player) == team) {
				members.add(player);
			}
		}
		return members;
	}

	/** Every team with at least one online player right now, mapped to that list of players. */
	public static Map<Team, List<ServerPlayer>> groupOnlineByTeam(final MinecraftServer server) {
		Map<Team, List<ServerPlayer>> grouped = new LinkedHashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			grouped.computeIfAbsent(teamOf(player), t -> new ArrayList<>()).add(player);
		}
		return grouped;
	}

	/**
	 * Re-points an online player's shared inventory at whatever team they
	 * currently resolve to, and force-syncs their menu so they see it right
	 * away. Equipment and hotbar-ownership/health/hunger tracking don't need
	 * this - they're resolved live, per call/tick - but the shared item list
	 * is a backing reference swapped once, so switching teams has to redo
	 * that swap explicitly.
	 */
	public static void reassignInventory(final ServerPlayer player) {
		Team team = teamOf(player);
		((PantheonInventoryAccess) player.getInventory()).pantheon$setItems(team.items);
		player.inventoryMenu.broadcastFullState();
	}

	public static void reassignAllOnline(final MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			reassignInventory(player);
		}
	}
}
