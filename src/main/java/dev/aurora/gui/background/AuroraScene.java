package dev.aurora.gui.background;

import dev.aurora.gui.Theme;
import dev.aurora.gui.render.Render2D;
import dev.aurora.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Night over a mountain lake: northern lights in the theme's colours drift over snowy peaks and
 * a spruce shore, everything mirrored in still water, with stars, shooting stars and low mist.
 */
final class AuroraScene implements Scene {
	private static final float WATER = 0.655f;
	private static final float SQUASH = 0.78f;
	private static final float[] SKY_STOPS = {0f, 0.24f, 0.44f, 0.58f, WATER + 0.01f};
	private static final int[] SKY_COLORS = {0xFF03040B, 0xFF060A1C, 0xFF0C1431, 0xFF141F47, 0xFF1C2B57};

	private final Ridge far = new Ridge();
	private final Ridge mid = new Ridge();
	private final Ridge shore = new Ridge();
	private final Ridge bankLeft = new Ridge();
	private final Ridge bankRight = new Ridge();
	private final StarField stars = new StarField(7, 0xFFFFE8C8, 0xFFCFE0FF);
	private float builtW = -1;
	private float builtH = -1;

	/** Natural aurora green and violet, nudged towards the theme so the scene matches the UI. */
	private static int low() {
		return ColorUtil.lerp(0xFF3DFFA8, Theme.accent(), 0.3f);
	}

	private static int high() {
		return ColorUtil.lerp(0xFF9D6BFF, Theme.accent2(), 0.3f);
	}

	private float[] base = new float[0];
	private float[] height = new float[0];
	private float[] light = new float[0];
	private float[] rays = new float[0];

	// ======================================================================= landscape

	private void build(Frame f) {
		if (f.w() == builtW && f.h() == builtH) {
			return;
		}
		builtW = f.w();
		builtH = f.h();
		float w = f.w();
		float h = f.h();
		float m = f.margin() * 1.3f;
		float water = h * WATER;
		float step = Math.max(f.px() * 2.5f, h / 320f);

		far.clear();
		mid.clear();
		Ridge ground = new Ridge();
		for (float x = -m; x <= w + m + step; x += step) {
			float u = (x - w * 0.5f) / h;
			float peaks = (float) Math.pow(Noise.ridged(u * 1.45f + 4.2f, 11, 6), 1.4);
			float range = 0.68f + 0.32f * Noise.fbm(u * 0.45f + 2f, 12, 2);
			far.add(x, water - h * (0.05f + 0.3f * peaks * range));
			mid.add(x, water - h * (0.015f + 0.1f * (float) Math.pow(Noise.ridged(u * 2.3f + 9f, 21, 5), 1.3)));
			ground.add(x, water - h * (0.003f + 0.012f * Noise.fbm(u * 3f, 31, 3)));
		}

		// The far shore: a spruce forest with clearings.
		List<Ridge.Tree> trees = new ArrayList<>();
		int k = 0;
		for (float x = -m; x < w + m; k++) {
			float u = (x - w * 0.5f) / h;
			float density = Noise.fbm(u * 1.3f + 5f, 32, 3);
			float half = h * (0.0055f + 0.005f * Noise.hash(k, 33));
			if (density > 0.36f) {
				float th = h * (0.014f + 0.03f * Noise.hash(k, 34)) * (0.35f + 0.65f * Noise.smooth(0.36f, 0.6f, density));
				trees.add(new Ridge.Tree(x, half, th, 3));
			}
			x += half * (1.05f + 0.9f * Noise.hash(k, 35));
		}
		float fine = Math.max(f.px() * 1.5f, h / 600f);
		shore.forest(ground, trees, -m, w + m, fine);

		// Foreground banks with tall spruces framing both sides.
		Ridge leftGround = new Ridge();
		Ridge rightGround = new Ridge();
		for (float x = -m; x <= w + m + step; x += step) {
			float u = (x - w * 0.5f) / h;
			float rough = h * 0.02f * Noise.fbm(u * 4f + 1f, 41, 3);
			leftGround.add(x, h * 1.02f - h * 0.075f * Noise.smooth(w * 0.24f, w * 0.06f, x) - rough);
			rightGround.add(x, h * 1.02f - h * 0.11f * Noise.smooth(w * 0.68f, w * 0.86f, x) - rough);
		}
		List<Ridge.Tree> left = List.of(
			new Ridge.Tree(-w * 0.01f, h * 0.075f, h * 0.3f, 7),
			new Ridge.Tree(w * 0.05f, h * 0.05f, h * 0.17f, 5),
			new Ridge.Tree(w * 0.105f, h * 0.032f, h * 0.08f, 4));
		List<Ridge.Tree> right = List.of(
			new Ridge.Tree(w * 0.78f, h * 0.035f, h * 0.09f, 4),
			new Ridge.Tree(w * 0.84f, h * 0.062f, h * 0.27f, 6),
			new Ridge.Tree(w * 0.905f, h * 0.088f, h * 0.43f, 8),
			new Ridge.Tree(w * 0.965f, h * 0.068f, h * 0.31f, 7),
			new Ridge.Tree(w + h * 0.03f, h * 0.075f, h * 0.36f, 7));
		bankLeft.forest(leftGround, left, -m, w * 0.3f, fine);
		bankRight.forest(rightGround, right, w * 0.62f, w + m, fine);
	}

