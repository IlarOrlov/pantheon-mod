package com.pantheon.network;

import com.pantheon.PantheonMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent from a client to the server when the local player presses the
 * "Request slot" key while hovering a hotbar slot locked to someone else, so
 * the server can politely nudge that owner to free it up. Purely a courtesy
 * ping - it never touches the slot itself, and the server independently
 * verifies the slot really is locked to someone else and enforces a cooldown
 * before relaying anything (see {@code PantheonNetworking}).
 */
public record RequestSlotPayload(int slot) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RequestSlotPayload> TYPE = new CustomPacketPayload.Type<>(PantheonMod.id("request_slot"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RequestSlotPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, RequestSlotPayload::slot,
		RequestSlotPayload::new
	);

	@Override
	public CustomPacketPayload.Type<RequestSlotPayload> type() {
		return TYPE;
	}
}
