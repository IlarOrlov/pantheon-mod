package com.pantheon;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Server-authoritative settings for how much is shared and whether hotbar
 * slots are exclusively "owned" by one player at a time. Loaded from (and
 * saved back to) {@code config/pantheon.json}. There is exactly one instance
 * for the running server; every field here must stay identical for every
 * connected client, so it is only ever changed through {@link #applyAndSave}
 * (the in-game settings screen and the {@code /pantheon} command both funnel
 * through it) rather than being edited piecemeal.
 */
public final class PantheonConfig {
	/** Slots on the server are capped at this many once hotbar ownership is enabled. */
	public static final int HOTBAR_OWNERSHIP_PLAYER_CAP = 9;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static volatile PantheonConfig instance = new PantheonConfig();
	private static Path configPath;

	public boolean syncArmor = false;
	public boolean syncOffhand = false;
	public boolean enableHotbarOwnership = true;
	public boolean syncHealth = false;
	public boolean syncHunger = false;
	public boolean syncExperience = false;
	/** When on, sharing is scoped per-team ({@code /pantheon team}) instead of one pool for the whole server. */
	public boolean teamsEnabled = true;
	/** When on, the propagated-death jokes can also draw from a cruder, swear-heavier phrase pool. Off by default. */
	public boolean crudeHumor = false;

	public static PantheonConfig get() {
		return instance;
	}

	public boolean isEquipmentSlotShared(final EquipmentSlot slot) {
		return switch (slot) {
			case HEAD, CHEST, LEGS, FEET -> this.syncArmor;
			case OFFHAND -> this.syncOffhand;
			default -> false;
		};
	}

	public static synchronized void load() {
		configPath = FabricLoader.getInstance().getConfigDir().resolve("pantheon.json");
		if (Files.exists(configPath)) {
			try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
				PantheonConfig loaded = GSON.fromJson(reader, PantheonConfig.class);
				if (loaded != null) {
					instance = loaded;
					PantheonMod.LOGGER.info("[PantheonConfig] Loaded config/pantheon.json: {}", describe(instance));
				} else {
					PantheonMod.LOGGER.warn("[PantheonConfig] config/pantheon.json parsed as null - keeping defaults: {}", describe(instance));
				}
			} catch (IOException | RuntimeException e) {
				PantheonMod.LOGGER.warn("[PantheonConfig] Failed to read config/pantheon.json, using defaults: {}", describe(instance), e);
			}
		} else {
			PantheonMod.LOGGER.info("[PantheonConfig] No config/pantheon.json yet - using defaults: {}", describe(instance));
		}
		save();
	}

	/**
	 * Writes to a temporary sibling file and atomically moves it over the
	 * real one, so a write that's interrupted (crash, forced process kill,
	 * the game closing mid-write) can never leave {@code pantheon.json} in a
	 * half-written, unparseable state - which {@link #load} would otherwise
	 * silently treat as "no valid config" and quietly reset to defaults, on
	 * top of the actual data loss from the interrupted write itself.
	 */
	public static synchronized void save() {
		if (configPath == null) {
			configPath = FabricLoader.getInstance().getConfigDir().resolve("pantheon.json");
		}
		try {
			Files.createDirectories(configPath.getParent());
			Path tmp = configPath.resolveSibling("pantheon.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
			Files.move(tmp, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			PantheonMod.LOGGER.info("[PantheonConfig] Saved config/pantheon.json: {}", describe(instance));
		} catch (IOException e) {
			PantheonMod.LOGGER.warn("[PantheonConfig] Failed to write config/pantheon.json", e);
		}
	}

	/** Replaces the live config, persists it, and returns it so callers can broadcast it. */
	public static synchronized PantheonConfig applyAndSave(final PantheonConfig updated) {
		instance = updated;
		save();
		return instance;
	}

	public PantheonConfig copy() {
		PantheonConfig copy = new PantheonConfig();
		copy.syncArmor = this.syncArmor;
		copy.syncOffhand = this.syncOffhand;
		copy.enableHotbarOwnership = this.enableHotbarOwnership;
		copy.syncHealth = this.syncHealth;
		copy.syncHunger = this.syncHunger;
		copy.syncExperience = this.syncExperience;
		copy.teamsEnabled = this.teamsEnabled;
		copy.crudeHumor = this.crudeHumor;
		return copy;
	}

	private static String describe(final PantheonConfig config) {
		return "syncArmor=" + config.syncArmor
			+ ", syncOffhand=" + config.syncOffhand
			+ ", enableHotbarOwnership=" + config.enableHotbarOwnership
			+ ", syncHealth=" + config.syncHealth
			+ ", syncHunger=" + config.syncHunger
			+ ", syncExperience=" + config.syncExperience
			+ ", teamsEnabled=" + config.teamsEnabled
			+ ", crudeHumor=" + config.crudeHumor;
	}
}
