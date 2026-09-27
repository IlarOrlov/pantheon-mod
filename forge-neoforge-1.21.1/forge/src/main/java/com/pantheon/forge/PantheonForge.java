package com.pantheon.forge;

import com.pantheon.PantheonCommands;
import com.pantheon.PantheonMod;
import com.pantheon.client.PantheonModClient;
import com.pantheon.network.ForceHotbarSlotPayload;
import com.pantheon.network.HotbarOwnersPayload;
import com.pantheon.network.LocationMarkPayload;
import com.pantheon.network.PantheonNetworking;
import com.pantheon.network.PlaceLocationMarkPayload;
import com.pantheon.network.RequestSlotPayload;
import com.pantheon.network.SyncConfigPayload;
import com.pantheon.network.UpdateConfigPayload;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;

/**
 * Forge entrypoint (both sides): forwards Forge's events to the shared hooks
 * in {@link PantheonMod}. The client-only half is {@link PantheonForgeClient}.
 */
@Mod(PantheonMod.MOD_ID)
public final class PantheonForge {
	/**
	 * Optional, so a vanilla client can still join (the inventory sharing
	 * itself is all server-side) and a Pantheon client can still join a
	 * vanilla server. Client-bound handlers only ever run on a client, so the
	 * client classes they call are never loaded on a dedicated server.
	 */
	static final Channel<CustomPacketPayload> CHANNEL = ChannelBuilder.named(PantheonMod.id("main"))
		.networkProtocolVersion(1)
		.optional()
		.payloadChannel()
		.play()
		.clientbound()
		.addMain(HotbarOwnersPayload.TYPE, HotbarOwnersPayload.CODEC,
			(payload, context) -> PantheonModClient.onHotbarOwners(payload))
		.addMain(SyncConfigPayload.TYPE, SyncConfigPayload.CODEC,
			(payload, context) -> PantheonModClient.onSyncConfig(payload))
		.addMain(ForceHotbarSlotPayload.TYPE, ForceHotbarSlotPayload.CODEC,
			(payload, context) -> PantheonModClient.onForceHotbarSlot(payload))
		.addMain(LocationMarkPayload.TYPE, LocationMarkPayload.CODEC,
			(payload, context) -> PantheonModClient.onLocationMark(payload))
		.serverbound()
		.addMain(UpdateConfigPayload.TYPE, UpdateConfigPayload.CODEC, (payload, context) -> {
			ServerPlayer player = context.getSender();
			if (player != null) {
				PantheonNetworking.handleUpdateConfig(player.server, player, payload);
			}
		})
		.addMain(RequestSlotPayload.TYPE, RequestSlotPayload.CODEC, (payload, context) -> {
			ServerPlayer player = context.getSender();
			if (player != null) {
				PantheonNetworking.handleRequestSlot(player.server, player, payload);
			}
		})
		.addMain(PlaceLocationMarkPayload.TYPE, PlaceLocationMarkPayload.CODEC, (payload, context) -> {
			ServerPlayer player = context.getSender();
			if (player != null) {
				PantheonNetworking.handlePlaceLocationMark(player.server, player, payload);
			}
		})
		.build();

	public PantheonForge(final FMLJavaModLoadingContext context) {
		PantheonMod.init();

		IEventBus bus = MinecraftForge.EVENT_BUS;
		bus.addListener((ServerStartingEvent event) -> PantheonMod.onServerStarting(event.getServer()));
		bus.addListener((TickEvent.ServerTickEvent.Pre event) -> PantheonMod.onServerTickStart(event.getServer()));
		bus.addListener((TickEvent.ServerTickEvent.Post event) -> PantheonMod.onServerTickEnd(event.getServer()));
		bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				PantheonMod.onPlayerJoin(player.server, player);
			}
		});
		bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				PantheonMod.onPlayerLeave(player.server);
			}
		});
		bus.addListener((RegisterCommandsEvent event) -> PantheonCommands.register(event.getDispatcher()));

		if (FMLEnvironment.dist.isClient()) {
			PantheonForgeClient.init(context.getModEventBus());
		}
	}
}