	// ======================================================================= frame

	@Override
	public void render(Frame f) {
		build(f);
		float w = f.w();
		float h = f.h();
		float water = h * WATER;
		float auroraX = f.shiftX(0.1f);
		float auroraY = f.shiftY(0.1f);

		Render2D.Batch b = Render2D.batch();
		Scene.sky(b, f, SKY_STOPS, SKY_COLORS);
		int glow = ColorUtil.fade(ColorUtil.lerp(low(), high(), 0.35f), 0.09f);
		b.vertical(0, h * 0.1f, w, h * 0.22f, glow & 0xFFFFFF, glow);
		b.vertical(0, h * 0.32f, w, water - h * 0.32f, glow, ColorUtil.fade(glow, 0.4f));
		milkyWay(b, f, f.shiftX(0.03f), f.shiftY(0.03f));
		stars.draw(b, f, StarField.density(f, 115), water, f.shiftX(0.04f), f.shiftY(0.04f), 1f);
		meteor(b, f, f.shiftX(0.04f), f.shiftY(0.04f));
		b.submit(f.g());

		b = Render2D.batch();
		curtains(b, f, auroraX, auroraY, false, water, 1f);
		b.submit(f.g());

		float farX = f.shiftX(0.22f);
		float midX = f.shiftX(0.36f);
		float shoreX = f.shiftX(0.5f);
		int farTop = ColorUtil.lerp(0xFF34477E, low(), 0.1f);
		b = Render2D.batch();
		far.fill(b, f, farX, 0, water + 1, far.top(), farTop, far.top() + h * 0.14f, 0xFF0D1434);
		mid.fill(b, f, midX, 0, water + 1, mid.top(), 0xFF141C3D, water, 0xFF090D20);
		shore.fill(b, f, shoreX, 0, water + 1, shore.top(), 0xFF05070F, water, 0xFF04060D);
		b.submit(f.g());

		// The lake: sky colour at the far shore darkening towards us, then the mirrored world.
		b = Render2D.batch();
		b.vertical(0, water, w, h - water + 1, 0xFF121C40, 0xFF03050B);
		curtains(b, f, auroraX, auroraY, true, water, 0.42f);
		b.submit(f.g());

		b = Render2D.batch();
		far.reflect(b, f, farX, 0, water, SQUASH, far.top(), ColorUtil.lerp(farTop, 0xFF0A1230, 0.45f),
			far.top() + h * 0.14f, 0xFF0A1029, 1f);
		mid.reflect(b, f, midX, 0, water, SQUASH, mid.top(), 0xFF0B1128, water, 0xFF070A1A, 1f);
		shore.reflect(b, f, shoreX, 0, water, SQUASH, shore.top(), 0xFF04060D, water, 0xFF04060D, 1f);
		int edge = ColorUtil.fade(ColorUtil.lerp(low(), 0xFFFFFFFF, 0.5f), 0.22f);
		b.vertical(0, water, w, f.px() * 1.5f, edge, edge & 0xFFFFFF);
		ripples(b, f, water, shoreX);
		mist(b, f, water, midX);
		b.submit(f.g());

		b = Render2D.batch();
		float bankX = f.shiftX(1f);
		bankLeft.fill(b, f, bankX, 0, h + 2, bankLeft.top(), 0xFF060914, h, 0xFF020307);
		bankRight.fill(b, f, bankX, 0, h + 2, bankRight.top(), 0xFF060914, h, 0xFF020307);
		Scene.vignette(b, f, 0xFF000000, 0.35f, 0.55f, 0.3f);
		b.submit(f.g());
	}

