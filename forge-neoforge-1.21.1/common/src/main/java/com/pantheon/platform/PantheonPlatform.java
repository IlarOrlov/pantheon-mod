package com.pantheon.platform;

import java.nio.file.Path;
import java.util.ServiceLoader;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The few things the shared code needs from whichever mod loader it runs on.
 * Each loader module provides exactly one implementation, registered under
 * {@code META-INF/services}.
 */
public interface PantheonPlatform {
	PantheonPlatform INSTANCE = ServiceLoader.load(PantheonPlatform.class, PantheonPlatform.class.getClassLoader())
		.findFirst()
		.orElseThrow(() -> new IllegalStateException("No Pantheon platform implementation found"));

	/** The game's {@code config} directory. */
	Path configDir();

	/** Sends a server-to-client payload to one player. */
	void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

	/** Sends a client-to-server payload. Only ever called on the client. */
	void sendToServer(CustomPacketPayload payload);
}
