package com.pantheon.network;

import com.pantheon.PantheonConfig;
import com.pantheon.PantheonMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent from a client's Pantheon settings screen to the server, asking it to
 * change the shared settings. The server only honors this from the
 * singleplayer host or a server operator (see the handler registered in
 * {@link PantheonNetworking}); anyone else's request is silently dropped
 * (with a chat message telling them so).
 */
public record UpdateConfigPayload(
	boolean syncArmor,
	boolean syncOffhand,
	boolean enableHotbarOwnership,
	boolean syncHealth,
	boolean syncHunger,
	boolean syncExperience,
	boolean syncEffects,
	boolean teamsEnabled,
	boolean crudeHumor,
	boolean twoHandSlotMode,
	boolean locationMarks,
	int locationMarkLifetimeSeconds
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<UpdateConfigPayload> TYPE = new CustomPacketPayload.Type<>(PantheonMod.id("update_config"));

	// Written by hand: 1.21.1's StreamCodec.composite tops out at six fields.
	public static final StreamCodec<RegistryFriendlyByteBuf, UpdateConfigPayload> CODEC = StreamCodec.of(
		(buf, payload) -> {
			buf.writeBoolean(payload.syncArmor);
			buf.writeBoolean(payload.syncOffhand);
			buf.writeBoolean(payload.enableHotbarOwnership);
			buf.writeBoolean(payload.syncHealth);
			buf.writeBoolean(payload.syncHunger);
			buf.writeBoolean(payload.syncExperience);
			buf.writeBoolean(payload.syncEffects);
			buf.writeBoolean(payload.teamsEnabled);
			buf.writeBoolean(payload.crudeHumor);
			buf.writeBoolean(payload.twoHandSlotMode);
			buf.writeBoolean(payload.locationMarks);
			buf.writeVarInt(payload.locationMarkLifetimeSeconds);
		},
		buf -> new UpdateConfigPayload(
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readVarInt()
		)
	);

	public static UpdateConfigPayload fromConfig(final PantheonConfig config) {
		return new UpdateConfigPayload(
			config.syncArmor,
			config.syncOffhand,
			config.enableHotbarOwnership,
			config.syncHealth,
			config.syncHunger,
			config.syncExperience,
			config.syncEffects,
			config.teamsEnabled,
			config.crudeHumor,
			config.twoHandSlotMode,
			config.locationMarks,
			config.locationMarkLifetimeSeconds
		);
	}

	public PantheonConfig toConfig() {
		PantheonConfig config = new PantheonConfig();
		config.syncArmor = this.syncArmor;
		config.syncOffhand = this.syncOffhand;
		config.enableHotbarOwnership = this.enableHotbarOwnership;
		config.syncHealth = this.syncHealth;
		config.syncHunger = this.syncHunger;
		config.syncExperience = this.syncExperience;
		config.syncEffects = this.syncEffects;
		config.teamsEnabled = this.teamsEnabled;
		config.crudeHumor = this.crudeHumor;
		config.twoHandSlotMode = this.twoHandSlotMode;
		config.locationMarks = this.locationMarks;
		config.locationMarkLifetimeSeconds = this.locationMarkLifetimeSeconds;
		return config;
	}

	@Override
	public CustomPacketPayload.Type<UpdateConfigPayload> type() {
		return TYPE;
	}
}
