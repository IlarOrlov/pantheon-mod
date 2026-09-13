package com.pantheon.client;

/** The palette every player's owned-slot frame is drawn in, picked by their persistent color index (0-8). */
public final class HotbarColors {
	private static final int[] PALETTE = {
		0xFFE6194B, 0xFF3CB44B, 0xFFFFE119, 0xFF4363D8, 0xFFF58231,
		0xFF911EB4, 0xFF46F0F0, 0xFFF032E6, 0xFFBFEF45
	};

	private HotbarColors() {
	}

	public static int colorFor(final int index) {
		if (index < 0) {
			return 0xFFFFFFFF;
		}
		return PALETTE[index % PALETTE.length];
	}
}
