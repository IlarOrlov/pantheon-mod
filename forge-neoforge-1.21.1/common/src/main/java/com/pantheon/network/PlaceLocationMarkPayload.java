package com.pantheon.network;

import com.pantheon.PantheonMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent from a client when its player middle-clicks to mark a spot: the
 * block they're looking at, found by the client's own long-range raycast.
 * The server relays it to the placer's team as a {@link LocationMarkPayload}.
 */
public record PlaceLocationMarkPayload(BlockPos pos) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PlaceLocationMarkPayload> TYPE = new CustomPacketPayload.Type<>(PantheonMod.id("place_location_mark"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PlaceLocationMarkPayload> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, PlaceLocationMarkPayload::pos,
		PlaceLocationMarkPayload::new
	);

	@Override
	public CustomPacketPayload.Type<PlaceLocationMarkPayload> type() {
		return TYPE;
	}
}
