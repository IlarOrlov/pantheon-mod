package com.pantheon.neoforge;

import java.nio.file.Path;

import com.pantheon.platform.PantheonPlatform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The channels are optional (see {@link PantheonNeoForge}), so every send
 * first checks the other side actually has Pantheon - a vanilla player or
 * server just doesn't get the packet.
 */
public final class NeoForgePlatform implements PantheonPlatform {
	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public void sendToPlayer(final ServerPlayer player, final CustomPacketPayload payload) {
		if (player.connection != null && player.connection.hasChannel(payload)) {
			PacketDistributor.sendToPlayer(player, payload);
		}
	}

	@Override
	public void sendToServer(final CustomPacketPayload payload) {
		Client.sendToServer(payload);
	}

	/** Kept apart so a dedicated server never loads the client classes it names. */
	private static final class Client {
		static void sendToServer(final CustomPacketPayload payload) {
			ClientPacketListener connection = Minecraft.getInstance().getConnection();
			if (connection != null && connection.hasChannel(payload)) {
				PacketDistributor.sendToServer(payload);
			}
		}
	}
}
