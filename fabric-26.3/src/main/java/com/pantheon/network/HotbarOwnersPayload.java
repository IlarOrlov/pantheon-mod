package com.pantheon.network;

import java.util.List;
import java.util.UUID;

import com.pantheon.PantheonMod;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent from server to every client whenever the set of online players
 * changes, or whenever anyone's selected hotbar slot changes. Carries exactly
 * {@link #SLOT_COUNT} entries, one per hotbar slot; an owner equal to
 * {@link #NO_OWNER} means that slot currently has no player locked to it.
 * Each owner's frame color is computed client-side from their UUID (see
 * {@code com.pantheon.client.HotbarColors}), so it never needs to be sent
 * over the wire.
 */
public record HotbarOwnersPayload(List<UUID> owners) implements CustomPacketPayload {
	public static final int SLOT_COUNT = 9;
	/** The one hotbar slot two-hand mode leaves selectable - the center slot, not slot 0, so it sits under the crosshair rather than the far left edge. */
	public static final int TWO_HAND_ACTIVE_SLOT = SLOT_COUNT / 2;
	public static final UUID NO_OWNER = new UUID(0L, 0L);

	public static final CustomPacketPayload.Type<HotbarOwnersPayload> TYPE = new CustomPacketPayload.Type<>(PantheonMod.id("hotbar_owners"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HotbarOwnersPayload> CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(SLOT_COUNT)),
		HotbarOwnersPayload::owners,
		HotbarOwnersPayload::new
	);

	@Override
	public CustomPacketPayload.Type<HotbarOwnersPayload> type() {
		return TYPE;
	}
}
