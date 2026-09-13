package com.pantheon.network;

import com.pantheon.HotbarOwnership;
import com.pantheon.PantheonConfig;
import com.pantheon.PantheonMod;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

public final class PantheonNetworking {
	private PantheonNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(HotbarOwnersPayload.TYPE, HotbarOwnersPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SyncConfigPayload.TYPE, SyncConfigPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UpdateConfigPayload.TYPE, UpdateConfigPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(UpdateConfigPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			MinecraftServer server = context.server();
			server.execute(() -> handleUpdateConfig(server, player, payload));
		});
	}

	private static void handleUpdateConfig(final MinecraftServer server, final ServerPlayer player, final UpdateConfigPayload payload) {
		if (!canConfigure(server, player)) {
			player.sendSystemMessage(Component.literal("You don't have permission to change Pantheon's settings."));
			return;
		}

		PantheonConfig updated = PantheonConfig.applyAndSave(payload.toConfig());
		PantheonMod.LOGGER.info("Pantheon config changed by {}: {}", player.getGameProfile().name(), updated);

		SyncConfigPayload syncPayload = SyncConfigPayload.fromConfig(updated);
		for (ServerPlayer online : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(online, syncPayload);
		}

		HotbarOwnership.broadcast(server);
	}

	/** The singleplayer host, or a server operator, may change Pantheon's shared settings. */
	public static boolean canConfigure(final MinecraftServer server, final ServerPlayer player) {
		NameAndId nameAndId = player.nameAndId();
		return server.isSingleplayerOwner(nameAndId) || server.getPlayerList().isOp(nameAndId);
	}

	public static void sendConfigTo(final ServerPlayer player) {
		ServerPlayNetworking.send(player, SyncConfigPayload.fromConfig(PantheonConfig.get()));
	}
}
