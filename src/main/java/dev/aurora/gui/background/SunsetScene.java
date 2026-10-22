package dev.aurora.gui.background;

import dev.aurora.gui.render.Render2D;
import dev.aurora.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * A warm evening in the hills: layered ridges fading into haze, a low sun with soft rays,
 * drifting clouds, a flock of birds, fireflies in the foreground and a lookout tower with its
 * lamp on.
 */
final class SunsetScene implements Scene {
	private static final float[] SKY_STOPS = {0f, 0.2f, 0.36f, 0.48f, 0.56f, 0.62f, 0.72f};
	private static final int[] SKY_COLORS = {0xFF1C1640, 0xFF3D2366, 0xFF7E3A78, 0xFFC9566E, 0xFFF0805C, 0xFFFFB36E, 0xFFFFD99B};
	private static final float[] BASE = {0.585f, 0.63f, 0.69f, 0.765f, 0.86f};
	private static final float[] AMP = {0.07f, 0.065f, 0.06f, 0.055f, 0.05f};
	private static final int[] TOP = {0xFFD9786F, 0xFFB45A73, 0xFF84406F, 0xFF552A5E, 0xFF2C163B};
	private static final int[] FOG = {0xFFF6B48C, 0xFFE18F84, 0xFFB86C80, 0xFF7F4872, 0xFF48264F};
	private static final float[] DEPTH = {0.14f, 0.24f, 0.36f, 0.52f, 0.72f};
	/** Tree size on each ridge as a fraction of the height; 0 leaves the ridge bare. */
	private static final float[] TREES = {0f, 0f, 0.011f, 0.02f, 0.034f};
	private static final int TOWER_LAYER = 2;

	private final Ridge[] layers = new Ridge[BASE.length];
	private final Ridge foreground = new Ridge();
	private float towerX;
	private float towerY;
	private float builtW = -1;
	private float builtH = -1;

	SunsetScene() {
		for (int i = 0; i < layers.length; i++) {
			layers[i] = new Ridge();
		}
	}

	private float sunX(Frame f) {
		return f.w() * 0.68f;
	}

	private void build(Frame f) {
		if (f.w() == builtW && f.h() == builtH) {
			return;
		}
		builtW = f.w();
		builtH = f.h();
		float w = f.w();
		float h = f.h();
		float m = f.margin() * 1.3f;
		float step = Math.max(f.px() * 2.5f, h / 320f);
		float fine = Math.max(f.px() * 1.5f, h / 600f);
		towerX = w * 0.82f;
		for (int i = 0; i < layers.length; i++) {
			Ridge ground = new Ridge();
			for (float x = -m; x <= w + m + step; x += step) {
				float u = (x - w * 0.5f) / h;
				ground.add(x, h * BASE[i] - h * AMP[i] * Noise.fbm(u * (1.1f + 0.45f * i) + i * 10, 200 + i, 4));
			}
			if (i == TOWER_LAYER) {
				towerY = ground.yAt(towerX);
			}
			if (TREES[i] <= 0) {
				layers[i] = ground;
				continue;
			}
			List<Ridge.Tree> trees = new ArrayList<>();
			int k = 0;
			for (float x = -m; x < w + m; k++) {
				float u = (x - w * 0.5f) / h;
				float density = Noise.fbm(u * 1.6f + i * 3, 300 + i, 3);
				float half = TREES[i] * h * (0.45f + 0.2f * Noise.hash(k, 310 + i));
				boolean clearing = i == TOWER_LAYER && Math.abs(x - towerX) < h * 0.03f;
				if (density > 0.42f && !clearing) {
					float th = TREES[i] * h * (1.4f + 1.6f * Noise.hash(k, 320 + i)) * (0.4f + 0.6f * Noise.smooth(0.42f, 0.6f, density));
					trees.add(new Ridge.Tree(x, half, th, i >= 4 ? 4 : 3));
				}
				x += half * (1f + 0.8f * Noise.hash(k, 330 + i));
			}
			layers[i].forest(ground, trees, -m, w + m, fine);
		}

		Ridge ground = new Ridge();
		for (float x = -m; x <= w + m + step; x += step) {
			float u = (x - w * 0.5f) / h;
			ground.add(x, h * 1.03f - h * 0.03f * Noise.fbm(u * 3f, 340, 3));
		}
		List<Ridge.Tree> trees = List.of(
			new Ridge.Tree(-w * 0.005f, h * 0.085f, h * 0.5f, 8),
			new Ridge.Tree(w * 0.06f, h * 0.05f, h * 0.24f, 5),
			new Ridge.Tree(w * 0.845f, h * 0.042f, h * 0.16f, 4),
			new Ridge.Tree(w * 0.895f, h * 0.07f, h * 0.4f, 7),
			new Ridge.Tree(w * 0.955f, h * 0.09f, h * 0.55f, 9),
			new Ridge.Tree(w + h * 0.05f, h * 0.07f, h * 0.38f, 7));
		foreground.forest(ground, trees, -m, w + m, fine);
	}

