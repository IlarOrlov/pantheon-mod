package com.pantheon;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Keeps a crash from duplicating a death's drops.
 *
 * <p>The shared inventory lives in world saved data, written only on a
 * world save (autosave every few minutes, pause, quit). A death's dropped
 * items, though, are ordinary entities saved with their chunk - which
 * happens on its own as soon as the chunk unloads, e.g. the moment everyone
 * respawns far away. Crash after that and the world comes back with the
 * inventory from the last autosave (still full, from before the death)
 * <em>and</em> the dropped items lying on the ground: everything doubled.
 * The same happens the other way round after picking the drops back up.
 *
 * <p>So both moments trigger a full world save - players, chunks and saved
 * data together, exactly what autosave does - keeping the inventory and the
 * items on the ground consistent with each other on disk:
 * <ul>
 *   <li>right after any player death ({@link #onPlayerDeath});</li>
 *   <li>shortly after picking up an item dropped by a death
 *   ({@link #onDeathDropPickedUp}, debounced so a player sweeping up a whole
 *   pile triggers one save once they're done, not one per stack).</li>
 * </ul>
 */
public final class DeathDropSaves {
	/** Entity tag on every item dropped by a death, so picking it back up can be recognized. */
	public static final String DEATH_DROP_TAG = "pantheon_death_drop";

	/** Save this long after the last death-drop pickup - long enough to sweep up a pile in one go. */
	private static final int PICKUP_SAVE_DELAY_TICKS = 40;

	/** ...but never put off longer than this from the first pickup, however long the sweeping goes on. */
	private static final int PICKUP_SAVE_MAX_DELAY_TICKS = 200;

	/** Server tick to save at, or -1 for none pending. */
	private static int saveAtTick = -1;

	/** Latest tick {@link #saveAtTick} may be pushed back to by further pickups. */
	private static int saveDeadlineTick = -1;

	private DeathDropSaves() {
	}

	public static void tagDeathDrop(final ItemEntity item) {
		if (item != null) {
			item.addTag(DEATH_DROP_TAG);
		}
	}

	public static void onPlayerDeath(final MinecraftServer server) {
		// Next tick rather than right now: the death's drops are spawned
		// within this same tick, possibly after this is called.
		int at = server.getTickCount() + 1;
		saveAtTick = saveAtTick >= 0 ? Math.min(saveAtTick, at) : at;
		saveDeadlineTick = saveAtTick;
	}

	public static void onDeathDropPickedUp(final MinecraftServer server) {
		int now = server.getTickCount();
		if (saveAtTick < 0) {
			saveAtTick = now + PICKUP_SAVE_DELAY_TICKS;
			saveDeadlineTick = now + PICKUP_SAVE_MAX_DELAY_TICKS;
			return;
		}
		// Push a pending save back while pickups keep coming, but never past
		// its deadline (a pending death save's deadline is itself - so that
		// one is never delayed at all).
		saveAtTick = Math.min(Math.max(saveAtTick, now + PICKUP_SAVE_DELAY_TICKS), saveDeadlineTick);
	}

	/** Called every server tick; runs the pending save once it's due. */
	public static void tick(final MinecraftServer server) {
		if (saveAtTick < 0 || server.getTickCount() < saveAtTick) {
			return;
		}
		saveAtTick = -1;
		saveDeadlineTick = -1;
		PantheonMod.LOGGER.info("[DeathDropSaves] saving the world so a crash can't duplicate death drops");
		server.saveEverything(true, false, false);
	}

	/** Forget anything pending - the server is going away (or a new one is starting in the same JVM). */
	public static void reset() {
		saveAtTick = -1;
		saveDeadlineTick = -1;
	}
}
