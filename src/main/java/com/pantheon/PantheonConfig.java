package com.pantheon;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

	public boolean syncArmor = true;
	public boolean syncOffhand = true;
	public boolean enableHotbarOwnership = false;
	public boolean syncHealth = false;
	public boolean syncHunger = false;
	/** When on, sharing is scoped per-team ({@code /pantheon team}) instead of one pool for the whole server. */
	public boolean teamsEnabled = false;

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
				}
			} catch (IOException | RuntimeException e) {
				PantheonMod.LOGGER.warn("Failed to read config/pantheon.json, using defaults", e);
			}
		}
		save();
	}

	public static synchronized void save() {
		if (configPath == null) {
			configPath = FabricLoader.getInstance().getConfigDir().resolve("pantheon.json");
		}
		try {
			Files.createDirectories(configPath.getParent());
			try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			PantheonMod.LOGGER.warn("Failed to write config/pantheon.json", e);
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
		copy.teamsEnabled = this.teamsEnabled;
		return copy;
	}
}
