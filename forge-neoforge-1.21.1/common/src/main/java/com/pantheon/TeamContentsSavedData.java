package com.pantheon;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-save persistence for every {@link Team}'s shared items and shared
 * armor/offhand, stored alongside vanilla's own saved data
 * ({@code <world>/data/pantheon_team_contents.dat}). Vanilla writes a
 * player's items into that player's own file - which
 * {@link com.pantheon.mixin.InventorySharingMixin} deliberately skips on
 * load, since one player's file must never overwrite the whole team's pool -
 * so the pool has to be saved somewhere that belongs to the world instead.
 *
 * <p>{@link #save} encodes straight from the live {@link TeamManager} state,
 * so all {@link TeamManager} has to do is flag this dirty before each world
 * save ({@link TeamManager#markDirty}). Loading produces a snapshot that
 * {@link TeamManager#load} copies into the freshly rebuilt teams.
 */
public final class TeamContentsSavedData extends SavedData {
	public static final String NAME = "pantheon_team_contents";

	/** No data fixer: nothing vanilla knows about is stored in a shape it could upgrade. */
	public static final SavedData.Factory<TeamContentsSavedData> FACTORY = new SavedData.Factory<>(
		TeamContentsSavedData::new,
		TeamContentsSavedData::load,
		null
	);

	private static final String TEAMS_KEY = "teams";
	private static final String ITEMS_KEY = "items";
	private static final String EQUIPMENT_KEY = "equipment";
	private static final String SLOT_KEY = "Slot";

	/** One team's persisted contents. */
	public record TeamContents(NonNullList<ItemStack> items, Map<EquipmentSlot, ItemStack> equipment) {
		public static TeamContents of(final Team team) {
			NonNullList<ItemStack> items = NonNullList.withSize(team.items.size(), ItemStack.EMPTY);
			for (int slot = 0; slot < team.items.size(); slot++) {
				items.set(slot, team.items.get(slot).copy());
			}
			Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ItemStack stack = team.equipment(slot);
				if (!stack.isEmpty()) {
					equipment.put(slot, stack.copy());
				}
			}
			return new TeamContents(items, equipment);
		}

		public void applyTo(final Team team) {
			team.items.clear();
			for (int slot = 0; slot < team.items.size() && slot < this.items.size(); slot++) {
				team.items.set(slot, this.items.get(slot));
			}
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				team.setEquipment(slot, this.equipment.getOrDefault(slot, ItemStack.EMPTY));
			}
		}

		/** Empty slots are simply absent. */
		CompoundTag save(final HolderLookup.Provider registries) {
			CompoundTag tag = new CompoundTag();
			ListTag items = new ListTag();
			for (int slot = 0; slot < this.items.size(); slot++) {
				ItemStack stack = this.items.get(slot);
				if (!stack.isEmpty()) {
					CompoundTag entry = new CompoundTag();
					entry.putByte(SLOT_KEY, (byte) slot);
					items.add(stack.save(registries, entry));
				}
			}
			tag.put(ITEMS_KEY, items);
			CompoundTag equipment = new CompoundTag();
			for (Map.Entry<EquipmentSlot, ItemStack> entry : this.equipment.entrySet()) {
				if (!entry.getValue().isEmpty()) {
					equipment.put(entry.getKey().getName(), entry.getValue().save(registries));
				}
			}
			tag.put(EQUIPMENT_KEY, equipment);
			return tag;
		}

		static TeamContents load(final CompoundTag tag, final HolderLookup.Provider registries) {
			NonNullList<ItemStack> items = NonNullList.withSize(36, ItemStack.EMPTY);
			ListTag itemList = tag.getList(ITEMS_KEY, Tag.TAG_COMPOUND);
			for (int i = 0; i < itemList.size(); i++) {
				CompoundTag entry = itemList.getCompound(i);
				int slot = entry.getByte(SLOT_KEY) & 255;
				if (slot < items.size()) {
					items.set(slot, ItemStack.parseOptional(registries, entry));
				}
			}
			Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
			CompoundTag equipmentTag = tag.getCompound(EQUIPMENT_KEY);
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (equipmentTag.contains(slot.getName(), Tag.TAG_COMPOUND)) {
					equipment.put(slot, ItemStack.parseOptional(registries, equipmentTag.getCompound(slot.getName())));
				}
			}
			return new TeamContents(items, equipment);
		}
	}

	/** What was on disk when this was loaded; empty for a fresh world. Only meaningful right after load. */
	private final Map<String, TeamContents> loaded;

	public TeamContentsSavedData() {
		this(Map.of());
	}

	private TeamContentsSavedData(final Map<String, TeamContents> loaded) {
		this.loaded = loaded;
	}

	public Map<String, TeamContents> loaded() {
		return this.loaded;
	}

	private static TeamContentsSavedData load(final CompoundTag tag, final HolderLookup.Provider registries) {
		Map<String, TeamContents> loaded = new LinkedHashMap<>();
		CompoundTag teams = tag.getCompound(TEAMS_KEY);
		for (String name : teams.getAllKeys()) {
			loaded.put(name, TeamContents.load(teams.getCompound(name), registries));
		}
		return new TeamContentsSavedData(loaded);
	}

	/** Writes the current live contents of every team, as they are at save time. */
	@Override
	public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries) {
		CompoundTag teams = new CompoundTag();
		for (Team team : TeamManager.allTeams()) {
			teams.put(team.name, TeamContents.of(team).save(registries));
		}
		tag.put(TEAMS_KEY, teams);
		return tag;
	}
}