	@Override
	public void render(Frame f) {
		build(f);
		float w = f.w();
		float h = f.h();
		float px = f.px();
		float t = f.time();
		float sx = sunX(f) + f.shiftX(0.06f);
		float sy = h * 0.575f;
		float sr = h * 0.06f;

		Render2D.Batch b = Render2D.batch();
		Scene.sky(b, f, SKY_STOPS, SKY_COLORS);
		b.vertical(0, h * SKY_STOPS[SKY_STOPS.length - 1], w, h, SKY_COLORS[SKY_COLORS.length - 1], SKY_COLORS[SKY_COLORS.length - 1]);
		b.disc(sx, sy, h * 0.75f, ColorUtil.fade(0xFFFFA36B, 0.24f), 0x00FF8A6B, 64);
		rays(b, f, sx, sy);
		b.ring(sx, sy, sr * 0.95f, sr * 3.2f, ColorUtil.fade(0xFFFFE2A8, 0.62f), 0x00FFD08A, 64);
		int core = 0xFFFFF4DA;
		b.disc(sx, sy, sr, core, core, 48);
		b.ring(sx, sy, sr, sr + px, core, core & 0xFFFFFF, 48);
		b.submit(f.g());

		clouds(f);

		b = Render2D.batch();
		birds(b, f);
		for (int i = 0; i < layers.length; i++) {
			Ridge layer = layers[i];
			float dx = f.shiftX(DEPTH[i]);
			float yLow = h * BASE[i] + h * 0.02f;
			layer.fill(b, f, dx, 0, h + 2, layer.top(), TOP[i], yLow, FOG[i]);
			if (i == 1) {
				// Light from the low sun washes over the far ridges.
				b.disc(sx, sy, h * 0.42f, ColorUtil.fade(0xFFFFC890, 0.2f), 0x00FFC890, 48);
			}
			if (i == TOWER_LAYER) {
				tower(b, f, towerX + dx, towerY, TOP[i]);
			}
		}
		b.submit(f.g());

		b = Render2D.batch();
		foreground.fill(b, f, f.shiftX(1f), 0, h + 2, foreground.top(), 0xFF1C0D27, h, 0xFF0F0716);
		fireflies(b, f);
		Scene.vignette(b, f, 0xFF140820, 0.3f, 0.35f, 0.25f);
		b.submit(f.g());
	}

	// ======================================================================= sky

	/** Soft beams fanning up from the sun, slowly breathing. */
	private static void rays(Render2D.Batch b, Frame f, float sx, float sy) {
		float t = f.time();
		float length = f.h() * 1.3f;
		for (int k = 0; k < 7; k++) {
			double angle = -Math.PI / 2 + (k - 3) * 0.36 + Math.sin(t * 0.05 + k * 1.3) * 0.05;
			double spread = 0.035 + 0.02 * Noise.hash(k, 400);
			float a = 0.055f + 0.04f * (float) Math.sin(t * 0.3 + k * 2.1);
			int apex = ColorUtil.fade(0xFFFFE6B0, a);
			float x0 = sx + (float) Math.cos(angle - spread) * length;
			float y0 = sy + (float) Math.sin(angle - spread) * length;
			float x1 = sx + (float) Math.cos(angle + spread) * length;
			float y1 = sy + (float) Math.sin(angle + spread) * length;
			b.triangle(sx, sy, apex, x0, y0, apex & 0xFFFFFF, x1, y1, apex & 0xFFFFFF);
		}
	}

