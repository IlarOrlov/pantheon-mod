package com.pantheon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.pantheon.network.LocationMarkPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of location marks: a player middle-clicks a spot, and every
 * online member of their team sees a marker there in that player's color
 * (drawn client-side by {@code com.pantheon.client.LocationMarkOverlay})
 * until {@link PantheonConfig#locationMarkLifetimeSeconds} runs out. One
 * mark per player - placing another moves it, and marking the exact same
 * block again takes it down early.
 */
public final class LocationMarks {
	/** Minimum gap between one player's marks, so a held or bouncing button can't spam the team. */
	private static final int PLACE_COOLDOWN_TICKS = 5;

	/** Farther than any client could have legitimately raycast to - anything past this is ignored. */
	private static final double MAX_DISTANCE = 1024.0;

	private record ActiveMark(Team team, String placerName, Identifier dimension, BlockPos pos, long expiresTick) {
	}

	private static final Map<UUID, ActiveMark> ACTIVE = new HashMap<>();
	private static final Map<UUID, Long> LAST_PLACED_TICK = new HashMap<>();

	private LocationMarks() {
	}

	public static void place(final MinecraftServer server, final ServerPlayer player, final BlockPos pos) {
		PantheonConfig config = PantheonConfig.get();
		if (!config.locationMarks) {
			player.sendOverlayMessage(Component.literal("Location marks are turned off."));
			return;
		}

		long now = server.getTickCount();
		UUID uuid = player.getUUID();
		Long lastPlaced = LAST_PLACED_TICK.get(uuid);
		if (lastPlaced != null && now - lastPlaced < PLACE_COOLDOWN_TICKS) {
			return;
		}
		if (player.position().distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE * MAX_DISTANCE) {
			return;
		}
		LAST_PLACED_TICK.put(uuid, now);

		Team team = TeamManager.teamOf(player);
		Identifier dimension = player.level().dimension().identifier();
		String name = player.getGameProfile().name();

		ActiveMark previous = ACTIVE.get(uuid);
		boolean sameSpot = previous != null && previous.expiresTick() > now
			&& previous.pos().equals(pos) && previous.dimension().equals(dimension);
		int lifetimeTicks;
		if (sameSpot) {
			ACTIVE.remove(uuid);
			lifetimeTicks = 0;
		} else {
			lifetimeTicks = config.locationMarkLifetimeSeconds * 20;
			ACTIVE.put(uuid, new ActiveMark(team, name, dimension, pos, now + lifetimeTicks));
		}

		LocationMarkPayload payload = new LocationMarkPayload(uuid, name, dimension, pos, lifetimeTicks);
		// A mark moved to a different team since it was placed would leave the
		// old team's copy up until it expires - take it down there explicitly.
		if (previous != null && previous.team() != team) {
			sendToTeam(server, previous.team(), new LocationMarkPayload(uuid, name, previous.dimension(), previous.pos(), 0));
		}
		sendToTeam(server, team, payload);
	}

	/** Catches a joining player up on their team's marks that are still up. */
	public static void sendActiveTo(final MinecraftServer server, final ServerPlayer player) {
		long now = server.getTickCount();
		Team team = TeamManager.teamOf(player);
		ACTIVE.entrySet().removeIf(entry -> entry.getValue().expiresTick() <= now);
		for (Map.Entry<UUID, ActiveMark> entry : ACTIVE.entrySet()) {
			ActiveMark mark = entry.getValue();
			if (mark.team() != team) {
				continue;
			}
			ServerPlayNetworking.send(player, new LocationMarkPayload(
				entry.getKey(), mark.placerName(), mark.dimension(), mark.pos(), (int) (mark.expiresTick() - now)));
		}
	}

	/** Forget every mark - the server is going away (or a new one is starting in the same JVM). */
	public static void clear() {
		ACTIVE.clear();
		LAST_PLACED_TICK.clear();
	}

	private static void sendToTeam(final MinecraftServer server, final Team team, final LocationMarkPayload payload) {
		for (ServerPlayer member : TeamManager.onlineMembersOf(team, server)) {
			ServerPlayNetworking.send(member, payload);
		}
	}
}
