package com.pantheon.network;

import com.pantheon.FunnyMessages;
import com.pantheon.HotbarOwnership;
import com.pantheon.PantheonConfig;
import com.pantheon.PantheonMod;
import com.pantheon.Team;
import com.pantheon.TeamManager;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.util.List;
import java.util.UUID;

public final class PantheonNetworking {
	/** Minimum gap between one player's slot-request pings, so it can't be spammed. */
	private static final int PING_COOLDOWN_TICKS = 60;

	private PantheonNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(HotbarOwnersPayload.TYPE, HotbarOwnersPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SyncConfigPayload.TYPE, SyncConfigPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ForceHotbarSlotPayload.TYPE, ForceHotbarSlotPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UpdateConfigPayload.TYPE, UpdateConfigPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestSlotPayload.TYPE, RequestSlotPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(UpdateConfigPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			MinecraftServer server = context.server();
			server.execute(() -> handleUpdateConfig(server, player, payload));
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestSlotPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			MinecraftServer server = context.server();
			server.execute(() -> handleRequestSlot(server, player, payload));
		});
	}

	private static void handleRequestSlot(final MinecraftServer server, final ServerPlayer requester, final RequestSlotPayload payload) {
		if (!PantheonConfig.get().enableHotbarOwnership) {
			return;
		}
		int slot = payload.slot();
		if (slot < 0 || slot >= HotbarOwnersPayload.SLOT_COUNT) {
			return;
		}

		List<UUID> owners = HotbarOwnership.currentOwnersFor(requester);
		UUID ownerId = owners.get(slot);
		if (ownerId.equals(HotbarOwnersPayload.NO_OWNER) || ownerId.equals(requester.getUUID())) {
			return;
		}

		Team team = TeamManager.teamOf(requester);
		long now = server.getTickCount();
		Long lastPing = team.lastPingTick.get(requester.getUUID());
		if (lastPing != null && now - lastPing < PING_COOLDOWN_TICKS) {
			return;
		}
		team.lastPingTick.put(requester.getUUID(), now);

		ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
		// currentOwnersFor already only returns owners among the requester's own
		// teammates, but re-checking here too means this can never regress into
		// cross-team pinging even if that computation is ever refactored.
		if (owner == null || TeamManager.teamOf(owner) != team) {
			return;
		}

		owner.sendOverlayMessage(FunnyMessages.randomSlotRequest(requester.getGameProfile().name()));
		requester.sendOverlayMessage(Component.literal("Poked " + owner.getGameProfile().name() + " about that slot."));
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

		TeamManager.reassignAllOnline(server);
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
