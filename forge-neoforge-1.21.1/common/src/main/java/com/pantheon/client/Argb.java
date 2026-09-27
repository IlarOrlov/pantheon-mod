package com.pantheon.client;

/**
 * The few packed-ARGB helpers Pantheon needs. Newer Minecraft ships these as
 * {@code net.minecraft.util.ARGB}; 1.21.1 doesn't have that class, so they
 * live here - {@link #setBrightness} in particular reproduces the newer
 * vanilla method exactly, so a player's frame color is the same on every
 * Pantheon build.
 */
public final class Argb {
	private Argb() {
	}

	public static int alpha(final int color) {
		return color >>> 24;
	}

	public static int red(final int color) {
		return color >> 16 & 0xFF;
	}

	public static int green(final int color) {
		return color >> 8 & 0xFF;
	}

	public static int blue(final int color) {
		return color & 0xFF;
	}

	public static int color(final int alpha, final int red, final int green, final int blue) {
		return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
	}

	public static int color(final int alpha, final int rgb) {
		return alpha << 24 | rgb & 0xFFFFFF;
	}

	/** Keeps the color's hue and saturation but replaces its HSB brightness. */
	public static int setBrightness(final int color, final float brightness) {
		int red = red(color);
		int green = green(color);
		int blue = blue(color);
		int alpha = alpha(color);
		int max = Math.max(Math.max(red, green), blue);
		int min = Math.min(Math.min(red, green), blue);
		float range = max - min;
		float saturation = max != 0 ? range / max : 0.0F;
		float hue;
		if (saturation == 0.0F) {
			hue = 0.0F;
		} else {
			float redC = (max - red) / range;
			float greenC = (max - green) / range;
			float blueC = (max - blue) / range;
			if (red == max) {
				hue = blueC - greenC;
			} else if (green == max) {
				hue = 2.0F + redC - blueC;
			} else {
				hue = 4.0F + greenC - redC;
			}
			hue /= 6.0F;
			if (hue < 0.0F) {
				hue++;
			}
		}

		if (saturation == 0.0F) {
			int grey = Math.round(brightness * 255.0F);
			return color(alpha, grey, grey, grey);
		}
		float sector = (hue - (float) Math.floor(hue)) * 6.0F;
		float fraction = sector - (float) Math.floor(sector);
		float p = brightness * (1.0F - saturation);
		float q = brightness * (1.0F - saturation * fraction);
		float t = brightness * (1.0F - saturation * (1.0F - fraction));
		switch ((int) sector) {
			case 0 -> {
				red = Math.round(brightness * 255.0F);
				green = Math.round(t * 255.0F);
				blue = Math.round(p * 255.0F);
			}
			case 1 -> {
				red = Math.round(q * 255.0F);
				green = Math.round(brightness * 255.0F);
				blue = Math.round(p * 255.0F);
			}
			case 2 -> {
				red = Math.round(p * 255.0F);
				green = Math.round(brightness * 255.0F);
				blue = Math.round(t * 255.0F);
			}
			case 3 -> {
				red = Math.round(p * 255.0F);
				green = Math.round(q * 255.0F);
				blue = Math.round(brightness * 255.0F);
			}
			case 4 -> {
				red = Math.round(t * 255.0F);
				green = Math.round(p * 255.0F);
				blue = Math.round(brightness * 255.0F);
			}
			case 5 -> {
				red = Math.round(brightness * 255.0F);
				green = Math.round(p * 255.0F);
				blue = Math.round(q * 255.0F);
			}
			default -> {
			}
		}
		return color(alpha, red, green, blue);
	}
}