	// ======================================================================= sky

	/** A faint diagonal band of the galaxy behind everything. */
	private static void milkyWay(Render2D.Batch b, Frame f, float dx, float dy) {
		float w = f.w();
		float h = f.h();
		float ax = w * 0.98f + dx;
		float ay = -h * 0.06f + dy;
		float bx = w * 0.12f + dx;
		float by = h * 0.66f + dy;
		float len = (float) Math.hypot(bx - ax, by - ay);
		float nx = -(by - ay) / len;
		float ny = (bx - ax) / len;
		int color = 0xFFC2BCFF;
		int segments = 36;
		for (int i = 0; i < segments; i++) {
			float s0 = i / (float) segments;
			float s1 = (i + 1f) / segments;
			float x0 = ax + (bx - ax) * s0;
			float y0 = ay + (by - ay) * s0;
			float x1 = ax + (bx - ax) * s1;
			float y1 = ay + (by - ay) * s1;
			float w0 = h * (0.045f + 0.05f * Noise.fbm(s0 * 5f, 120, 3));
			float w1 = h * (0.045f + 0.05f * Noise.fbm(s1 * 5f, 120, 3));
			int c0 = ColorUtil.fade(color, 0.075f * Noise.smooth(0.25f, 0.7f, Noise.fbm(s0 * 3.5f + 3, 121, 3)));
			int c1 = ColorUtil.fade(color, 0.075f * Noise.smooth(0.25f, 0.7f, Noise.fbm(s1 * 3.5f + 3, 121, 3)));
			b.quad(x0 + nx * w0, y0 + ny * w0, c0 & 0xFFFFFF, x1 + nx * w1, y1 + ny * w1, c1 & 0xFFFFFF, x1, y1, c1, x0, y0, c0);
			b.quad(x0, y0, c0, x1, y1, c1, x1 - nx * w1, y1 - ny * w1, c1 & 0xFFFFFF, x0 - nx * w0, y0 - ny * w0, c0 & 0xFFFFFF);
		}
	}

	/** Every few seconds a shooting star streaks down across the sky. */
	private static void meteor(Render2D.Batch b, Frame f, float dx, float dy) {
		float period = 8.5f;
		float t = f.time();
		int k = (int) Math.floor(t / period);
		float start = Noise.hash(k, 90) * (period - 1.6f);
		float duration = 0.8f + 0.5f * Noise.hash(k, 91);
		float p = (t - k * period - start) / duration;
		if (p < 0 || p > 1) {
			return;
		}
		float w = f.w();
		float h = f.h();
		float sx = w * (0.32f + 0.6f * Noise.hash(k, 92)) + dx;
		float sy = h * (0.04f + 0.2f * Noise.hash(k, 93)) + dy;
		double angle = Math.toRadians(148 + 22 * Noise.hash(k, 94));
		float dirX = (float) Math.cos(angle);
		float dirY = (float) Math.sin(angle);
		float travel = h * (0.28f + 0.18f * Noise.hash(k, 95));
		float eased = 1 - (1 - p) * (1 - p);
		float hx = sx + dirX * travel * eased;
		float hy = sy + dirY * travel * eased;
		float fade = (float) Math.sin(Math.PI * p);
		float tail = h * 0.16f * fade;
		float tx = hx - dirX * tail;
		float ty = hy - dirY * tail;
		float nx = -dirY;
		float ny = dirX;
		float px = f.px();
		int head = ColorUtil.fade(0xFFFFFFFF, fade);
		int halo = ColorUtil.fade(0xFFBFE6FF, fade * 0.3f);
		b.triangle(hx + nx * px * 4, hy + ny * px * 4, halo, hx - nx * px * 4, hy - ny * px * 4, halo, tx, ty, halo & 0xFFFFFF);
		b.triangle(hx + nx * px * 1.1f, hy + ny * px * 1.1f, head, hx - nx * px * 1.1f, hy - ny * px * 1.1f, head, tx, ty, 0x00FFFFFF);
		b.disc(hx, hy, px * 6, ColorUtil.fade(0xFFDDF2FF, fade * 0.7f), 0x00DDF2FF, 10);
	}

