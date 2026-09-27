package com.pantheon;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Resolves which {@link Team} each player is currently sharing with.
 *
 * <p>When {@link PantheonConfig#teamsEnabled} is off, {@link #teamOf} always
 * returns the one {@value #GLOBAL_TEAM_NAME} team regardless of any team
 * assignment on record - the original one-pool-for-the-whole-server
 * behavior. When it's on, a player resolves to whichever team they were
 * last assigned to via {@code /pantheon team}, or back to
 * {@value #GLOBAL_TEAM_NAME} if they were never assigned one - there's no
 * separate "default" team to keep track of; an unassigned player just stays
 * in the global pool until someone puts them on a real team.
 *
 * <p>Which teams exist and who's assigned to them persists in
 * {@code config/pantheon-teams.json} (loaded fresh on every server start via
 * {@link #load}), same as {@link PantheonConfig}'s settings. Each
 * {@link Team}'s shared items and armor/offhand persist with the world
 * itself via {@link TeamContentsSavedData}, so they survive leaving and
 * re-entering a world; the rest of a team's state (hotbar/health/hunger/xp
 * tracking) is session-only and starts fresh each time.
 */
public final class TeamManager {
	public static final String GLOBAL_TEAM_NAME = "global";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final Map<String, Team> teams = new LinkedHashMap<>();
	private static final Map<UUID, String> assignments = new LinkedHashMap<>();
	private static Path configPath;

	static {
		teams.put(GLOBAL_TEAM_NAME, new Team(GLOBAL_TEAM_NAME));
	}

	private TeamManager() {
	}

	/**
	 * Rebuilds every team from scratch (so session-only state starts fresh),
	 * re-reads which teams exist and who's on them from the config file, then
	 * restores each team's shared items/equipment from the world's saved data.
	 * Called on every server start (dedicated boot, or re-entering a
	 * singleplayer world), before any player has joined.
	 */
	public static synchronized void load(final MinecraftServer server) {
		teams.clear();
		assignments.clear();
		teams.put(GLOBAL_TEAM_NAME, new Team(GLOBAL_TEAM_NAME));

		configPath = FabricLoader.getInstance().getConfigDir().resolve("pantheon-teams.json");
		if (Files.exists(configPath)) {
			try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
				Persisted persisted = GSON.fromJson(reader, Persisted.class);
				if (persisted != null) {
					if (persisted.teamNames != null) {
						for (String name : persisted.teamNames) {
							teams.computeIfAbsent(name, Team::new);
						}
					}
					if (persisted.assignments != null) {
						for (Map.Entry<String, String> entry : persisted.assignments.entrySet()) {
							try {
								UUID uuid = UUID.fromString(entry.getKey());
								assignments.put(uuid, entry.getValue());
								teams.computeIfAbsent(entry.getValue(), Team::new);
							} catch (IllegalArgumentException ignored) {
								// Corrupt/foreign UUID string - drop just this entry rather than fail the whole load.
							}
						}
					}
				}
			} catch (IOException | RuntimeException e) {
				PantheonMod.LOGGER.warn("Failed to read config/pantheon-teams.json, starting with no custom teams", e);
			}
		}

		TeamContentsSavedData saved = server.getDataStorage().computeIfAbsent(TeamContentsSavedData.TYPE);
		for (Map.Entry<String, TeamContentsSavedData.TeamContents> entry : saved.loaded().entrySet()) {
			// A team that was deleted via /pantheon team since the world was last saved
			// stays gone - don't resurrect it just because its items are still on disk.
			Team team = teams.get(entry.getKey());
			if (team != null) {
				entry.getValue().applyTo(team);
			}
		}
		PantheonMod.LOGGER.info("[TeamManager] Loaded teams={}, assignments={}, restored contents for {}",
			teams.keySet(), assignments, saved.loaded().keySet());
	}

	/**
	 * Flags the shared contents for inclusion in the world save that's about
	 * to happen. The saved-data codec reads the live teams directly, so this
	 * only has to mark it dirty.
	 */
	public static void markDirty(final MinecraftServer server) {
		server.getDataStorage().computeIfAbsent(TeamContentsSavedData.TYPE).setDirty();
	}

	/** Writes to a temp sibling file and atomically moves it into place - see {@link PantheonConfig#save} for why. */
	public static synchronized void save() {
		if (configPath == null) {
			configPath = FabricLoader.getInstance().getConfigDir().resolve("pantheon-teams.json");
		}

		Persisted persisted = new Persisted();
		persisted.teamNames = new ArrayList<>();
		for (String name : teams.keySet()) {
			if (!GLOBAL_TEAM_NAME.equals(name)) {
				persisted.teamNames.add(name);
			}
		}
		persisted.assignments = new LinkedHashMap<>();
		for (Map.Entry<UUID, String> entry : assignments.entrySet()) {
			persisted.assignments.put(entry.getKey().toString(), entry.getValue());
		}

		try {
			Files.createDirectories(configPath.getParent());
			Path tmp = configPath.resolveSibling("pantheon-teams.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(persisted, writer);
			}
			Files.move(tmp, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			PantheonMod.LOGGER.info("[TeamManager] Saved teams={}, assignments={}", teams.keySet(), assignments);
		} catch (IOException e) {
			PantheonMod.LOGGER.warn("[TeamManager] Failed to write config/pantheon-teams.json", e);
		}
	}

	private static final class Persisted {
		List<String> teamNames;
		Map<String, String> assignments;
	}

	public static Team teamOf(final UUID player) {
		if (!PantheonConfig.get().teamsEnabled) {
			return teams.get(GLOBAL_TEAM_NAME);
		}
		String name = assignments.get(player);
		if (name == null) {
			return teams.get(GLOBAL_TEAM_NAME);
		}
		return teams.computeIfAbsent(name, Team::new);
	}

	public static Team teamOf(final ServerPlayer player) {
		return teamOf(player.getUUID());
	}

	/** The team name a player would show up under in {@code /pantheon team list}, regardless of teamsEnabled. */
	public static String assignedTeamName(final UUID player) {
		return assignments.getOrDefault(player, GLOBAL_TEAM_NAME);
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
		save();
		return true;
	}

	/** Deletes a team (never the global one) and un-assigns anyone on it, who then fall back to the global pool. */
	public static boolean delete(final String name) {
		if (GLOBAL_TEAM_NAME.equals(name) || !teams.containsKey(name)) {
			return false;
		}
		teams.remove(name);
		assignments.values().removeIf(name::equals);
		save();
		return true;
	}

	public static void assign(final UUID player, final String teamName) {
		assignments.put(player, teamName);
		save();
	}

	public static void unassign(final UUID player) {
		assignments.remove(player);
		save();
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
	 * away. Equipment and hotbar-ownership/health/hunger/xp tracking don't
	 * need this - they're resolved live, per call/tick - but the shared item
	 * list is a backing reference swapped once, so switching teams has to
	 * redo that swap explicitly.
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
