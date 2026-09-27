package com.pantheon.network;

import com.pantheon.PantheonConfig;
import com.pantheon.PantheonMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent from server to every client on join, and again whenever an op or the
 * singleplayer host changes the settings via {@link UpdateConfigPayload}, so
 * every client's settings screen and hotbar-lock rendering agree with the
 * server's actual behavior.
 */
public record SyncConfigPayload(
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
	public static final CustomPacketPayload.Type<SyncConfigPayload> TYPE = new CustomPacketPayload.Type<>(PantheonMod.id("sync_config"));

	// Written by hand: 1.21.1's StreamCodec.composite tops out at six fields.
	public static final StreamCodec<RegistryFriendlyByteBuf, SyncConfigPayload> CODEC = StreamCodec.of(
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
		buf -> new SyncConfigPayload(
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

	public static SyncConfigPayload fromConfig(final PantheonConfig config) {
		return new SyncConfigPayload(
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
	public CustomPacketPayload.Type<SyncConfigPayload> type() {
		return TYPE;
	}
}
