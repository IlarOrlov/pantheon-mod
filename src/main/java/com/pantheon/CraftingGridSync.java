package com.pantheon;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * When the 2x2 crafting grid is shared ({@link com.pantheon.mixin.CraftingGridSharingMixin}
 * points every player's grid at the same backing list), a change one player
 * makes to it only recomputes *that player's own* recipe-result preview slot.
 * This forces every other online player's preview to recompute too, every
 * tick, so it never shows a stale/incorrect result.
 */
public final class CraftingGridSync {
	private CraftingGridSync() {
	}

	public static void tick(final MinecraftServer server) {
		if (!PantheonConfig.get().syncCraftingGrid) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.inventoryMenu.slotsChanged(player.inventoryMenu.getCraftSlots());
		}
	}
}
