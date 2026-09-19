package com.pantheon.mixin;

import java.util.List;
import java.util.UUID;

import org.spongepowered.asm.mixin.Final;
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
import com.pantheon.network.HotbarOwnersPayload;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps a hotbar slot untouchable by whoever it's locked against, in two
 * layers:
 *
 * <ol>
 *   <li>If the click is directly on a locked slot - hovering it and pressing
 *   Q (drop), F (swap hands), a number key, or clicking it outright - the
 *   whole click is cancelled before vanilla does anything. This has to
 *   happen <em>before</em>, not after: dropping spawns an item entity in the
 *   world and swap-hands writes into the offhand slot, neither of which a
 *   same-tick revert could undo without leaving a duplicated item behind.</li>
 *   <li>As a backstop for indirect landings vanilla picks internally - e.g.
 *   shift-clicking a stack in from a chest, where the destination hotbar
 *   slot isn't a parameter we can check up front - the 9 hotbar slots and
 *   the cursor are snapshotted before the click and restored if a locked
 *   slot ends up changed anyway.</li>
 * </ol>
 *
 * <p>What counts as "locked" depends on which of two mutually-exclusive
 * modes is on (see {@link PantheonConfig#normalize}): with
 * {@link PantheonConfig#enableHotbarOwnership}, a slot is locked to everyone
 * except whichever online teammate currently owns it ({@link HotbarOwnership});
 * with {@link PantheonConfig#twoHandSlotMode}, every slot but the one active
 * slot ({@link HotbarOwnersPayload#TWO_HAND_ACTIVE_SLOT}) is locked to
 * <em>everyone</em>, including its own "owner" - there's no per-player
 * ownership to arbitrate when only one slot is ever selectable to begin
 * with, so without this the eight slots {@link com.pantheon.client.TwoHandHotbarOverlay}
 * and {@code HotbarLockFrameMixin} merely hide from view would otherwise
 * stay fully clickable dead storage.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class HotbarLockMixin {
	@Shadow
	@Final
	public NonNullList<Slot> slots;

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

	@Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
	private void pantheon$capture(final int slotId, final int button, final ContainerInput input, final Player player, final CallbackInfo ci) {
		this.pantheon$before = null;

		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		PantheonConfig config = PantheonConfig.get();
		if (!config.enableHotbarOwnership && !config.twoHandSlotMode) {
			return;
		}

		// Only meaningful (non-null) under per-slot ownership - two-hand mode
		// locks every non-active slot outright, with no owner to look up.
		List<UUID> owners = config.enableHotbarOwnership ? HotbarOwnership.currentOwnersFor(serverPlayer) : null;

		int hoveredHotbarSlot = this.pantheon$hotbarIndexOf(slotId, serverPlayer);
		boolean touchesLockedSlotDirectly = this.pantheon$isLocked(hoveredHotbarSlot, owners, serverPlayer, config)
			|| (input == ContainerInput.SWAP && this.pantheon$isLocked(button, owners, serverPlayer, config));

		if (touchesLockedSlotDirectly) {
			ci.cancel();
			return;
		}

		Team team = TeamManager.teamOf(serverPlayer);
		ItemStack[] snapshot = new ItemStack[HotbarOwnersPayload.SLOT_COUNT];
		for (int i = 0; i < snapshot.length; i++) {
			snapshot[i] = team.items.get(i).copy();
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

		PantheonConfig config = PantheonConfig.get();
		List<UUID> owners = config.enableHotbarOwnership ? HotbarOwnership.currentOwnersFor(serverPlayer) : null;
		Team team = TeamManager.teamOf(serverPlayer);

		boolean violated = false;
		for (int i = 0; i < before.length; i++) {
			if (this.pantheon$isLocked(i, owners, serverPlayer, config) && !ItemStack.matches(before[i], team.items.get(i))) {
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
		this.setCarried(this.pantheon$beforeCarried);
		this.broadcastFullState();
	}

	/** Maps a menu-local slot id to the shared hotbar index (0-8) it refers to, or -1 if it isn't one. */
	@Unique
	private int pantheon$hotbarIndexOf(final int slotId, final ServerPlayer player) {
		if (slotId < 0 || slotId >= this.slots.size()) {
			return -1;
		}
		Slot slot = this.slots.get(slotId);
		if (slot.container != player.getInventory()) {
			return -1;
		}
		int index = slot.getContainerSlot();
		return (index >= 0 && index < HotbarOwnersPayload.SLOT_COUNT) ? index : -1;
	}

	/**
	 * Whether {@code hotbarIndex} is off-limits to {@code player} right now.
	 * {@code owners} is {@code null} under two-hand mode (both call sites
	 * above only compute it when {@link PantheonConfig#enableHotbarOwnership}
	 * is on), which is the signal to use its everyone-but-the-active-slot
	 * rule instead of per-player ownership.
	 */
	@Unique
	private boolean pantheon$isLocked(final int hotbarIndex, final List<UUID> owners, final ServerPlayer player, final PantheonConfig config) {
		if (hotbarIndex < 0 || hotbarIndex >= HotbarOwnersPayload.SLOT_COUNT) {
			return false;
		}
		if (owners == null) {
			return config.twoHandSlotMode && hotbarIndex != HotbarOwnersPayload.TWO_HAND_ACTIVE_SLOT;
		}
		UUID owner = owners.get(hotbarIndex);
		return !owner.equals(HotbarOwnersPayload.NO_OWNER) && !owner.equals(player.getUUID());
	}
}
