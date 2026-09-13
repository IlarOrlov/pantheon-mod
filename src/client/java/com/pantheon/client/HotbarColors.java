package com.pantheon.client;

import java.util.UUID;

import net.minecraft.util.ARGB;

/**
 * Every hotbar-lock frame is drawn in the same color as that player's dot on
 * the vanilla locator bar, computed the exact same way vanilla does
 * ({@code LocatorBar}): a brightened hash of their UUID. Since every client
 * computes this independently and identically, it never needs to be sent
 * over the network, and it's guaranteed to match the locator bar exactly.
 */
public final class HotbarColors {
	private HotbarColors() {
	}

	public static int colorFor(final UUID uuid) {
		return ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9f);
	}
}