	/**
	 * Clouds with a flat base and a few rounded puffs on top, lit from below by the low sun and
	 * drifting at different speeds by height. Each cloud is opaque and shares one gradient, so its
	 * overlapping parts merge without seams; a faint halo softens the outline.
	 */
	private static void clouds(Frame f) {
		float w = f.w();
		float h = f.h();
		float t = f.time();
		for (int k = 0; k < 7; k++) {
			float y = h * (0.08f + 0.36f * Noise.hash(k, 500));
			float length = h * (0.2f + 0.42f * Noise.hash(k, 501));
			float thick = h * (0.013f + 0.014f * Noise.hash(k, 502));
			float speed = h * (0.004f + 0.008f * Noise.hash(k, 503));
			float span = w + length * 2;
			float x = (Noise.hash(k, 504) * span + speed * t) % span - length + f.shiftX(0.08f);
			if (x > w || x + length < 0) {
				continue;
			}
			float lit = Math.min(1f, y / (h * 0.5f));
			int top = ColorUtil.lerp(0xFF573679, 0xFF9A4B7C, lit);
			int bottom = ColorUtil.lerp(0xFFD06A8C, 0xFFFFB585, lit);
			int puffs = 2 + (int) (Noise.hash(k, 505) * 3);
			float tallest = thick * 2.3f;
			float base = y + thick;
			Render2D.Paint paint = Render2D.Paint.vertical(base - tallest, tallest, top, bottom);
			Render2D.shadow(f.g(), x, y, length, thick, thick / 2f, thick * 1.4f, ColorUtil.fade(bottom, 0.22f));
			Render2D.roundRect(f.g(), x, y, length, thick, thick / 2f, paint);
			for (int p = 0; p < puffs; p++) {
				float size = thick * (1.25f + 1.05f * Noise.hash(k * 7 + p, 507));
				float width = size * (1.5f + Noise.hash(k * 7 + p, 508));
				float center = x + length * (0.18f + 0.64f * (p + 0.5f) / puffs)
					+ (Noise.hash(k * 7 + p, 506) - 0.5f) * length * 0.08f;
				Render2D.roundRect(f.g(), center - width / 2f, base - size, width, size, size / 2f, paint);
			}
		}
	}

	/** Every so often a small flock crosses the sky in a loose V, wings beating out of sync. */
	private static void birds(Render2D.Batch b, Frame f) {
		float w = f.w();
		float h = f.h();
		float t = f.time();
		float period = 46f;
		int cycle = (int) Math.floor(t / period);
		float p = (t - cycle * period) / 30f;
		if (p > 1) {
			return;
		}
		float cx = -w * 0.15f + p * w * 1.3f + f.shiftX(0.1f);
		float cy = h * (0.2f + 0.08f * Noise.hash(cycle, 600)) + (float) Math.sin(p * 4) * h * 0.015f;
		float s = h * 0.0085f;
		float px = f.px();
		int color = 0xD0301A3C;
		float width = Math.max(px * 1.3f, s * 0.16f);
		for (int k = 0; k < 7; k++) {
			int rank = (k + 1) / 2;
			int side = k % 2 == 0 ? 1 : -1;
			float x = cx - rank * h * 0.026f + (float) Math.sin(t * 0.7 + k) * s * 0.6f;
			float y = cy + side * rank * h * 0.014f + (float) Math.sin(t * 1.1 + k * 2) * s * 0.5f;
			float flap = (float) Math.sin(t * 7.5 + k * 0.9);
			float ex = s * 0.48f;
			float ey = -s * 0.28f * (0.4f + flap);
			float tx = s;
			float ty = s * 0.12f - s * 0.62f * flap;
			b.line(px, x, y, x - ex, y + ey, width, color);
			b.line(px, x - ex, y + ey, x - tx, y + ty, width, color);
			b.line(px, x, y, x + ex, y + ey, width, color);
			b.line(px, x + ex, y + ey, x + tx, y + ty, width, color);
		}
	}

