package com.pantheon;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World-save persistence for every {@link Team}'s shared items and shared
 * armor/offhand, stored alongside vanilla's own saved data
 * ({@code <world>/data/pantheon_team_contents.dat}). Vanilla writes a
 * player's items into that player's own file - which
 * {@link com.pantheon.mixin.InventorySharingMixin} deliberately skips on
 * load, since one player's file must never overwrite the whole team's pool -
 * so the pool has to be saved somewhere that belongs to the world instead.
 *
 * <p>The codec encodes straight from the live {@link TeamManager} state, so
 * all {@link TeamManager} has to do is flag this dirty before each world
 * save ({@link TeamManager#markDirty}). Decoding produces a snapshot that
 * {@link TeamManager#load} copies into the freshly rebuilt teams.
 */
public final class TeamContentsSavedData extends SavedData {
	/** One team's persisted contents; empty slots are simply absent. */
	public record TeamContents(List<ItemStackWithSlot> items, Map<EquipmentSlot, ItemStack> equipment) {
		public static final Codec<TeamContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ItemStackWithSlot.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(TeamContents::items),
			Codec.unboundedMap(EquipmentSlot.CODEC, ItemStack.CODEC).optionalFieldOf("equipment", Map.of()).forGetter(TeamContents::equipment)
		).apply(instance, TeamContents::new));

		public static TeamContents of(final Team team) {
			List<ItemStackWithSlot> items = new ArrayList<>();
			for (int slot = 0; slot < team.items.size(); slot++) {
				ItemStack stack = team.items.get(slot);
				if (!stack.isEmpty()) {
					items.add(new ItemStackWithSlot(slot, stack.copy()));
				}
			}
			Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
			for (Map.Entry<EquipmentSlot, ItemStack> entry : team.equipment.entrySet()) {
				if (!entry.getValue().isEmpty()) {
					equipment.put(entry.getKey(), entry.getValue().copy());
				}
			}
			return new TeamContents(items, equipment);
		}

		public void applyTo(final Team team) {
			team.items.clear();
			for (ItemStackWithSlot entry : this.items) {
				if (entry.isValidInContainer(team.items.size())) {
					team.items.set(entry.slot(), entry.stack());
				}
			}
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				team.equipment.put(slot, this.equipment.getOrDefault(slot, ItemStack.EMPTY));
			}
		}
	}

	private static final Codec<Map<String, TeamContents>> CONTENTS_CODEC = Codec.unboundedMap(Codec.STRING, TeamContents.CODEC);

	public static final Codec<TeamContentsSavedData> CODEC = CONTENTS_CODEC
		.fieldOf("teams")
		.codec()
		.xmap(TeamContentsSavedData::new, data -> data.snapshot());

	public static final SavedDataType<TeamContentsSavedData> TYPE = new SavedDataType<>(
		PantheonMod.id("team_contents"),
		TeamContentsSavedData::new,
		CODEC,
		null
	);

	/** What was on disk when this was decoded; empty for a fresh world. Only meaningful right after load. */
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

	/** Current live contents of every team, as the codec sees them at save time. */
	private Map<String, TeamContents> snapshot() {
		Map<String, TeamContents> snapshot = new LinkedHashMap<>();
		for (Team team : TeamManager.allTeams()) {
			snapshot.put(team.name, TeamContents.of(team));
		}
		return snapshot;
	}
}
