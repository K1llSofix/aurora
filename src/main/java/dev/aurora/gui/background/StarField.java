package dev.aurora.gui.background;

import dev.aurora.gui.render.Render2D;
import dev.aurora.util.ColorUtil;

/** Twinkling stars: mostly faint pinpricks, a few bright ones with a soft glow and a glint. */
final class StarField {
	private final int seed;
	private final int warm;
	private final int cool;
	private float[] u;
	private float[] v;
	private float[] size;
	private float[] phase;
	private float[] speed;
	private int[] tint;
	private int count = -1;

	StarField(int seed, int warm, int cool) {
		this.seed = seed;
		this.warm = warm;
		this.cool = cool;
	}

	private void build(int count) {
		this.count = count;
		u = new float[count];
		v = new float[count];
		size = new float[count];
		phase = new float[count];
		speed = new float[count];
		tint = new int[count];
		for (int i = 0; i < count; i++) {
			u[i] = Noise.hash(i, seed);
			// Denser towards the zenith, thinning out near the horizon.
			v[i] = (float) Math.pow(Noise.hash(i, seed + 1), 1.35);
			float s = Noise.hash(i, seed + 2);
			size[i] = 0.7f + 2.1f * s * s * s;
			phase[i] = Noise.hash(i, seed + 3) * 6.2832f;
			speed[i] = 0.5f + 2.4f * Noise.hash(i, seed + 4);
			float c = Noise.hash(i, seed + 5);
			tint[i] = c < 0.16f ? warm : c < 0.42f ? cool : 0xFFFFFFFF;
		}
	}

	/** A star count that looks the same at any window size and GUI scale. */
	static int density(Frame f, float perMillion) {
		float physical = (f.w() / f.px()) * (f.h() / f.px());
		return Math.max(60, Math.min(420, Math.round(physical / 1_000_000f * perMillion)));
	}

	/** Stars over [0, w] x [0, maxY], fading out towards maxY like haze over the horizon. */
	void draw(Render2D.Batch b, Frame f, int count, float maxY, float dx, float dy, float alpha) {
		if (this.count != count) {
			build(count);
		}
		float px = f.px();
		float t = f.time();
		float m = f.margin();
		float w = f.w();
		for (int i = 0; i < count; i++) {
			float x = u[i] * (w + 2 * m) - m + dx;
			float y = v[i] * maxY + dy;
			if (x < -8 || x > w + 8) {
				continue;
			}
			float twinkle = 0.55f + 0.45f * (float) Math.sin(t * speed[i] + phase[i]);
			float haze = 1 - Noise.smooth(0.5f, 1f, v[i]);
			float a = alpha * twinkle * haze * Math.min(1f, 0.3f + size[i] * 0.38f);
			if (a <= 0.02f) {
				continue;
			}
			float s = size[i] * px;
			int c = ColorUtil.fade(tint[i], a);
			int clear = c & 0xFFFFFF;
			b.disc(x, y, Math.max(1.3f * px, s * 1.3f), c, clear, 6);
			if (size[i] > 1.6f) {
				float core = s * 0.45f;
				b.rect(x - core, y - core, core * 2, core * 2, c, c, c, c);
			}
			if (size[i] > 2.3f) {
				// A thin cross glint on the brightest stars, breathing with the twinkle.
				float gl = s * 3.2f * twinkle;
				int cg = ColorUtil.fade(tint[i], a * 0.55f);
				int cc = cg & 0xFFFFFF;
				b.rect(x - gl, y - px * 0.5f, gl, px, cc, cg, cg, cc);
				b.rect(x, y - px * 0.5f, gl, px, cg, cc, cc, cg);
				b.rect(x - px * 0.5f, y - gl, px, gl, cc, cc, cg, cg);
				b.rect(x - px * 0.5f, y, px, gl, cg, cg, cc, cc);
			}
		}
	}
}
