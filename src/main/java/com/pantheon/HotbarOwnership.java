package com.pantheon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pantheon.network.HotbarOwnersPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Decides which online player "owns" each hotbar slot right now, scoped to
 * one {@link Team} at a time: whichever slot a player currently has selected
 * is theirs, as long as nobody else <em>on their team</em> has also selected
 * it (a momentary tie leaves the slot unowned rather than picking a winner).
 * That player's color is framed around the slot for their teammates
 * ({@link com.pantheon.client.HotbarOwnerOverlay}), and nobody else on the
 * team can touch it or select it themselves
 * ({@link com.pantheon.mixin.HotbarLockMixin}, {@link com.pantheon.mixin.HotbarSelectionCapMixin}).
 * Purely inert unless {@link PantheonConfig#enableHotbarOwnership} is on.
 */
public final class HotbarOwnership {
	private HotbarOwnership() {
	}

	/** slot index -> owning player UUID (or {@link HotbarOwnersPayload#NO_OWNER}) for right now, among {@code members}. */
	public static List<UUID> currentOwners(final List<ServerPlayer> members) {
		List<UUID> owners = new ArrayList<>(HotbarOwnersPayload.SLOT_COUNT);
		for (int i = 0; i < HotbarOwnersPayload.SLOT_COUNT; i++) {
			owners.add(HotbarOwnersPayload.NO_OWNER);
		}

		if (!PantheonConfig.get().enableHotbarOwnership) {
			return owners;
		}

		int[] selectedCount = new int[HotbarOwnersPayload.SLOT_COUNT];
		UUID[] selectedBy = new UUID[HotbarOwnersPayload.SLOT_COUNT];
		for (ServerPlayer player : members) {
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

	/** Convenience for mixin call sites that only have a single player in hand: resolves their team and teammates for them. */
	public static List<UUID> currentOwnersFor(final ServerPlayer player) {
		MinecraftServer server = ((ServerLevel) player.level()).getServer();
		Team team = TeamManager.teamOf(player);
		return currentOwners(TeamManager.onlineMembersOf(team, server));
	}

	/**
	 * If {@code player}'s currently selected slot (loaded from their own save
	 * data, or just wherever they left off) is already owned by a different
	 * online teammate, moves them to the first free slot instead of contending
	 * for an occupied one. With the {@link PantheonConfig#HOTBAR_OWNERSHIP_PLAYER_CAP}
	 * per-team join limit in place there's always at least one free slot for a
	 * player who just successfully joined their team.
	 */
	public static void resolveJoinConflict(final MinecraftServer server, final ServerPlayer player) {
		if (!PantheonConfig.get().enableHotbarOwnership) {
			return;
		}

		Team team = TeamManager.teamOf(player);
		List<UUID> owners = currentOwners(TeamManager.onlineMembersOf(team, server));
		int mySlot = player.getInventory().getSelectedSlot();
		if (mySlot < 0 || mySlot >= HotbarOwnersPayload.SLOT_COUNT) {
			return;
		}

		UUID owner = owners.get(mySlot);
		if (owner.equals(HotbarOwnersPayload.NO_OWNER) || owner.equals(player.getUUID())) {
			return;
		}

		for (int i = 0; i < HotbarOwnersPayload.SLOT_COUNT; i++) {
			UUID other = owners.get(i);
			if (other.equals(HotbarOwnersPayload.NO_OWNER) || other.equals(player.getUUID())) {
				player.getInventory().setSelectedSlot(i);
				return;
			}
		}
	}

	/** Recomputes ownership per online team and, only for teams where it changed since the last check, pushes it to that team's clients. */
	public static void tick(final MinecraftServer server) {
		for (Map.Entry<Team, List<ServerPlayer>> entry : TeamManager.groupOnlineByTeam(server).entrySet()) {
			Team team = entry.getKey();
			List<ServerPlayer> members = entry.getValue();
			List<UUID> owners = currentOwners(members);
			if (!owners.equals(team.lastBroadcastOwners)) {
				sendToTeam(team, members, owners);
			}
		}
	}

	/** Recomputes ownership for every online team and unconditionally pushes it to each team's clients (join/disconnect/periodic resync/config change). */
	public static void broadcast(final MinecraftServer server) {
		for (Map.Entry<Team, List<ServerPlayer>> entry : TeamManager.groupOnlineByTeam(server).entrySet()) {
			Team team = entry.getKey();
			List<ServerPlayer> members = entry.getValue();
			sendToTeam(team, members, currentOwners(members));
		}
	}

	private static void sendToTeam(final Team team, final List<ServerPlayer> members, final List<UUID> owners) {
		team.lastBroadcastOwners = owners;

		HotbarOwnersPayload payload = new HotbarOwnersPayload(owners);
		for (ServerPlayer player : members) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
