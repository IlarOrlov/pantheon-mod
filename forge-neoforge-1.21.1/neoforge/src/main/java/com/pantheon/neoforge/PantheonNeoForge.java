package com.pantheon.neoforge;

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

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge entrypoint (both sides): forwards NeoForge's events to the shared
 * hooks in {@link PantheonMod}. The client-only half is {@link PantheonNeoForgeClient}.
 */
@Mod(PantheonMod.MOD_ID)
public final class PantheonNeoForge {
	public PantheonNeoForge(final IEventBus modBus) {
		PantheonMod.init();

		modBus.addListener(PantheonNeoForge::registerPayloads);

		IEventBus bus = NeoForge.EVENT_BUS;
		bus.addListener((ServerStartingEvent event) -> PantheonMod.onServerStarting(event.getServer()));
		bus.addListener((ServerTickEvent.Pre event) -> PantheonMod.onServerTickStart(event.getServer()));
		bus.addListener((ServerTickEvent.Post event) -> PantheonMod.onServerTickEnd(event.getServer()));
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
	}

	/**
	 * Optional, so a vanilla client can still join (the inventory sharing
	 * itself is all server-side) and a Pantheon client can still join a
	 * vanilla server. Client-bound handlers only ever run on a client, so the
	 * client classes they call are never loaded on a dedicated server.
	 */
	private static void registerPayloads(final RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1").optional();

		registrar.playToClient(HotbarOwnersPayload.TYPE, HotbarOwnersPayload.CODEC,
			(payload, context) -> PantheonModClient.onHotbarOwners(payload));
		registrar.playToClient(SyncConfigPayload.TYPE, SyncConfigPayload.CODEC,
			(payload, context) -> PantheonModClient.onSyncConfig(payload));
		registrar.playToClient(ForceHotbarSlotPayload.TYPE, ForceHotbarSlotPayload.CODEC,
			(payload, context) -> PantheonModClient.onForceHotbarSlot(payload));
		registrar.playToClient(LocationMarkPayload.TYPE, LocationMarkPayload.CODEC,
			(payload, context) -> PantheonModClient.onLocationMark(payload));

		registrar.playToServer(UpdateConfigPayload.TYPE, UpdateConfigPayload.CODEC, (payload, context) -> {
			ServerPlayer player = (ServerPlayer) context.player();
			PantheonNetworking.handleUpdateConfig(player.server, player, payload);
		});
		registrar.playToServer(RequestSlotPayload.TYPE, RequestSlotPayload.CODEC, (payload, context) -> {
			ServerPlayer player = (ServerPlayer) context.player();
			PantheonNetworking.handleRequestSlot(player.server, player, payload);
		});
		registrar.playToServer(PlaceLocationMarkPayload.TYPE, PlaceLocationMarkPayload.CODEC, (payload, context) -> {
			ServerPlayer player = (ServerPlayer) context.player();
			PantheonNetworking.handlePlaceLocationMark(player.server, player, payload);
		});
	}
}
