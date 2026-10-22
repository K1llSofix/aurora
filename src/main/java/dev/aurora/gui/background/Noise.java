package dev.aurora.gui.background;

/** Small deterministic 1D noise used to shape landscapes and light; same seed, same picture. */
final class Noise {
	private Noise() {
	}

	/** A pseudo-random value in [0, 1) for an integer coordinate. */
	static float hash(int i, int seed) {
		int h = i * 0x27D4EB2D + seed * 0x165667B1;
		h ^= h >>> 15;
		h *= 0x85EBCA6B;
		h ^= h >>> 13;
		h *= 0xC2B2AE35;
		h ^= h >>> 16;
		return (h >>> 8) * (1f / 16777216f);
	}

	/** Smoothly interpolated value noise in [0, 1). */
	static float value(float x, int seed) {
		int i = (int) Math.floor(x);
		float f = x - i;
		float u = f * f * (3 - 2 * f);
		float a = hash(i, seed);
		return a + (hash(i + 1, seed) - a) * u;
	}

	/** Fractal noise in [0, 1): rolling hills, cloud density, slow variation. */
	static float fbm(float x, int seed, int octaves) {
		float sum = 0;
		float amp = 0.5f;
		float norm = 0;
		for (int o = 0; o < octaves; o++) {
			sum += value(x, seed + o * 1013) * amp;
			norm += amp;
			x = x * 2.03f + 17.1f;
			amp *= 0.5f;
		}
		return sum / norm;
	}

	/** Sharp-crested fractal noise in [0, 1]: mountain ranges. */
	static float ridged(float x, int seed, int octaves) {
		float sum = 0;
		float amp = 0.5f;
		float norm = 0;
		for (int o = 0; o < octaves; o++) {
			float n = 1 - Math.abs(value(x, seed + o * 1013) * 2 - 1);
			sum += n * n * amp;
			norm += amp;
			x = x * 2.07f + 31.7f;
			amp *= 0.48f;
		}
		return sum / norm;
	}

	static float smooth(float edge0, float edge1, float x) {
		float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
		return t * t * (3 - 2 * t);
	}
}
