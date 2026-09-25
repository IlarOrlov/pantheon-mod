package com.pantheon.network;

import java.util.UUID;

import com.pantheon.PantheonMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent from server to every online member of the placer's team: show (or,
 * with {@code lifetimeTicks == 0}, remove) {@code placer}'s location mark.
 * Each player has at most one mark at a time - a new one replaces the old.
 * Carries its own lifetime rather than leaving clients to read it from the
 * synced config, so a mark keeps the lifetime it was placed with even if
 * the setting changes while it's up.
 */
public record LocationMarkPayload(UUID placer, String placerName, Identifier dimension, BlockPos pos, int lifetimeTicks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<LocationMarkPayload> TYPE = new CustomPacketPayload.Type<>(PantheonMod.id("location_mark"));

	public static final StreamCodec<RegistryFriendlyByteBuf, LocationMarkPayload> CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC, LocationMarkPayload::placer,
		ByteBufCodecs.STRING_UTF8, LocationMarkPayload::placerName,
		Identifier.STREAM_CODEC, LocationMarkPayload::dimension,
		BlockPos.STREAM_CODEC, LocationMarkPayload::pos,
		ByteBufCodecs.VAR_INT, LocationMarkPayload::lifetimeTicks,
		LocationMarkPayload::new
	);

	@Override
	public CustomPacketPayload.Type<LocationMarkPayload> type() {
		return TYPE;
	}
}
