package com.pantheon.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pantheon.network.LocationMarkPayload;
import com.pantheon.network.PlaceLocationMarkPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Client side of location marks: sends our own placements and keeps the
 * marks the server relays from our team (ourselves included) until they
 * expire, for {@link LocationMarkOverlay} to draw.
 */
public final class LocationMarkClient {
	/** How far the marking raycast reaches - well past any render distance, so anything visible can be marked. */
	private static final double MARK_REACH = 512.0;

	public record Mark(UUID placer, String placerName, Identifier dimension, BlockPos pos, long expiresAtMillis) {
	}

	private static final Map<UUID, Mark> MARKS = new HashMap<>();

	private LocationMarkClient() {
	}

	/**
	 * Whether a middle-click should place a mark instead of vanilla's pick
	 * block: marks are on, and either there's nothing in reach to pick (the
	 * click would otherwise do nothing at all) or we're sneaking, to mark
	 * something close by.
	 */
	public static boolean shouldMarkInsteadOfPick(final Minecraft minecraft) {
		if (minecraft.player == null || !PantheonModClient.getLastKnownConfig().locationMarks) {
			return false;
		}
		HitResult inReach = minecraft.hitResult;
		return inReach == null || inReach.getType() == HitResult.Type.MISS || minecraft.player.isShiftKeyDown();
	}

	public static void placeMark(final Minecraft minecraft) {
		if (minecraft.player == null) {
			return;
		}
		if (!PantheonModClient.getLastKnownConfig().locationMarks) {
			minecraft.player.sendOverlayMessage(Component.literal("Location marks are turned off."));
			return;
		}
		HitResult hit = minecraft.player.pick(MARK_REACH, 1.0F, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
			minecraft.player.sendOverlayMessage(Component.literal("Nothing to mark there."));
			return;
		}
		ClientPlayNetworking.send(new PlaceLocationMarkPayload(blockHit.getBlockPos()));
	}

	public static void onMarkPayload(final LocationMarkPayload payload) {
		if (payload.lifetimeTicks() <= 0) {
			MARKS.remove(payload.placer());
			return;
		}
		long expiresAt = System.currentTimeMillis() + payload.lifetimeTicks() * 50L;
		MARKS.put(payload.placer(), new Mark(payload.placer(), payload.placerName(), payload.dimension(), payload.pos(), expiresAt));
	}

	/** Every mark that's still up - expired ones are dropped on the way. */
	public static Collection<Mark> activeMarks() {
		if (MARKS.isEmpty()) {
			return List.of();
		}
		long now = System.currentTimeMillis();
		MARKS.values().removeIf(mark -> mark.expiresAtMillis() <= now);
		return MARKS.values();
	}

	public static void clear() {
		MARKS.clear();
	}
}
