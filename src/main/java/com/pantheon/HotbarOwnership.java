package com.pantheon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pantheon.network.HotbarOwnersPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Decides which online player "owns" each hotbar slot right now: that
 * player's color is framed around the slot for everyone
 * ({@link com.pantheon.client.HotbarOwnerOverlay}), and nobody else can touch
 * it ({@link com.pantheon.mixin.HotbarLockMixin}). Purely inert unless
 * {@link PantheonConfig#enableHotbarOwnership} is on.
 */
public final class HotbarOwnership {
	private HotbarOwnership() {
	}

	/**
	 * Every online player gets one of these slots (0-8) for as long as they
	 * stay connected, assigned in UUID order as they join. It is always their
	 * color; in {@link PantheonConfig.HotbarOwnershipMode#FIXED} mode it is
	 * also the one slot they own.
	 */
	private static final Map<UUID, Integer> JOIN_ORDER = new LinkedHashMap<>();

	private static List<UUID> lastBroadcastOwners = null;

	public static void onDisconnect(final UUID player) {
		JOIN_ORDER.remove(player);
	}

	/** slot index -> owning player UUID (or {@link HotbarOwnersPayload#NO_OWNER}) for right now. */
	public static List<UUID> currentOwners(final MinecraftServer server) {
		PantheonConfig config = PantheonConfig.get();
		List<ServerPlayer> online = new ArrayList<>(server.getPlayerList().getPlayers());

		List<UUID> owners = new ArrayList<>(HotbarOwnersPayload.SLOT_COUNT);
		for (int i = 0; i < HotbarOwnersPayload.SLOT_COUNT; i++) {
			owners.add(HotbarOwnersPayload.NO_OWNER);
		}

		if (!config.enableHotbarOwnership || online.isEmpty()) {
			JOIN_ORDER.keySet().retainAll(online.stream().map(ServerPlayer::getUUID).toList());
			return owners;
		}

		assignJoinOrder(online);

		if (config.hotbarOwnershipMode == PantheonConfig.HotbarOwnershipMode.FIXED) {
			for (ServerPlayer player : online) {
				Integer slot = JOIN_ORDER.get(player.getUUID());
				if (slot != null) {
					owners.set(slot, player.getUUID());
				}
			}
			return owners;
		}

		int[] selectedCount = new int[HotbarOwnersPayload.SLOT_COUNT];
		UUID[] selectedBy = new UUID[HotbarOwnersPayload.SLOT_COUNT];
		for (ServerPlayer player : online) {
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

	/** The owner's persistent color index (0-8, stable while connected), or -1 if unknown. */
	public static int colorOf(final UUID player) {
		Integer index = JOIN_ORDER.get(player);
		return index == null ? -1 : index;
	}

	private static void assignJoinOrder(final List<ServerPlayer> online) {
		JOIN_ORDER.keySet().retainAll(online.stream().map(ServerPlayer::getUUID).toList());

		List<ServerPlayer> sorted = new ArrayList<>(online);
		sorted.sort(Comparator.comparing(ServerPlayer::getUUID));
		for (ServerPlayer player : sorted) {
			JOIN_ORDER.computeIfAbsent(player.getUUID(), id -> firstFreeIndex());
		}
	}

	private static int firstFreeIndex() {
		for (int i = 0; i < HotbarOwnersPayload.SLOT_COUNT; i++) {
			if (!JOIN_ORDER.containsValue(i)) {
				return i;
			}
		}
		return 0;
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

		List<Integer> colors = new ArrayList<>(HotbarOwnersPayload.SLOT_COUNT);
		for (UUID owner : owners) {
			colors.add(owner.equals(HotbarOwnersPayload.NO_OWNER) ? -1 : colorOf(owner));
		}

		HotbarOwnersPayload payload = new HotbarOwnersPayload(owners, colors);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
