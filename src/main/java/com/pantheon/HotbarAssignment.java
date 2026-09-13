package com.pantheon;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.pantheon.network.HotbarOwnersPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Purely cosmetic bookkeeping: decides which online player each hotbar slot
 * is "tagged" as belonging to right now, and tells every client. Players are
 * ordered by UUID so the assignment is stable and identical on every client.
 */
public final class HotbarAssignment {
	private HotbarAssignment() {
	}

	public static void broadcast(final MinecraftServer server) {
		List<ServerPlayer> online = new ArrayList<>(server.getPlayerList().getPlayers());
		online.sort((a, b) -> a.getUUID().compareTo(b.getUUID()));

		List<UUID> owners = new ArrayList<>(HotbarOwnersPayload.SLOT_COUNT);
		for (int slot = 0; slot < HotbarOwnersPayload.SLOT_COUNT; slot++) {
			owners.add(slot < online.size() ? online.get(slot).getUUID() : HotbarOwnersPayload.NO_OWNER);
		}

		HotbarOwnersPayload payload = new HotbarOwnersPayload(owners);
		for (ServerPlayer player : online) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
