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
import com.pantheon.SharedInventory;
import com.pantheon.PantheonConfig;
import com.pantheon.network.HotbarOwnersPayload;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * Blocks any container interaction - a direct click, shift-click, the
 * swap-hands key, a number key pressed while hovering another slot, the drop
 * key, drag-splitting, all of it - from touching a hotbar slot that
 * {@link HotbarOwnership} currently locks to a different online player.
 *
 * <p>Rather than special-casing each of those input paths (they all ultimately
 * funnel through {@link AbstractContainerMenu#clicked}, but as different
 * combinations of slot/button/{@link ContainerInput}), this snapshots the 9
 * hotbar slots and the cursor stack before the click and reverts the entire
 * click if any slot locked to someone else ends up changed. That covers every
 * path a click can take without needing to enumerate them.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class HotbarLockMixin {
	@Shadow
	public abstract ItemStack getCarried();

	@Shadow
	public abstract void setCarried(ItemStack stack);

	@Shadow
	public abstract void broadcastFullState();

	@Unique
	private ItemStack[] pantheon$before;

	@Unique
	private ItemStack pantheon$beforeCarried;

	@Unique
	private UUID pantheon$clicker;

	@Inject(method = "clicked", at = @At("HEAD"))
	private void pantheon$capture(final int slotId, final int button, final ContainerInput input, final Player player, final CallbackInfo ci) {
		this.pantheon$before = null;

		if (!(player instanceof ServerPlayer serverPlayer) || !PantheonConfig.get().enableHotbarOwnership) {
			return;
		}

		this.pantheon$clicker = serverPlayer.getUUID();

		ItemStack[] snapshot = new ItemStack[HotbarOwnersPayload.SLOT_COUNT];
		for (int i = 0; i < snapshot.length; i++) {
			snapshot[i] = SharedInventory.ITEMS.get(i).copy();
		}
		this.pantheon$before = snapshot;
		this.pantheon$beforeCarried = this.getCarried().copy();
	}

	@Inject(method = "clicked", at = @At("RETURN"))
	private void pantheon$revert(final int slotId, final int button, final ContainerInput input, final Player player, final CallbackInfo ci) {
		ItemStack[] before = this.pantheon$before;
		this.pantheon$before = null;
		if (before == null || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		List<UUID> owners = HotbarOwnership.currentOwners(level.getServer());

		boolean violated = false;
		for (int i = 0; i < before.length; i++) {
			UUID owner = owners.get(i);
			boolean lockedToSomeoneElse = !owner.equals(HotbarOwnersPayload.NO_OWNER) && !owner.equals(this.pantheon$clicker);
			if (lockedToSomeoneElse && !ItemStack.matches(before[i], SharedInventory.ITEMS.get(i))) {
				violated = true;
				break;
			}
		}

		if (!violated) {
			return;
		}

		for (int i = 0; i < before.length; i++) {
			SharedInventory.ITEMS.set(i, before[i]);
		}
		this.setCarried(this.pantheon$beforeCarried);
		this.broadcastFullState();
	}
}
