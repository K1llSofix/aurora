package dev.aurora.util;

/** ARGB colour helpers. Every colour in Aurora is a packed 0xAARRGGBB int. */
public final class ColorUtil {
	private ColorUtil() {
	}

	public static int argb(int a, int r, int g, int b) {
		return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
	}

	public static int alpha(int c) {
		return c >>> 24;
	}

	public static int red(int c) {
		return (c >> 16) & 0xFF;
	}

	public static int green(int c) {
		return (c >> 8) & 0xFF;
	}

	public static int blue(int c) {
		return c & 0xFF;
	}

	public static int withAlpha(int c, int a) {
		return (clamp255(a) << 24) | (c & 0xFFFFFF);
	}

	/** Scales the existing alpha, so a half transparent colour faded by 0.5 ends up at a quarter. */
	public static int fade(int c, float factor) {
		return withAlpha(c, Math.round(alpha(c) * clamp01(factor)));
	}

	public static int lerp(int from, int to, float t) {
		t = clamp01(t);
		return argb(
			Math.round(alpha(from) + (alpha(to) - alpha(from)) * t),
			Math.round(red(from) + (red(to) - red(from)) * t),
			Math.round(green(from) + (green(to) - green(from)) * t),
			Math.round(blue(from) + (blue(to) - blue(from)) * t));
	}

	/** Moves the colour towards white (positive amount) or black (negative amount). */
	public static int shade(int c, float amount) {
		return amount >= 0 ? lerp(c, withAlpha(0xFFFFFF, alpha(c)), amount) : lerp(c, withAlpha(0x000000, alpha(c)), -amount);
	}

	/** h, s, v in [0, 1]. */
	public static int hsv(float h, float s, float v, int alpha) {
		h = (h % 1f + 1f) % 1f;
		s = clamp01(s);
		v = clamp01(v);
		float r, g, b;
		float sector = h * 6f;
		int i = (int) Math.floor(sector);
		float f = sector - i;
		float p = v * (1 - s);
		float q = v * (1 - s * f);
		float t = v * (1 - s * (1 - f));
		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return argb(alpha, Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
	}

	/** Returns {hue, saturation, value}, each in [0, 1]. */
	public static float[] toHsv(int c) {
		float r = red(c) / 255f, g = green(c) / 255f, b = blue(c) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float h;
		if (delta == 0) {
			h = 0;
		} else if (max == r) {
			h = ((g - b) / delta) % 6f;
		} else if (max == g) {
			h = (b - r) / delta + 2f;
		} else {
			h = (r - g) / delta + 4f;
		}
		h /= 6f;
		if (h < 0) h += 1f;
		float s = max == 0 ? 0 : delta / max;
		return new float[]{h, s, max};
	}

	/** A hue that cycles over time; offset lets neighbouring elements form a wave. */
	public static int rainbow(float speed, float offset, float saturation, float brightness, int alpha) {
		double seconds = System.nanoTime() / 1_000_000_000.0;
		float hue = (float) ((seconds * 0.12 * speed + offset) % 1.0);
		return hsv(hue, saturation, brightness, alpha);
	}

	public static String toHex(int c, boolean withAlpha) {
		return withAlpha ? String.format("#%08X", c) : String.format("#%06X", c & 0xFFFFFF);
	}

	/** Perceived luminance in [0, 1], used to choose readable foreground colours. */
	public static float luminance(int c) {
		return (0.2126f * red(c) + 0.7152f * green(c) + 0.0722f * blue(c)) / 255f;
	}

	public static float clamp01(float v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	private static int clamp255(int v) {
		return v < 0 ? 0 : (v > 255 ? 255 : v);
	}
}
