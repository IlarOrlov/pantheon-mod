package com.pantheon.mixin;

import java.util.List;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pantheon.HotbarOwnership;
import com.pantheon.PantheonConfig;
import com.pantheon.Team;
import com.pantheon.TeamManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;

/**
 * "Pick block" (middle-click a block or entity to grab a matching item, or
 * conjure a fresh one in Creative) writes straight into whichever hotbar
 * slot {@code Inventory#getSuitableHotbarSlot} picks - the first empty one
 * starting from whatever's currently selected, wrapping across all 9 -
 * through {@code Inventory#addAndPickItem}/{@code #pickSlot}, neither of
 * which goes anywhere near {@code AbstractContainerMenu#clicked}.
 * {@link HotbarLockMixin}'s protection (built entirely around that one
 * method) never sees this at all, so a blanked two-hand-mode slot -
 * typically empty, exactly what makes a slot "suitable" - was free to be
 * picked into like any other, items and all.
 *
 * <p>Same snapshot-and-revert backstop as {@code HotbarLockMixin}'s second
 * layer, wrapping this entirely different vanilla entry point instead: the
 * hotbar and the player's selected slot (also moved as a side effect of
 * both vanilla methods above) are captured before {@code tryPickItem} runs,
 * and rolled back - selection included - if a locked slot ended up changed.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class HotbarPickItemLockMixin {
	@Shadow
	public ServerPlayer player;

	@Unique
	private ItemStack[] pantheon$before;

	@Unique
	private int pantheon$beforeSelectedSlot;

	@Inject(method = "tryPickItem", at = @At("HEAD"))
	private void pantheon$capture(final ItemStack stack, final CallbackInfo ci) {
		this.pantheon$before = null;

		PantheonConfig config = PantheonConfig.get();
		if (!config.enableHotbarOwnership && !config.twoHandSlotMode) {
			return;
		}

		Team team = TeamManager.teamOf(this.player);
		// The whole 36-slot inventory, not just the hotbar: pickSlot's
		// source (an existing matching stack) is just as often one of the
		// other 27 slots, and restoring only the hotbar destination after
		// vanilla already emptied that source would lose the item outright.
		ItemStack[] snapshot = new ItemStack[team.items.size()];
		for (int i = 0; i < snapshot.length; i++) {
			snapshot[i] = team.items.get(i).copy();
		}
		this.pantheon$before = snapshot;
		this.pantheon$beforeSelectedSlot = this.player.getInventory().getSelectedSlot();
	}

	@Inject(method = "tryPickItem", at = @At("RETURN"))
	private void pantheon$revert(final ItemStack stack, final CallbackInfo ci) {
		ItemStack[] before = this.pantheon$before;
		this.pantheon$before = null;
		if (before == null) {
			return;
		}

		PantheonConfig config = PantheonConfig.get();
		List<UUID> owners = config.enableHotbarOwnership ? HotbarOwnership.currentOwnersFor(this.player) : null;
		Team team = TeamManager.teamOf(this.player);

		boolean violated = false;
		for (int i = 0; i < before.length; i++) {
			if (HotbarOwnership.isLocked(i, owners, this.player, config) && !ItemStack.matches(before[i], team.items.get(i))) {
				violated = true;
				break;
			}
		}

		if (!violated) {
			return;
		}

		for (int i = 0; i < before.length; i++) {
			team.items.set(i, before[i]);
		}
		this.player.getInventory().setSelectedSlot(this.pantheon$beforeSelectedSlot);
		this.player.inventoryMenu.broadcastFullState();
	}
}
