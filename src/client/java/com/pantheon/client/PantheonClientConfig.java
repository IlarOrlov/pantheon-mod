package com.pantheon.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.pantheon.PantheonMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Purely local, per-client settings - never sent to or read from the server.
 * Currently just the low-health screen tint toggle: whether a warning is
 * shown is each player's own business, unlike everything in
 * {@link com.pantheon.PantheonConfig}, which has to be identical for
 * everyone since it changes actual shared gameplay behavior.
 */
public final class PantheonClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile PantheonClientConfig instance = new PantheonClientConfig();
	private static Path configPath;

	public boolean lowHealthWarningEnabled = true;

	public static PantheonClientConfig get() {
		return instance;
	}

	public static synchronized void load() {
		configPath = FabricLoader.getInstance().getConfigDir().resolve("pantheon-client.json");
		if (Files.exists(configPath)) {
			try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
				PantheonClientConfig loaded = GSON.fromJson(reader, PantheonClientConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (IOException | RuntimeException e) {
				PantheonMod.LOGGER.warn("Failed to read config/pantheon-client.json, using defaults", e);
			}
		}
		save();
	}

	public static synchronized void save() {
		if (configPath == null) {
			configPath = FabricLoader.getInstance().getConfigDir().resolve("pantheon-client.json");
		}
		try {
			Files.createDirectories(configPath.getParent());
			Path tmp = configPath.resolveSibling("pantheon-client.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
			Files.move(tmp, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			PantheonMod.LOGGER.warn("Failed to write config/pantheon-client.json", e);
		}
	}

	public static synchronized void setLowHealthWarningEnabled(final boolean enabled) {
		instance.lowHealthWarningEnabled = enabled;
		save();
	}
}