	private void ensure(int size) {
		if (base.length < size) {
			base = new float[size];
			height = new float[size];
			light = new float[size];
			rays = new float[size];
		}
	}

	/**
	 * Three ribbons of light. Each column has a bright, sharp lower edge with a whitish core on
	 * the strongest rays, fading upwards from the theme's first accent into its second; fine
	 * vertical rays drift along the ribbon while whole sections slowly brighten and fade.
	 */
	private void curtains(Render2D.Batch b, Frame f, float dx, float dy, boolean mirror, float water, float alpha) {
		float w = f.w();
		float h = f.h();
		float t = f.time();
		int low = low();
		int high = high();
		int middle = ColorUtil.lerp(low, high, 0.5f);
		int cols = Math.max(90, Math.min(420, Math.round(w / Math.max(1.6f, f.px() * 4))));
		float step = w / cols;
		ensure(cols + 1);
		for (int r = 0; r < 3; r++) {
			float strength = r == 0 ? 1f : r == 1 ? 0.78f : 0.58f;
			for (int i = 0; i <= cols; i++) {
				float u = (i * step - dx - w * 0.5f) / h;
				float wave = (float) Math.sin(u * (1.7f + 0.45f * r) + t * (0.05f + 0.025f * r) + r * 2.1f);
				// Tighter folds ride on the long waves, like a curtain blowing in the wind.
				float fold = (float) Math.sin(u * (6.2f + r) + t * (0.11f + 0.03f * r) + r * 1.3f);
				float drift = Noise.fbm(u * 1.2f + t * 0.02f + r * 7.3f, 40 + r, 3) - 0.5f;
				base[i] = h * (0.25f + 0.07f * r) - h * 0.07f * u + h * 0.045f * wave + h * 0.022f * fold
					+ h * 0.06f * drift + dy;
				float section = Noise.smooth(0.36f, 0.68f, Noise.fbm(u * 0.8f - t * 0.016f + r * 3.7f, 50 + r, 3));
				float ray = Noise.fbm(u * 15f + t * (r % 2 == 0 ? 0.2f : -0.16f), 60 + r, 3);
				float flicker = 0.88f + 0.12f * (float) Math.sin(t * 1.1f + u * 7f + r);
				rays[i] = ray;
				light[i] = Math.min(1f, section * (0.35f + 0.8f * ray) * flicker * strength * 1.15f) * alpha;
				height[i] = h * (0.1f + 0.13f * Noise.fbm(u * 2.2f + r * 5f + t * 0.03f, 70 + r, 2)) * (0.55f + 0.65f * ray);
			}
			for (int i = 0; i < cols; i++) {
				float aL = light[i];
				float aR = light[i + 1];
				if (aL < 0.004f && aR < 0.004f) {
					continue;
				}
				float x0 = i * step;
				float x1 = x0 + step;
				int edgeL = ColorUtil.fade(ColorUtil.lerp(low, 0xFFFFFFFF, 0.32f * rays[i]), Math.min(1f, aL));
				int edgeR = ColorUtil.fade(ColorUtil.lerp(low, 0xFFFFFFFF, 0.32f * rays[i + 1]), Math.min(1f, aR));
				int midL = ColorUtil.fade(middle, aL * 0.48f);
				int midR = ColorUtil.fade(middle, aR * 0.48f);
				int top = high & 0xFFFFFF;
				int under = low & 0xFFFFFF;
				float bL = base[i];
				float bR = base[i + 1];
				float kL = bL - height[i] * 0.3f;
				float kR = bR - height[i + 1] * 0.3f;
				band(b, mirror, water, x0, x1, bL - height[i], bR - height[i + 1], top, top, kL, kR, midL, midR);
				band(b, mirror, water, x0, x1, kL, kR, midL, midR, bL, bR, edgeL, edgeR);
				band(b, mirror, water, x0, x1, bL, bR, edgeL, edgeR, bL + h * 0.011f, bR + h * 0.011f, under, under);
			}
		}
	}

