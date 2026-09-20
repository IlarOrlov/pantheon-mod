package com.pantheon.network;

import com.pantheon.EquipmentSharingTransfer;
import com.pantheon.FunnyMessages;
import com.pantheon.HotbarOwnership;
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

import java.util.List;
import java.util.UUID;

public final class PantheonNetworking {
	/** Minimum gap between one player's slot-request pings, so it can't be spammed. */
	private static final int PING_COOLDOWN_TICKS = 60;

	private PantheonNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(HotbarOwnersPayload.TYPE, HotbarOwnersPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SyncConfigPayload.TYPE, SyncConfigPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ForceHotbarSlotPayload.TYPE, ForceHotbarSlotPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UpdateConfigPayload.TYPE, UpdateConfigPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestSlotPayload.TYPE, RequestSlotPayload.CODEC);

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

		owner.sendOverlayMessage(FunnyMessages.randomSlotRequest(requester.getGameProfile().name()));
		playPingSound(owner);
		requester.sendOverlayMessage(Component.literal("Poked " + owner.getGameProfile().name() + " about that slot."));
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