	// ======================================================================= land

	/** A fire lookout on stilts with a warm lit window, as a silhouette of its ridge. */
	private static void tower(Render2D.Batch b, Frame f, float x, float groundY, int color) {
		float h = f.h();
		float px = f.px();
		float t = f.time();
		float s = h * 0.052f;
		float leg = Math.max(px * 1.2f, s * 0.035f);
		float cabinW = s * 0.36f;
		float cabinH = s * 0.24f;
		float cabinY = groundY - s * 0.92f;
		float legTop = cabinY + cabinH;
		float lx0 = x - s * 0.22f;
		float rx0 = x + s * 0.22f;
		float lx1 = x - cabinW * 0.4f;
		float rx1 = x + cabinW * 0.4f;
		b.line(px, lx0, groundY + px * 2, lx1, legTop, leg, color);
		b.line(px, rx0, groundY + px * 2, rx1, legTop, leg, color);
		for (float a = 0.1f; a < 0.9f; a += 0.45f) {
			float ya = groundY + (legTop - groundY) * a;
			float yb = groundY + (legTop - groundY) * (a + 0.4f);
			b.line(px, lx0 + (lx1 - lx0) * a, ya, rx0 + (rx1 - rx0) * (a + 0.4f), yb, leg * 0.6f, color);
			b.line(px, rx0 + (rx1 - rx0) * a, ya, lx0 + (lx1 - lx0) * (a + 0.4f), yb, leg * 0.6f, color);
		}
		b.rect(x - cabinW / 2, cabinY, cabinW, cabinH, color, color, color, color);
		b.polygon(px, x, cabinY - s * 0.04f, new float[]{x - cabinW * 0.68f, cabinY + px, x, cabinY - s * 0.13f, x + cabinW * 0.68f, cabinY + px}, color);
		float flicker = 0.85f + 0.15f * (float) Math.sin(t * 2.3) * (float) Math.sin(t * 3.7 + 1);
		int lamp = ColorUtil.fade(0xFFFFD27A, flicker);
		b.disc(x, cabinY + cabinH * 0.5f, s * 0.32f, ColorUtil.fade(0xFFFFC46A, 0.3f * flicker), 0x00FFC46A, 16);
		b.rect(x - cabinW * 0.3f, cabinY + cabinH * 0.28f, cabinW * 0.6f, cabinH * 0.42f, lamp, lamp, lamp, lamp);
	}

	/** Fireflies drifting over the meadow, each blinking on its own rhythm. */
	private static void fireflies(Render2D.Batch b, Frame f) {
		float w = f.w();
		float h = f.h();
		float t = f.time();
		float dx = f.shiftX(0.85f);
		for (int k = 0; k < 28; k++) {
			float x = Noise.hash(k, 700) * w + dx
				+ (float) (Math.sin(t * 0.21 + k) * h * 0.04 + Math.cos(t * 0.13 + k * 1.7) * h * 0.02);
			float y = h * (0.74f + 0.25f * Noise.hash(k, 701)) + (float) Math.sin(t * 0.27 + k * 2.3) * h * 0.025f;
			float blink = (float) (0.5 + 0.5 * Math.sin(t * (0.6 + 0.5 * Noise.hash(k, 702)) + k * 4.1));
			float a = Noise.smooth(0.35f, 0.95f, blink);
			if (a < 0.02f) {
				continue;
			}
			float size = h * (0.0022f + 0.0016f * Noise.hash(k, 703));
			b.disc(x, y, size * 5.5f, ColorUtil.fade(0xFFFFCF6A, 0.35f * a), 0x00FFCF6A, 14);
			b.disc(x, y, Math.max(size, f.px() * 1.5f), ColorUtil.fade(0xFFFFF4C4, a), ColorUtil.fade(0xFFFFE08A, a * 0.6f), 10);
		}
	}
}
