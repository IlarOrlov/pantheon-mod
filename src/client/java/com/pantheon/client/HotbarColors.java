package com.pantheon.client;

import java.util.UUID;

import net.minecraft.util.Mth;

/**
 * Every hotbar-lock frame is drawn in a color derived from that player's
 * UUID, so it's consistent for them across every client without ever being
 * sent over the network - each client computes it independently and
 * identically.
 *
 * <p>This used to mirror vanilla's own {@code LocatorBar} formula exactly
 * (hash the UUID straight into RGB bits, then only normalize brightness),
 * but that formula doesn't control saturation at all: an unlucky hash lands
 * on a near-gray RGB triple whose brightness-only normalization is still
 * just a pale, washed-out shade, indistinguishable at a glance from any
 * other pale hash - two different players could easily both land there and
 * look like "the same color" even though the underlying values technically
 * differ. Deriving a hue from the hash instead and building a fully
 * saturated color from it guarantees every player gets one of a wide range
 * of vivid, visually distinct colors, at the cost of no longer matching the
 * vanilla locator bar's own (weaker) formula for the same player.
 */
public final class HotbarColors {
	private static final float SATURATION = 0.85f;
	private static final float BRIGHTNESS = 0.9f;

	private HotbarColors() {
	}

	public static int colorFor(final UUID uuid) {
		float hue = (uuid.hashCode() & 0xFFFFFF) / (float) 0x1000000;
		return Mth.hsvToArgb(hue, SATURATION, BRIGHTNESS, 255);
	}
}
