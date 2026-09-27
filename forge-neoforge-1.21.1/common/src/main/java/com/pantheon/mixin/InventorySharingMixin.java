package com.pantheon.mixin;

import java.util.List;

import com.google.common.collect.ImmutableList;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pantheon.PantheonInventoryAccess;
import com.pantheon.TeamManager;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Every ServerPlayer's Inventory is wired to point at their resolved team's
 * backing item list (see {@link TeamManager}), so putting an item in slot N
 * as one player puts it there for every other member of that team. Armor and
 * offhand are separate lists on the same Inventory, pointed at the team's own
 * lists only while {@link com.pantheon.PantheonConfig#isEquipmentSlotShared}
 * says so, and at the player's own lists otherwise. The "selected" hotbar
 * index stays per-instance (each player can still aim a different one of the
 * 9 shared slots as their own held item).
 *
 * <p>The actual pointing happens once the owning {@code ServerPlayer} is
 * fully constructed ({@link ServerPlayerInventorySharingMixin}) - the
 * Inventory itself is built before the player even has its real UUID, so its
 * team can't be resolved yet here.
 */
@Mixin(Inventory.class)
public abstract class InventorySharingMixin implements PantheonInventoryAccess {
	private static final String SLOT_KEY = "Slot";

	@Shadow
	@Final
	public Player player;

	@Shadow
	@Final
	@Mutable
	public NonNullList<ItemStack> items;

	@Shadow
	@Final
	@Mutable
	public NonNullList<ItemStack> armor;

	@Shadow
	@Final
	@Mutable
	public NonNullList<ItemStack> offhand;

	@Shadow
	@Final
	@Mutable
	private List<NonNullList<ItemStack>> compartments;

	/** This player's own armor/offhand, used whenever that slot type isn't shared. */
	@Unique
	private NonNullList<ItemStack> pantheon$ownArmor;

	@Unique
	private NonNullList<ItemStack> pantheon$ownOffhand;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void pantheon$rememberOwnEquipment(final Player player, final CallbackInfo ci) {
		this.pantheon$ownArmor = this.armor;
		this.pantheon$ownOffhand = this.offhand;
	}

	@Override
	public void pantheon$share(final NonNullList<ItemStack> items, final NonNullList<ItemStack> armor, final NonNullList<ItemStack> offhand) {
		this.items = items;
		this.armor = armor != null ? armor : this.pantheon$ownArmor;
		this.offhand = offhand != null ? offhand : this.pantheon$ownOffhand;
		// Nearly every Inventory method walks this list rather than the fields.
		this.compartments = ImmutableList.of(this.items, this.armor, this.offhand);
	}

	/**
	 * {@code load()} starts by clearing every list before repopulating them
	 * from the joining player's own saved data - for a ServerPlayer that would
	 * wipe the *shared* lists (everyone on their team's items, and armor/offhand
	 * if shared) down to just whatever this one player personally had saved
	 * from their last session. The shared contents persist with the world
	 * instead (see {@link com.pantheon.TeamContentsSavedData}) and must never
	 * be touched by a single player's save file, so for ServerPlayers this only
	 * loads the player's own armor/offhand, into their own lists, and leaves
	 * whatever the team currently has alone.
	 */
	@Inject(method = "load", at = @At("HEAD"), cancellable = true)
	private void pantheon$loadOnlyOwnEquipment(final ListTag list, final CallbackInfo ci) {
		if (!(this.player instanceof ServerPlayer)) {
			return;
		}
		ci.cancel();
		this.pantheon$ownArmor.clear();
		this.pantheon$ownOffhand.clear();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i);
			int slot = entry.getByte(SLOT_KEY) & 255;
			if (slot >= 100 && slot < this.pantheon$ownArmor.size() + 100) {
				this.pantheon$ownArmor.set(slot - 100, ItemStack.parse(this.player.registryAccess(), entry).orElse(ItemStack.EMPTY));
			} else if (slot >= 150 && slot < this.pantheon$ownOffhand.size() + 150) {
				this.pantheon$ownOffhand.set(slot - 150, ItemStack.parse(this.player.registryAccess(), entry).orElse(ItemStack.EMPTY));
			}
		}
	}

	/**
	 * The counterpart of {@link #pantheon$loadOnlyOwnEquipment}: a player's
	 * file gets their own armor/offhand, never the team's shared ones - which
	 * would otherwise be loaded back as a personal copy on their next join.
	 */
	@Inject(method = "save", at = @At("HEAD"), cancellable = true)
	private void pantheon$saveOwnEquipment(final ListTag list, final CallbackInfoReturnable<ListTag> cir) {
		if (!(this.player instanceof ServerPlayer)) {
			return;
		}
		pantheon$saveList(list, this.items, 0);
		pantheon$saveList(list, this.pantheon$ownArmor, 100);
		pantheon$saveList(list, this.pantheon$ownOffhand, 150);
		cir.setReturnValue(list);
	}

	@Unique
	private void pantheon$saveList(final ListTag list, final NonNullList<ItemStack> stacks, final int slotOffset) {
		for (int i = 0; i < stacks.size(); i++) {
			ItemStack stack = stacks.get(i);
			if (!stack.isEmpty()) {
				CompoundTag entry = new CompoundTag();
				entry.putByte(SLOT_KEY, (byte) (i + slotOffset));
				list.add(stack.save(this.player.registryAccess(), entry));
			}
		}
	}
}
