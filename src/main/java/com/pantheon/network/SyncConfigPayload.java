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
	boolean syncExperience,
	boolean syncEffects,
	boolean teamsEnabled,
	boolean crudeHumor,
	boolean twoHandSlotMode
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SyncConfigPayload> TYPE = new CustomPacketPayload.Type<>(PantheonMod.id("sync_config"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SyncConfigPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, SyncConfigPayload::syncArmor,
		ByteBufCodecs.BOOL, SyncConfigPayload::syncOffhand,
		ByteBufCodecs.BOOL, SyncConfigPayload::enableHotbarOwnership,
		ByteBufCodecs.BOOL, SyncConfigPayload::syncHealth,
		ByteBufCodecs.BOOL, SyncConfigPayload::syncHunger,
		ByteBufCodecs.BOOL, SyncConfigPayload::syncExperience,
		ByteBufCodecs.BOOL, SyncConfigPayload::syncEffects,
		ByteBufCodecs.BOOL, SyncConfigPayload::teamsEnabled,
		ByteBufCodecs.BOOL, SyncConfigPayload::crudeHumor,
		ByteBufCodecs.BOOL, SyncConfigPayload::twoHandSlotMode,
		SyncConfigPayload::new
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
			config.twoHandSlotMode
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
		return config;
	}

	@Override
	public CustomPacketPayload.Type<SyncConfigPayload> type() {
		return TYPE;
	}
}
