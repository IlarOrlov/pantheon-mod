package com.pantheon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pantheon.network.ForceHotbarSlotPayload;
import com.pantheon.network.HotbarOwnersPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Decides which online player "owns" each hotbar slot right now, scoped to
 * one {@link Team} at a time: whichever slot a player currently has selected
 * is theirs, as long as nobody else <em>on their team</em> has also selected
 * it (a momentary tie leaves the slot unowned rather than picking a winner),
 * and as long as they're actually alive - a player sitting on the death
 * screen isn't using their slot, so it's freed (frame gone, teammates can
 * take it) for as long as they're dead, and re-claimed automatically the
 * moment they respawn. That player's color is framed around the slot for
 * their teammates
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
			if (player.getHealth() <= 0f) {
				// Dead, sitting on the death screen awaiting their own respawn
				// click - not actually using this slot right now, so don't
				// hold it hostage from the rest of the team while they're gone.
				continue;
			}
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
	 * data on join, or carried over from before they died on respawn) is also
	 * selected by a different online teammate, moves {@code player} to the
	 * first free slot instead of leaving two players contending for the same
	 * one. With the {@link PantheonConfig#HOTBAR_OWNERSHIP_PLAYER_CAP}
	 * per-team join limit in place there's always at least one free slot.
	 *
	 * <p>This deliberately scans {@code members}' own selected slots directly
	 * rather than going through {@link #currentOwners}, which would collapse
	 * a genuine two-player collision on {@code player}'s slot into "unowned"
	 * (its tie-breaking rule for a <em>momentary</em> tie while two clients'
	 * selections are mid-flight) - that would read as "no conflict, nothing
	 * to move" and leave both players stuck sharing the slot indefinitely,
	 * instead of resolving it.
	 *
	 * <p>Selecting a hotbar slot is normally entirely client-driven - the
	 * server only ever trusts whatever the client last reported. On respawn
	 * specifically, the server correctly carries the player's selected slot
	 * over from before they died ({@code Inventory#replaceWith} inside
	 * {@code ServerPlayer#restoreFrom}) - but that's a server-side copy
	 * between two entity instances that the client has no part in, so the
	 * client's <em>own</em> freshly-respawned local view of its selected slot
	 * resets to 0 independently, with no idea the server just restored a
	 * different value. Left alone, the client then reports that stale 0 back
	 * as an entirely ordinary selection-change packet: unremarkable if 0
	 * happens to be free, but if it's already someone else's, our own lock
	 * silently rejects the packet server-side while the client is never told
	 * - so it keeps believing (and rendering its own lock frame on) the slot
	 * 0 it already thinks is its, stacked right on top of the real owner's
	 * frame everyone else's broadcast correctly shows there. Always telling
	 * the client its actual resolved slot here, via {@link ForceHotbarSlotPayload},
	 * regardless of whether a conflict needed resolving, closes that gap at
	 * the source instead of only patching the cases where we ourselves moved
	 * the player.
	 */
	public static void resolveSlotConflict(final MinecraftServer server, final ServerPlayer player) {
		if (!PantheonConfig.get().enableHotbarOwnership) {
			return;
		}

		Team team = TeamManager.teamOf(player);
		List<ServerPlayer> members = TeamManager.onlineMembersOf(team, server);
		int mySlot = player.getInventory().getSelectedSlot();
		if (mySlot < 0 || mySlot >= HotbarOwnersPayload.SLOT_COUNT) {
			return;
		}

		boolean[] takenByOthers = new boolean[HotbarOwnersPayload.SLOT_COUNT];
		boolean contested = false;
		for (ServerPlayer other : members) {
			if (other == player || other.getHealth() <= 0f) {
				// Skip dead teammates too - their slot is already free (see
				// currentOwners), so it shouldn't block this player from it.
				continue;
			}
			int slot = other.getInventory().getSelectedSlot();
			if (slot < 0 || slot >= HotbarOwnersPayload.SLOT_COUNT) {
				continue;
			}
			takenByOthers[slot] = true;
			if (slot == mySlot) {
				contested = true;
			}
		}

		int finalSlot = mySlot;
		if (contested) {
			int free = firstFreeSlot(takenByOthers);
			if (free >= 0) {
				finalSlot = free;
				player.getInventory().setSelectedSlot(free);
			}
			PantheonMod.LOGGER.info("[HotbarOwnership] resolveSlotConflict: {} moved from contested slot {} to {} in team '{}'",
				player.getGameProfile().name(), mySlot, finalSlot, team.name);
		} else {
			PantheonMod.LOGGER.info("[HotbarOwnership] resolveSlotConflict: {} on slot {} in team '{}' - no conflict",
				player.getGameProfile().name(), mySlot, team.name);
		}

		ServerPlayNetworking.send(player, new ForceHotbarSlotPayload(finalSlot));
	}

	/**
	 * Called right when {@link PantheonConfig#enableHotbarOwnership}
	 * transitions from off to on - most notably when two-hand mode (which
	 * forces every online player onto the center slot) is turned back off and this
	 * restores ownership with it, but just as easily several players who
	 * happened to park on the same slot while ownership was off. Left alone,
	 * {@link #currentOwners} correctly treats that as an unowned tie
	 * server-side, but each client's own {@code effectiveOwner} local
	 * prediction shows *itself* as the owner of whatever slot it currently
	 * has selected regardless of any tie - so every one of those players
	 * would see their own color painted over that same slot until they
	 * happen to manually move off it. Spreading every team's tied-up members
	 * out across distinct free slots up front, before the first ownership
	 * broadcast after the transition, avoids that altogether.
	 */
	public static void spreadOutOnEnable(final MinecraftServer server) {
		for (Map.Entry<Team, List<ServerPlayer>> entry : TeamManager.groupOnlineByTeam(server).entrySet()) {
			boolean[] taken = new boolean[HotbarOwnersPayload.SLOT_COUNT];
			for (ServerPlayer player : entry.getValue()) {
				if (player.getHealth() <= 0f) {
					// Dead, sitting on the death screen - not actually using
					// their selected slot right now (see currentOwners), so
					// they shouldn't reserve it from a live teammate, or need
					// moving off it themselves.
					continue;
				}
				int slot = player.getInventory().getSelectedSlot();
				if (slot >= 0 && slot < HotbarOwnersPayload.SLOT_COUNT && !taken[slot]) {
					taken[slot] = true;
					continue;
				}
				int free = firstFreeSlot(taken);
				if (free < 0) {
					// Can't happen while the HOTBAR_OWNERSHIP_PLAYER_CAP team
					// size cap holds - there's always at least one free slot
					// for one more team member - but leave them where they
					// are rather than throw if that invariant is ever broken.
					continue;
				}
				taken[free] = true;
				player.getInventory().setSelectedSlot(free);
				ServerPlayNetworking.send(player, new ForceHotbarSlotPayload(free));
			}
		}
	}

	/** Index of the first slot not marked {@code taken}, or -1 if every slot is. */
	private static int firstFreeSlot(final boolean[] taken) {
		for (int i = 0; i < taken.length; i++) {
			if (!taken[i]) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Two-hand mode leaves exactly one selectable hotbar slot
	 * ({@link HotbarOwnersPayload#TWO_HAND_ACTIVE_SLOT}, the center slot); a
	 * player can still show up with a different slot selected - loaded from
	 * their own save data from before the mode was turned on, or a stale
	 * client-predicted selection - so force it back every tick rather than
	 * only at the moments {@link com.pantheon.mixin.HotbarSelectionCapMixin}
	 * happens to see a selection packet.
	 */
	public static void enforceTwoHandMode(final MinecraftServer server) {
		if (!PantheonConfig.get().twoHandSlotMode) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.getInventory().getSelectedSlot() != HotbarOwnersPayload.TWO_HAND_ACTIVE_SLOT) {
				player.getInventory().setSelectedSlot(HotbarOwnersPayload.TWO_HAND_ACTIVE_SLOT);
				ServerPlayNetworking.send(player, new ForceHotbarSlotPayload(HotbarOwnersPayload.TWO_HAND_ACTIVE_SLOT));
			}
		}
	}

	/**
	 * Recomputes ownership per online team and, only for teams where it
	 * changed since the last check, pushes it to that team's clients. Takes
	 * the online-players-by-team grouping already computed by the caller
	 * (rather than a {@code MinecraftServer} it would have to group itself)
	 * since it's always called from {@link PantheonMod}'s per-tick handler
	 * right alongside {@link SharedStats#tick}, which needs the exact same
	 * grouping - computing it twice, every tick, would be pure waste.
	 */
	public static void tick(final Map<Team, List<ServerPlayer>> onlineByTeam) {
		for (Map.Entry<Team, List<ServerPlayer>> entry : onlineByTeam.entrySet()) {
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
		broadcast(TeamManager.groupOnlineByTeam(server));
	}

	/** Same as {@link #broadcast(MinecraftServer)}, for a caller (the per-tick periodic resync) that already has the grouping on hand. */
	public static void broadcast(final Map<Team, List<ServerPlayer>> onlineByTeam) {
		for (Map.Entry<Team, List<ServerPlayer>> entry : onlineByTeam.entrySet()) {
			Team team = entry.getKey();
			List<ServerPlayer> members = entry.getValue();
			sendToTeam(team, members, currentOwners(members));
		}
	}

	private static void sendToTeam(final Team team, final List<ServerPlayer> members, final List<UUID> owners) {
		team.lastBroadcastOwners = owners;

		PantheonMod.LOGGER.info("[HotbarOwnership] broadcast to team '{}' ({} members): {}", team.name, members.size(), describe(members, owners));

		HotbarOwnersPayload payload = new HotbarOwnersPayload(owners);
		for (ServerPlayer player : members) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** Renders the owners list as "name(slot=selected)" for every member, plus the raw owner UUID-or-name per slot, for diagnostic logging. */
	private static String describe(final List<ServerPlayer> members, final List<UUID> owners) {
		StringBuilder sb = new StringBuilder();
		for (ServerPlayer player : members) {
			sb.append(player.getGameProfile().name()).append("(selected=").append(player.getInventory().getSelectedSlot()).append(") ");
		}
		sb.append("| owners=[");
		for (int i = 0; i < owners.size(); i++) {
			UUID owner = owners.get(i);
			String name = HotbarOwnersPayload.NO_OWNER.equals(owner) ? "-" : nameOf(members, owner);
			sb.append(i).append(':').append(name);
			if (i < owners.size() - 1) {
				sb.append(' ');
			}
		}
		sb.append(']');
		return sb.toString();
	}

	private static String nameOf(final List<ServerPlayer> members, final UUID uuid) {
		for (ServerPlayer player : members) {
			if (player.getUUID().equals(uuid)) {
				return player.getGameProfile().name();
			}
		}
		return uuid.toString();
	}
}
