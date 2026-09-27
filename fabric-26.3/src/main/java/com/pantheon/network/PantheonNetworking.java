package com.pantheon.network;

import com.pantheon.EquipmentSharingTransfer;
import com.pantheon.FunnyMessages;
import com.pantheon.HotbarOwnership;
import com.pantheon.LocationMarks;
import com.pantheon.PantheonConfig;
import com.pantheon.PantheonMod;
import com.pantheon.Team;
import com.pantheon.TeamManager;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PantheonNetworking {
	/** Minimum gap between one player's slot-request pings - just enough that holding the key down can't machine-gun the sound. */
	private static final int PING_COOLDOWN_TICKS = 10;

	/** How long the pinged owner's request message stays on screen - well past vanilla's ~3s action bar, so it isn't missed mid-fight. */
	private static final int PING_MESSAGE_TICKS = 160;

	/** Vanilla keeps an action-bar message up this long before it fades; re-sent a bit sooner than that to keep it up without a flicker. */
	private static final int OVERLAY_VANILLA_TICKS = 60;
	private static final int OVERLAY_RESEND_TICKS = 40;

	/** A pinged owner's request message, kept on screen by re-sending it until {@code untilTick}. */
	private record HeldOverlay(Component message, long untilTick, long nextSendTick) {
	}

	private static final Map<UUID, HeldOverlay> HELD_OVERLAYS = new HashMap<>();

	private PantheonNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(HotbarOwnersPayload.TYPE, HotbarOwnersPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SyncConfigPayload.TYPE, SyncConfigPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ForceHotbarSlotPayload.TYPE, ForceHotbarSlotPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LocationMarkPayload.TYPE, LocationMarkPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UpdateConfigPayload.TYPE, UpdateConfigPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestSlotPayload.TYPE, RequestSlotPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PlaceLocationMarkPayload.TYPE, PlaceLocationMarkPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(UpdateConfigPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			MinecraftServer server = context.server();
			server.execute(() -> handleUpdateConfig(server, player, payload));
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestSlotPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			MinecraftServer server = context.server();
			server.execute(() -> handleRequestSlot(server, player, payload));
		});

		ServerPlayNetworking.registerGlobalReceiver(PlaceLocationMarkPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			MinecraftServer server = context.server();
			server.execute(() -> LocationMarks.place(server, player, payload.pos()));
		});
	}

	private static void handleRequestSlot(final MinecraftServer server, final ServerPlayer requester, final RequestSlotPayload payload) {
		if (!PantheonConfig.get().enableHotbarOwnership) {
			return;
		}
		int slot = payload.slot();
		if (slot < 0 || slot >= HotbarOwnersPayload.SLOT_COUNT) {
			return;
		}

		List<UUID> owners = HotbarOwnership.currentOwnersFor(requester);
		UUID ownerId = owners.get(slot);
		if (ownerId.equals(HotbarOwnersPayload.NO_OWNER) || ownerId.equals(requester.getUUID())) {
			return;
		}

		Team team = TeamManager.teamOf(requester);
		long now = server.getTickCount();
		Long lastPing = team.lastPingTick.get(requester.getUUID());
		if (lastPing != null && now - lastPing < PING_COOLDOWN_TICKS) {
			return;
		}
		team.lastPingTick.put(requester.getUUID(), now);

		ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
		// currentOwnersFor already only returns owners among the requester's own
		// teammates, but re-checking here too means this can never regress into
		// cross-team pinging even if that computation is ever refactored.
		if (owner == null || TeamManager.teamOf(owner) != team) {
			return;
		}

		Component message = FunnyMessages.randomSlotRequest(requester.getGameProfile().name());
		owner.sendOverlayMessage(message);
		HELD_OVERLAYS.put(owner.getUUID(), new HeldOverlay(message, now + PING_MESSAGE_TICKS, now + OVERLAY_RESEND_TICKS));
		playPingSound(owner);
		requester.sendOverlayMessage(Component.literal("Poked " + owner.getGameProfile().name() + " about that slot."));
	}

	/**
	 * Keeps each pinged owner's request message up for {@link #PING_MESSAGE_TICKS}
	 * by re-sending it before vanilla's own action-bar timer runs out. Called
	 * every server tick.
	 */
	public static void tickHeldOverlays(final MinecraftServer server) {
		if (HELD_OVERLAYS.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		Iterator<Map.Entry<UUID, HeldOverlay>> it = HELD_OVERLAYS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, HeldOverlay> entry = it.next();
			HeldOverlay held = entry.getValue();
			// The last re-send has to happen while there's still a full
			// vanilla display's worth of time left, or it would overshoot.
			if (now + OVERLAY_VANILLA_TICKS > held.untilTick()) {
				it.remove();
				continue;
			}
			if (now < held.nextSendTick()) {
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null) {
				it.remove();
				continue;
			}
			owner.sendOverlayMessage(held.message());
			entry.setValue(new HeldOverlay(held.message(), held.untilTick(), now + OVERLAY_RESEND_TICKS));
		}
	}

	/** Forget every held message - the server is going away (or a new one is starting in the same JVM). */
	public static void clearHeldOverlays() {
		HELD_OVERLAYS.clear();
	}

	/**
	 * A short "ding" only the pinged owner hears, sent as a targeted sound
	 * packet (not {@code Level.playSound}, which would let nearby bystanders
	 * hear a random ding meant for someone else). Built from a vanilla note
	 * block sound rather than any real-world recording - the mod can't ship
	 * copyrighted audio.
	 */
	private static void playPingSound(final ServerPlayer owner) {
		owner.connection.send(new ClientboundSoundPacket(
			SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS,
			owner.getX(), owner.getY(), owner.getZ(),
			1.0f, 2.0f, owner.level().getRandom().nextLong()
		));
	}

	private static void handleUpdateConfig(final MinecraftServer server, final ServerPlayer player, final UpdateConfigPayload payload) {
		if (!canConfigure(server, player)) {
			player.sendSystemMessage(Component.literal("You don't have permission to change Pantheon's settings."));
			return;
		}

		PantheonConfig updated = applyConfigChange(server, payload.toConfig());
		PantheonMod.LOGGER.info("Pantheon config changed by {}: {}", player.getGameProfile().name(), updated);
	}

	/**
	 * The full sequence a config change goes through, regardless of whether
	 * it came from a player's settings screen (via {@link #handleUpdateConfig})
	 * or an op's {@code /pantheon config} command ({@code PantheonCommands}):
	 * apply + persist it, migrate equipment before the old sharing rules stop
	 * applying, push it to every client, migrate whatever two-hand mode is
	 * about to blank out of reach, re-point shared inventories, untangle any
	 * hotbar-ownership pile-up the change caused, and re-broadcast ownership.
	 * Centralized here (rather than duplicated at both call sites) so they
	 * can't quietly drift apart on which of these steps they remember to do -
	 * a future step added to only one of them would silently diverge behavior
	 * between the two paths.
	 */
	public static PantheonConfig applyConfigChange(final MinecraftServer server, final PantheonConfig newConfig) {
		PantheonConfig oldConfig = PantheonConfig.get();
		EquipmentSharingTransfer.handle(server, oldConfig, newConfig);
		PantheonConfig updated = PantheonConfig.applyAndSave(newConfig);

		SyncConfigPayload syncPayload = SyncConfigPayload.fromConfig(updated);
		for (ServerPlayer online : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(online, syncPayload);
		}

		if (!oldConfig.twoHandSlotMode && updated.twoHandSlotMode) {
			// Before reassignAllOnline's broadcastFullState below, so everyone
			// sees the post-migration contents immediately rather than the
			// about-to-be-relocated items still sitting in their old spots.
			HotbarOwnership.migrateBlankedSlotsOnEnable(server);
		}
		TeamManager.reassignAllOnline(server);
		if (!oldConfig.enableHotbarOwnership && updated.enableHotbarOwnership) {
			HotbarOwnership.spreadOutOnEnable(server);
		}
		HotbarOwnership.broadcast(server);
		return updated;
	}

	/** The singleplayer host, or a server operator, may change Pantheon's shared settings. */
	public static boolean canConfigure(final MinecraftServer server, final ServerPlayer player) {
		NameAndId nameAndId = player.nameAndId();
		return server.isSingleplayerOwner(nameAndId) || server.getPlayerList().isOp(nameAndId);
	}

	public static void sendConfigTo(final ServerPlayer player) {
		ServerPlayNetworking.send(player, SyncConfigPayload.fromConfig(PantheonConfig.get()));
	}
}
