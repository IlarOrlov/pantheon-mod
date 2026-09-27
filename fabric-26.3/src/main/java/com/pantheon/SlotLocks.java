package com.pantheon;

import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;

import com.pantheon.network.HotbarOwnersPayload;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

/**
 * One answer to "is this hotbar slot off-limits to this player right now",
 * usable from code that runs on both sides - {@code Slot#mayPlace}/
 * {@code #mayPickup}, {@code moveItemStackTo}, and every raw
 * {@link Inventory} scan (pickup, pick block, recipe book).
 *
 * <p>Server-side it's {@link HotbarOwnership#isLocked}, the real
 * authority. Client-side the client mod installs {@link #clientPredicate}
 * (backed by the last ownership broadcast it received) so the client's own
 * prediction of a click - and anything client-driven built on top of it,
 * like Mouse Tweaks' drag/scroll/shift handling - skips the exact same
 * slots the server will. Without that, a shift-click the client predicted
 * into a slot the server then refused would flash there and vanish until
 * the next resync.
 */
public final class SlotLocks {
	/**
	 * Installed by the client mod: whether this client-side player's hotbar
	 * slot is locked to them (only ever true for the local player).
	 * {@code null} on a dedicated server, where nothing but
	 * {@link ServerPlayer}s ever reaches this class.
	 */
	public static volatile BiPredicate<Player, Integer> clientPredicate;

	private SlotLocks() {
	}

	/** Whether any lock mode is on at all - cheap early-out for hot paths. */
	private static boolean anyLockMode() {
		PantheonConfig config = PantheonConfig.get();
		return config.enableHotbarOwnership || config.twoHandSlotMode;
	}

	/**
	 * Which of the 9 hotbar slots are locked for {@code player}, or
	 * {@code null} if none are - computed once so a caller scanning all 36
	 * slots doesn't recompute team ownership per slot.
	 */
	public static boolean[] lockedMask(final Player player) {
		boolean[] mask = null;
		if (player instanceof ServerPlayer serverPlayer) {
			if (!anyLockMode()) {
				return null;
			}
			PantheonConfig config = PantheonConfig.get();
			List<UUID> owners = config.enableHotbarOwnership ? HotbarOwnership.currentOwnersFor(serverPlayer) : null;
			for (int i = 0; i < HotbarOwnersPayload.SLOT_COUNT; i++) {
				if (HotbarOwnership.isLocked(i, owners, serverPlayer, config)) {
					if (mask == null) {
						mask = new boolean[HotbarOwnersPayload.SLOT_COUNT];
					}
					mask[i] = true;
				}
			}
			return mask;
		}
		BiPredicate<Player, Integer> client = clientPredicate;
		if (client == null || player == null || !player.level().isClientSide()) {
			return null;
		}
		for (int i = 0; i < HotbarOwnersPayload.SLOT_COUNT; i++) {
			if (client.test(player, i)) {
				if (mask == null) {
					mask = new boolean[HotbarOwnersPayload.SLOT_COUNT];
				}
				mask[i] = true;
			}
		}
		return mask;
	}

	/** Whether {@code index} (an {@link Inventory} index, 0-35) is locked under {@code mask} from {@link #lockedMask}. */
	public static boolean isLocked(final boolean[] mask, final int index) {
		return mask != null && index >= 0 && index < mask.length && mask[index];
	}

	/** Whether {@code player}'s own hotbar slot {@code index} is locked to them. */
	public static boolean isLocked(final Player player, final int index) {
		if (index < 0 || index >= HotbarOwnersPayload.SLOT_COUNT) {
			return false;
		}
		return isLocked(lockedMask(player), index);
	}

	/**
	 * Whether {@code slot} is a player-inventory hotbar slot locked to the
	 * player that inventory belongs to - i.e. the one looking at it, since a
	 * menu only ever shows its own viewer's inventory.
	 */
	public static boolean isLocked(final Slot slot) {
		if (!(slot.container instanceof Inventory inventory)) {
			return false;
		}
		int index = slot.getContainerSlot();
		if (index < 0 || index >= HotbarOwnersPayload.SLOT_COUNT) {
			return false;
		}
		return isLocked(inventory.player, index);
	}
}
