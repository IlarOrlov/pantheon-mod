package com.pantheon;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.pantheon.network.HotbarOwnersPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Decides which online player "owns" each hotbar slot right now: whichever
 * slot a player currently has selected is theirs, as long as nobody else has
 * also selected it (a momentary tie leaves the slot unowned rather than
 * picking a winner). That player's color is framed around the slot for
 * everyone ({@link com.pantheon.client.HotbarOwnerOverlay}), and nobody else
 * can touch it or select it themselves
 * ({@link com.pantheon.mixin.HotbarLockMixin}, {@link com.pantheon.mixin.HotbarSelectionCapMixin}).
 * Purely inert unless {@link PantheonConfig#enableHotbarOwnership} is on.
 */
public final class HotbarOwnership {
	private HotbarOwnership() {
	}

	private static List<UUID> lastBroadcastOwners = null;

	/** slot index -> owning player UUID (or {@link HotbarOwnersPayload#NO_OWNER}) for right now. */
	public static List<UUID> currentOwners(final MinecraftServer server) {
		List<UUID> owners = new ArrayList<>(HotbarOwnersPayload.SLOT_COUNT);
		for (int i = 0; i < HotbarOwnersPayload.SLOT_COUNT; i++) {
			owners.add(HotbarOwnersPayload.NO_OWNER);
		}

		if (!PantheonConfig.get().enableHotbarOwnership) {
			return owners;
		}

		int[] selectedCount = new int[HotbarOwnersPayload.SLOT_COUNT];
		UUID[] selectedBy = new UUID[HotbarOwnersPayload.SLOT_COUNT];
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int slot = player.getInventory().getSelectedSlot();
			if (slot >= 0 && slot < HotbarOwnersPayload.SLOT_COUNT) {
				selectedCount[slot]++;
				selectedBy[slot] = player.getUUID();
			}
		}
		for (int i = 0; i < HotbarOwnersPayload.SLOT_COUNT; i++) {
			if (selectedCount[i] == 1) {
				owners.set(i, selectedBy[i]);
			}
		}
		return owners;
	}

	/** Recomputes ownership and, only if it changed since the last check, pushes it to every client. */
	public static void tick(final MinecraftServer server) {
		List<UUID> owners = currentOwners(server);
		if (owners.equals(lastBroadcastOwners)) {
			return;
		}
		sendToAll(server, owners);
	}

	/** Recomputes ownership and unconditionally pushes it to every client (join/disconnect/periodic resync). */
	public static void broadcast(final MinecraftServer server) {
		sendToAll(server, currentOwners(server));
	}

	private static void sendToAll(final MinecraftServer server, final List<UUID> owners) {
		lastBroadcastOwners = owners;

		HotbarOwnersPayload payload = new HotbarOwnersPayload(owners);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
