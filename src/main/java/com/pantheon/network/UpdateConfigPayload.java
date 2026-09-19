package com.pantheon.network;

import com.pantheon.PantheonConfig;
import com.pantheon.PantheonMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
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
	boolean twoHandSlotMode
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<UpdateConfigPayload> TYPE = new CustomPacketPayload.Type<>(PantheonMod.id("update_config"));

	public static final StreamCodec<RegistryFriendlyByteBuf, UpdateConfigPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, UpdateConfigPayload::syncArmor,
		ByteBufCodecs.BOOL, UpdateConfigPayload::syncOffhand,
		ByteBufCodecs.BOOL, UpdateConfigPayload::enableHotbarOwnership,
		ByteBufCodecs.BOOL, UpdateConfigPayload::syncHealth,
		ByteBufCodecs.BOOL, UpdateConfigPayload::syncHunger,
		ByteBufCodecs.BOOL, UpdateConfigPayload::syncExperience,
		ByteBufCodecs.BOOL, UpdateConfigPayload::syncEffects,
		ByteBufCodecs.BOOL, UpdateConfigPayload::teamsEnabled,
		ByteBufCodecs.BOOL, UpdateConfigPayload::crudeHumor,
		ByteBufCodecs.BOOL, UpdateConfigPayload::twoHandSlotMode,
		UpdateConfigPayload::new
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
	public CustomPacketPayload.Type<UpdateConfigPayload> type() {
		return TYPE;
	}
}
