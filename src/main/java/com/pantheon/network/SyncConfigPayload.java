package com.pantheon.network;

import com.pantheon.PantheonConfig;
import com.pantheon.PantheonMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
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
	boolean teamsEnabled
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SyncConfigPayload> TYPE = new CustomPacketPayload.Type<>(PantheonMod.id("sync_config"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SyncConfigPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, SyncConfigPayload::syncArmor,
		ByteBufCodecs.BOOL, SyncConfigPayload::syncOffhand,
		ByteBufCodecs.BOOL, SyncConfigPayload::enableHotbarOwnership,
		ByteBufCodecs.BOOL, SyncConfigPayload::syncHealth,
		ByteBufCodecs.BOOL, SyncConfigPayload::syncHunger,
		ByteBufCodecs.BOOL, SyncConfigPayload::teamsEnabled,
		SyncConfigPayload::new
	);

	public static SyncConfigPayload fromConfig(final PantheonConfig config) {
		return new SyncConfigPayload(
			config.syncArmor,
			config.syncOffhand,
			config.enableHotbarOwnership,
			config.syncHealth,
			config.syncHunger,
			config.teamsEnabled
		);
	}

	public PantheonConfig toConfig() {
		PantheonConfig config = new PantheonConfig();
		config.syncArmor = this.syncArmor;
		config.syncOffhand = this.syncOffhand;
		config.enableHotbarOwnership = this.enableHotbarOwnership;
		config.syncHealth = this.syncHealth;
		config.syncHunger = this.syncHunger;
		config.teamsEnabled = this.teamsEnabled;
		return config;
	}

	@Override
	public CustomPacketPayload.Type<SyncConfigPayload> type() {
		return TYPE;
	}
}