	private static void band(Render2D.Batch b, boolean mirror, float water, float x0, float x1,
							 float topL, float topR, int cTopL, int cTopR, float botL, float botR, int cBotL, int cBotR) {
		if (mirror) {
			topL = water + (water - topL) * SQUASH;
			topR = water + (water - topR) * SQUASH;
			botL = water + (water - botL) * SQUASH;
			botR = water + (water - botR) * SQUASH;
		}
		b.quad(x0, topL, cTopL, x1, topR, cTopR, x1, botR, cBotR, x0, botL, cBotL);
	}

	// ======================================================================= water

	/** Thin light and dark streaks that break up the mirror like a breath of wind on the water. */
	private static void ripples(Render2D.Batch b, Frame f, float water, float dx) {
		float w = f.w();
		float h = f.h();
		float t = f.time();
		float px = f.px();
		float m = f.margin();
		int light = ColorUtil.lerp(low(), 0xFFFFFFFF, 0.55f);
		for (int i = 0; i < 70; i++) {
			float depth = (float) Math.pow(Noise.hash(i, 100), 1.6);
			float y = water + (h - water) * (0.015f + 0.985f * depth);
			float len = h * (0.015f + 0.09f * Noise.hash(i, 101)) * (0.35f + 0.65f * depth);
			float x = Noise.hash(i, 102) * (w + 2 * m) - m + dx * (0.6f + 0.4f * depth)
				+ (float) Math.sin(t * 0.3f + i) * h * 0.012f;
			float pulse = 0.5f + 0.5f * (float) Math.sin(t * (0.5f + Noise.hash(i, 103)) + i * 1.7f);
			float thick = px * (1 + 1.6f * depth);
			int c = Noise.hash(i, 104) < 0.4f
				? ColorUtil.fade(0xFF000000, 0.3f * (0.5f + 0.5f * pulse))
				: ColorUtil.fade(light, (0.05f + 0.13f * pulse) * (0.45f + 0.55f * depth));
			int clear = c & 0xFFFFFF;
			b.rect(x - len / 2, y, len / 2, thick, clear, c, c, clear);
			b.rect(x, y, len / 2, thick, c, clear, clear, c);
		}
	}

	/** Two slow banks of mist hugging the water line, drifting in opposite directions. */
	private static void mist(Render2D.Batch b, Frame f, float water, float dx) {
		float w = f.w();
		float h = f.h();
		float t = f.time();
		float m = f.margin();
		int tint = ColorUtil.lerp(0xFFB4C3EA, low(), 0.25f);
		int cols = 56;
		float step = (w + 2 * m) / cols;
		for (int band = 0; band < 2; band++) {
			float y = water - h * 0.01f + band * h * 0.022f;
			float half = h * (0.016f + 0.006f * band);
			float dir = band == 0 ? 1 : -1;
			for (int i = 0; i < cols; i++) {
				float x0 = -m + i * step + dx;
				float x1 = x0 + step;
				int c0 = ColorUtil.fade(tint, density(x0 - dx, w, h, t, dir, band));
				int c1 = ColorUtil.fade(tint, density(x1 - dx, w, h, t, dir, band));
				b.rect(x0, y - half, step, half, c0 & 0xFFFFFF, c1 & 0xFFFFFF, c1, c0);
				b.rect(x0, y, step, half, c0, c1, c1 & 0xFFFFFF, c0 & 0xFFFFFF);
			}
		}
	}

	private static float density(float x, float w, float h, float t, float dir, int band) {
		float u = (x - w * 0.5f) / h;
		return 0.15f * Noise.smooth(0.3f, 0.75f, Noise.fbm(u * 1.8f + t * 0.02f * dir + band * 4, 80 + band, 3));
	}
}
