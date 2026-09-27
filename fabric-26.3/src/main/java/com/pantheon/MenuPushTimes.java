package com.pantheon;

/**
 * Duck interface on {@code AbstractContainerMenu}: when the server last pushed
 * each slot's contents to the client. Lets the creative-slot handler tell a
 * write the client made while an update was already on its way (stale - it
 * never saw a teammate's change) from a deliberate one.
 */
public interface MenuPushTimes {
	/** {@code Util.getMillis()} of the last push of this slot, or 0 if never. */
	long pantheon$lastPushMillis(int slot);

	void pantheon$setLastPushMillis(int slot, long millis);
}
