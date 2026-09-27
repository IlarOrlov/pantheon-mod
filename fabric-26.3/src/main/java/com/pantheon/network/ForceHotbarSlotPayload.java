package com.pantheon.network;

import com.pantheon.PantheonMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent to a player when the server has to move their selected hotbar slot
 * out from under them - on join or respawn, if the slot they came back with
 * collides with an online teammate's ({@link com.pantheon.HotbarOwnership#resolveSlotConflict}).
 * Selecting a hotbar slot is normally entirely client-driven (the server
 * just trusts whatever the client last reported), so without this the
 * client's own view of its selected slot would never learn about a
 * server-forced move - showing its lock frame on the slot it thinks it's
 * still on, alongside the server's (correct) broadcast of the slot it
 * actually got moved to.
 */
public record ForceHotbarSlotPayload(int slot) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ForceHotbarSlotPayload> TYPE = new CustomPacketPayload.Type<>(PantheonMod.id("force_hotbar_slot"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ForceHotbarSlotPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, ForceHotbarSlotPayload::slot,
		ForceHotbarSlotPayload::new
	);

	@Override
	public CustomPacketPayload.Type<ForceHotbarSlotPayload> type() {
		return TYPE;
	}
}
