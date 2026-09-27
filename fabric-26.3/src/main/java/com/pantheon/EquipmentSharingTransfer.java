package com.pantheon;

import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * {@link com.pantheon.mixin.EquipmentSharingMixin} redirects armor/offhand
 * {@code get}/{@code set} to {@link Team#equipment} whenever
 * {@link PantheonConfig#isEquipmentSlotShared} says so, purely by switching
 * where those calls point - it never moves any item that's currently sitting
 * in whichever storage is about to stop being read. Left alone that means:
 *
 * <ul>
 *   <li>Turning sharing <b>on</b> for a slot: everyone's own local item in it
 *       vanishes from view (the redirect now points at the team's, initially
 *       empty, slot instead) - not lost forever, since it's still physically
 *       sitting in the entity's real equipment map, but completely inert and
 *       unreachable through any normal means until sharing is switched back
 *       off, and never actually shared with the team at all.</li>
 *   <li>Turning sharing <b>off</b>: the team's shared item in that slot stops
 *       being reachable by anyone (every player's own local, likely empty,
 *       slot takes back over) and just sits inert in {@link Team#equipment}.</li>
 * </ul>
 *
 * This has to run <em>before</em> {@link PantheonConfig#applyAndSave} takes
 * effect, while the old config is still active - only then does reading
 * through the normal {@code PlayerEquipment}/{@code Team} accessors still
 * reach the storage that's about to become unreachable.
 */
public final class EquipmentSharingTransfer {
	private EquipmentSharingTransfer() {
	}

	public static void handle(final MinecraftServer server, final PantheonConfig oldConfig, final PantheonConfig newConfig) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			boolean wasShared = oldConfig.isEquipmentSlotShared(slot);
			boolean nowShared = newConfig.isEquipmentSlotShared(slot);
			if (wasShared == nowShared) {
				continue;
			}
			if (nowShared) {
				migrateLocalToShared(server, slot);
			} else {
				migrateSharedToLocal(server, slot);
			}
		}
	}

	/** Every online player's current local item in {@code slot} moves into their (shared) inventory, or drops if it doesn't fit. */
	private static void migrateLocalToShared(final MinecraftServer server, final EquipmentSlot slot) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack local = player.getItemBySlot(slot);
			if (local.isEmpty()) {
				continue;
			}
			player.setItemSlot(slot, ItemStack.EMPTY);
			giveOrDrop(player, local);
			PantheonMod.LOGGER.info("[EquipmentSharingTransfer] {} turned on sharing for {}: moved their local {} to inventory/drop",
				player.getGameProfile().name(), slot, local);
		}
	}

	/**
	 * Each team's current shared item in {@code slot} moves into a free slot
	 * of that same team's shared 36-slot inventory ({@link Team#items}, always
	 * shared regardless of this toggle) instead of sitting unreachable in
	 * {@link Team#equipment}. Landing it there rather than handing it to
	 * whichever member happens to be online right now works even if the whole
	 * team is offline at the moment sharing gets turned off, and keeps it
	 * team property rather than arbitrarily gifting it to one member.
	 */
	private static void migrateSharedToLocal(final MinecraftServer server, final EquipmentSlot slot) {
		for (Team team : TeamManager.allTeams()) {
			ItemStack shared = team.equipment.get(slot);
			if (shared == null || shared.isEmpty()) {
				continue;
			}
			team.equipment.put(slot, ItemStack.EMPTY);

			boolean placed = false;
			for (int i = 0; i < team.items.size(); i++) {
				if (team.items.get(i).isEmpty()) {
					team.items.set(i, shared.copyAndClear());
					placed = true;
					break;
				}
			}
			if (placed) {
				PantheonMod.LOGGER.info("[EquipmentSharingTransfer] turned off sharing for {} on team '{}': moved shared {} into the shared inventory",
					slot, team.name, shared);
				continue;
			}

			// Shared inventory is completely full - fall back to dropping it
			// near an online member; if nobody's online either, there's
			// nowhere left to put it.
			List<ServerPlayer> members = TeamManager.onlineMembersOf(team, server);
			if (members.isEmpty()) {
				PantheonMod.LOGGER.warn("[EquipmentSharingTransfer] turned off sharing for {} on team '{}': shared inventory is full and nobody is online - {} was discarded",
					slot, team.name, shared);
				continue;
			}
			members.get(0).spawnAtLocation((ServerLevel) members.get(0).level(), shared);
			PantheonMod.LOGGER.info("[EquipmentSharingTransfer] turned off sharing for {} on team '{}': shared inventory is full, dropped {} near {}",
				slot, team.name, shared, members.get(0).getGameProfile().name());
		}
	}

	/** {@code Inventory.add} shrinks {@code stack} as it fills slots; whatever's still left over (didn't fully fit) drops at the player's feet instead of being silently discarded. */
	private static void giveOrDrop(final ServerPlayer player, final ItemStack stack) {
		player.getInventory().add(stack);
		if (!stack.isEmpty()) {
			player.spawnAtLocation((ServerLevel) player.level(), stack);
		}
	}
}
