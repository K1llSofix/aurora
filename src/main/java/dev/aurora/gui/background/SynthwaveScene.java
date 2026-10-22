package dev.aurora.gui.background;

import dev.aurora.gui.render.Render2D;
import dev.aurora.util.ColorUtil;

/**
 * Retrowave: a striped sunset sun over wireframe mountains, an endless neon grid rolling
 * towards the viewer, palm silhouettes swaying in the breeze and a hint of VHS scanlines.
 */
final class SynthwaveScene implements Scene {
	private static final float HORIZON = 0.6f;
	private static final float[] SKY_STOPS = {0f, 0.28f, 0.46f, 0.55f, HORIZON + 0.005f};
	private static final int[] SKY_COLORS = {0xFF0A0420, 0xFF1E0A46, 0xFF4A1270, 0xFF9C2A86, 0xFFFF5C9E};
	private static final int PINK = 0xFFFF3FD0;
	private static final int CYAN = 0xFF3DF2FF;
	private static final int SILHOUETTE = 0xFF0C0219;

	private final StarField stars = new StarField(23, 0xFFFFD6F2, 0xFFD8D2FF);
	private final Ridge back = new Ridge();
	private final Ridge front = new Ridge();
	private float builtW = -1;
	private float builtH = -1;

	private float sunX(Frame f) {
		return f.w() * 0.66f;
	}

	private float sunRadius(Frame f) {
		return f.h() * 0.21f;
	}

	/** Low-poly ranges: low in front of the sun, rising towards the edges. */
	private void build(Frame f) {
		if (f.w() == builtW && f.h() == builtH) {
			return;
		}
		builtW = f.w();
		builtH = f.h();
		float w = f.w();
		float h = f.h();
		float m = f.margin() * 1.3f;
		float horizon = h * HORIZON;
		float sx = sunX(f);
		float r = sunRadius(f);
		back.clear();
		front.clear();
		float step = h * 0.045f;
		int k = 0;
		for (float x = -m; x <= w + m + step; x += step, k++) {
			float away = Noise.smooth(r * 0.55f, r * 1.7f, Math.abs(x - sx));
			back.add(x, horizon - h * (0.006f + (0.03f + 0.12f * Noise.hash(k, 51)) * (0.08f + 0.92f * away)));
		}
		step = h * 0.06f;
		k = 0;
		for (float x = -m + step * 0.4f; x <= w + m + step; x += step, k++) {
			float away = Noise.smooth(r * 0.9f, r * 2.2f, Math.abs(x - sx));
			front.add(x, horizon - h * (0.004f + (0.012f + 0.07f * Noise.hash(k, 52)) * away));
		}
	}

	@Override
	public void render(Frame f) {
		build(f);
		float w = f.w();
		float h = f.h();
		float px = f.px();
		float horizon = h * HORIZON;
		float sx = sunX(f) + f.shiftX(0.08f);
		float r = sunRadius(f);
		float sy = horizon - r * 0.42f;

		Render2D.Batch b = Render2D.batch();
		Scene.sky(b, f, SKY_STOPS, SKY_COLORS);
		stars.draw(b, f, StarField.density(f, 70), horizon * 0.8f, f.shiftX(0.04f), f.shiftY(0.04f), 0.9f);
		b.ring(sx, sy, r * 0.9f, r * 2.3f, ColorUtil.fade(0xFFFF4F9A, 0.42f), 0x00FF2E88, 72);
		b.ring(sx, sy, r * 0.9f, r * 1.35f, ColorUtil.fade(0xFFFF9A6A, 0.35f), 0x00FF6A88, 72);
		b.submit(f.g());

		b = Render2D.batch();
		sun(b, f, sx, sy, r, horizon);
		b.submit(f.g());

		b = Render2D.batch();
		float backX = f.shiftX(0.2f);
		float frontX = f.shiftX(0.32f);
		back.fill(b, f, backX, 0, horizon + 1, back.top(), 0xFF1E0A3E, horizon, 0xFF12052B);
		wire(b, f, back, backX, horizon, PINK, 0.85f);
		front.fill(b, f, frontX, 0, horizon + 1, front.top(), 0xFF150630, horizon, 0xFF0D0322);
		wire(b, f, front, frontX, horizon, CYAN, 0.75f);
		b.submit(f.g());

		b = Render2D.batch();
		grid(b, f, sx, horizon);
		// The horizon line itself: a hot white-pink seam with a wide glow.
		int seam = 0xFFFFC2E6;
		b.vertical(0, horizon - h * 0.03f, w, h * 0.03f, 0x00FF5CB0, ColorUtil.fade(0xFFFF5CB0, 0.45f));
		b.vertical(0, horizon, w, h * 0.05f, ColorUtil.fade(0xFFFF3D9A, 0.4f), 0x00FF3D9A);
		b.rect(0, horizon - px, w, px * 2, seam, seam, seam, seam);
		b.submit(f.g());

		b = Render2D.batch();
		float palmX = f.shiftX(0.9f);
		palm(b, f, w * 0.865f + palmX, h * 1.03f, h * 0.62f, -h * 0.07f, 0f);
		palm(b, f, w * 0.965f + palmX, h * 1.03f, h * 0.48f, h * 0.05f, 2.4f);
		palm(b, f, w * 0.03f + palmX, h * 1.03f, h * 0.34f, h * 0.05f, 4.1f);
		scanlines(b, f);
		Scene.vignette(b, f, 0xFF05000C, 0.25f, 0.5f, 0.35f);
		b.submit(f.g());
	}

	// ======================================================================= sun

	/**
	 * The sun as horizontal slices so it can be cut by stripes that open up towards the horizon
	 * and slide downwards; every edge gets a pixel of fade so the motion stays smooth.
	 */
	private static void sun(Render2D.Batch b, Frame f, float cx, float cy, float r, float horizon) {
		float px = f.px();
		float period = r * 0.095f;
		float phase = (float) ((f.t() * 0.22) % 1.0);
		float stripesFrom = cy - r * 0.32f;
		float bottom = Math.min(horizon, cy + r);
		float slice = Math.max(px * 1.5f, r / 120f);
		float y = cy - r;
		// Walk down the disc; inside a stripe nothing is drawn.
		while (y < bottom) {
			float y1 = Math.min(bottom, y + slice);
			float gapTop = Float.MAX_VALUE;
			float gapBottom = Float.MAX_VALUE;
			if (y1 > stripesFrom) {
				// The stripe whose band we are in (or the next one below).
				float rel = (y - stripesFrom) / period - phase;
				int idx = (int) Math.floor(rel);
				for (int k = idx; k <= idx + 1; k++) {
					float center = stripesFrom + (k + phase) * period;
					float depth = Math.max(0f, Math.min(1f, (center - stripesFrom) / (horizon - stripesFrom)));
					float half = period * 0.24f * (float) Math.pow(depth, 1.15);
					if (half > px * 0.25f && center + half > y && center - half < y1) {
						gapTop = center - half;
						gapBottom = center + half;
						break;
					}
				}
			}
			if (gapTop < y1 && gapBottom > y) {
				// Draw what is above the gap within this slice, then jump over it.
				if (gapTop > y) {
					chord(b, cx, cy, r, y, gapTop, px);
					edge(b, cx, cy, r, gapTop, px, false);
				}
				if (gapBottom < bottom) {
					edge(b, cx, cy, r, gapBottom, px, true);
				}
				y = gapBottom;
				continue;
			}
			chord(b, cx, cy, r, y, y1, px);
			y = y1;
		}
	}

	private static int sunColor(float cy, float r, float y) {
		float t = (y - (cy - r)) / (2 * r);
		return t < 0.5f ? ColorUtil.lerp(0xFFFFF27A, 0xFFFF9A5C, t * 2) : ColorUtil.lerp(0xFFFF9A5C, 0xFFFF2E88, (t - 0.5f) * 2);
	}

	private static float halfWidth(float cy, float r, float y) {
		float d = y - cy;
		return (float) Math.sqrt(Math.max(0, r * r - d * d));
	}

	private static void chord(Render2D.Batch b, float cx, float cy, float r, float y0, float y1, float px) {
		if (y1 <= y0) {
			return;
		}
		float w0 = halfWidth(cy, r, y0);
		float w1 = halfWidth(cy, r, y1);
		int c0 = sunColor(cy, r, y0);
		int c1 = sunColor(cy, r, y1);
		b.quad(cx - w0, y0, c0, cx + w0, y0, c0, cx + w1, y1, c1, cx - w1, y1, c1);
		// Anti-aliased rim on both sides.
		b.quad(cx - w0 - px, y0, c0 & 0xFFFFFF, cx - w0, y0, c0, cx - w1, y1, c1, cx - w1 - px, y1, c1 & 0xFFFFFF);
		b.quad(cx + w0, y0, c0, cx + w0 + px, y0, c0 & 0xFFFFFF, cx + w1 + px, y1, c1 & 0xFFFFFF, cx + w1, y1, c1);
	}

	/** One pixel of fade at a stripe edge (below the edge when {@code below}). */
	private static void edge(Render2D.Batch b, float cx, float cy, float r, float y, float px, boolean below) {
		float hw = halfWidth(cy, r, y);
		int c = sunColor(cy, r, y);
		int clear = c & 0xFFFFFF;
		if (below) {
			b.rect(cx - hw, y - px, hw * 2, px, clear, clear, c, c);
		} else {
			b.rect(cx - hw, y, hw * 2, px, c, c, clear, clear);
		}
	}

	// ======================================================================= mountains and grid

	/** Neon ridge line with a glow, plus facet lines from each peak down to the foot of the range. */
	private static void wire(Render2D.Batch b, Frame f, Ridge ridge, float dx, float horizon, int color, float alpha) {
		float px = f.px();
		float w = f.w();
		int line = ColorUtil.fade(color, alpha);
		int glow = ColorUtil.fade(color, alpha * 0.18f);
		int facet = ColorUtil.fade(color, alpha * 0.28f);
		for (int i = 0; i + 1 < ridge.size(); i++) {
			float x0 = ridge.x(i) + dx;
			float y0 = ridge.y(i);
			float x1 = ridge.x(i + 1) + dx;
			float y1 = ridge.y(i + 1);
			if (x1 < -4 || x0 > w + 4) {
				continue;
			}
			b.line(px, x0, y0, x1, y1, px * 6, glow);
			b.line(px, x0, y0, x1, y1, px * 1.4f, line);
			if (i > 0 && y0 < ridge.y(i - 1) && y0 < y1 && horizon - y0 > f.h() * 0.02f) {
				float rise = horizon - y0;
				b.line(px, x0, y0, x0 - rise * 0.35f, horizon, px, facet);
				b.line(px, x0, y0, x0 + rise * 0.5f, horizon, px, facet);
			}
		}
	}

	/** The perspective grid: rows slide towards the viewer, columns converge on the sun. */
	private static void grid(Render2D.Batch b, Frame f, float vanishX, float horizon) {
		float w = f.w();
		float h = f.h();
		float px = f.px();
		float depth = h - horizon;
		b.vertical(0, horizon, w, depth + 1, 0xFF1A0738, 0xFF06010F);
		// Rows: depth z maps to y = horizon + depth / z.
		float scroll = (float) ((f.t() * 0.75) % 1.0);
		for (int k = -2; k < 40; k++) {
			float z = 0.82f + (k + 1 - scroll) * 0.42f;
			if (z <= 0.05f) {
				continue;
			}
			float y = horizon + depth * 0.82f / z;
			float near = (y - horizon) / depth;
			float thick = px * (0.8f + 2.2f * Math.min(near, 1.3f));
			float g = thick * 3.5f;
			if (y - g > h) {
				continue;
			}
			float a = Noise.smooth(0.02f, 0.35f, near);
			if (a <= 0.01f) {
				break;
			}
			int core = ColorUtil.fade(PINK, 0.9f * a);
			int glow = ColorUtil.fade(PINK, 0.22f * a);
			b.vertical(0, y - g, w, g, glow & 0xFFFFFF, glow);
			b.vertical(0, y + thick, w, g, glow, glow & 0xFFFFFF);
			b.rect(0, y, w, thick, core, core, core, core);
		}
		// Columns from the bottom edge to the vanishing point, fading out with distance.
		float spacing = h * 0.16f;
		float shift = f.shiftX(0.9f);
		int n = (int) Math.ceil((w + Math.abs(vanishX - w * 0.5f)) / spacing) + 6;
		for (int j = -n; j <= n; j++) {
			float bx = vanishX + j * spacing + shift;
			float farX = vanishX + (bx - vanishX) * 0.06f;
			float farY = horizon + depth * 0.06f;
			int segments = 5;
			for (int s = 0; s < segments; s++) {
				float t0 = s / (float) segments;
				float t1 = (s + 1f) / segments;
				// Ease so that most of the fade happens near the horizon.
				float x0 = bx + (farX - bx) * t0;
				float y0 = h + (farY - h) * t0;
				float x1 = bx + (farX - bx) * t1;
				float y1 = h + (farY - h) * t1;
				if (Math.max(x0, x1) < -10 || Math.min(x0, x1) > w + 10) {
					continue;
				}
				float a = 1 - t0 * 0.85f;
				float width = px * (2.2f - 1.4f * t0);
				b.line(px, x0, y0, x1, y1, width * 3.5f, ColorUtil.fade(PINK, 0.16f * a));
				b.line(px, x0, y0, x1, y1, width, ColorUtil.fade(PINK, 0.85f * a));
			}
		}
	}

	// ======================================================================= palms and effects

	/** A palm tree silhouette: a curved tapering trunk and drooping fronds made of leaflets. */
	private static void palm(Render2D.Batch b, Frame f, float baseX, float baseY, float height, float lean, float seed) {
		float px = f.px();
		float t = f.time();
		float topX = baseX + lean;
		float topY = baseY - height;
		float ctrlX = baseX + lean * 0.05f;
		float ctrlY = baseY - height * 0.55f;
		int segments = 22;
		float prevLX = 0, prevLY = 0, prevRX = 0, prevRY = 0;
		for (int i = 0; i <= segments; i++) {
			float s = i / (float) segments;
			float mx = (1 - s) * (1 - s) * baseX + 2 * (1 - s) * s * ctrlX + s * s * topX;
			float my = (1 - s) * (1 - s) * baseY + 2 * (1 - s) * s * ctrlY + s * s * topY;
			float tx = 2 * (1 - s) * (ctrlX - baseX) + 2 * s * (topX - ctrlX);
			float ty = 2 * (1 - s) * (ctrlY - baseY) + 2 * s * (topY - ctrlY);
			float len = (float) Math.sqrt(tx * tx + ty * ty);
			float nx = -ty / len;
			float ny = tx / len;
			float half = height * (0.022f - 0.011f * s);
			float lx = mx + nx * half;
			float ly = my + ny * half;
			float rx = mx - nx * half;
			float ry = my - ny * half;
			if (i > 0) {
				b.quad(prevLX, prevLY, SILHOUETTE, lx, ly, SILHOUETTE, rx, ry, SILHOUETTE, prevRX, prevRY, SILHOUETTE);
			}
			prevLX = lx;
			prevLY = ly;
			prevRX = rx;
			prevRY = ry;
		}
		// Fronds fan out from the crown and droop under their weight; the outer ones hang lower.
		// Each is a solid blade, fuller on its lower side, with leaflet tips hanging from it.
		int fronds = 9;
		float length = height * 0.42f;
		for (int k = 0; k < fronds; k++) {
			float spread = (k - (fronds - 1) / 2f) / ((fronds - 1) / 2f);
			double angle = -Math.PI / 2 + spread * 2.2 + Math.sin(t * 0.9 + seed + k * 0.7) * 0.05;
			float dirX = (float) Math.cos(angle);
			float dirY = (float) Math.sin(angle);
			float droop = 0.5f + 0.5f * Math.abs(spread);
			float frondLen = length * (0.8f + 0.2f * (1 - Math.abs(spread)));
			int steps = 20;
			float edgeUX = topX, edgeUY = topY, edgeLX = topX, edgeLY = topY;
			for (int i = 1; i <= steps; i++) {
				float s = i / (float) steps;
				float cx = topX + dirX * frondLen * s;
				float cy = topY + dirY * frondLen * s + droop * frondLen * s * s;
				float tx = dirX;
				float ty = dirY + 2 * droop * s;
				float tl = (float) Math.sqrt(tx * tx + ty * ty);
				tx /= tl;
				ty /= tl;
				float nx = -ty;
				float ny = tx;
				float down = ny >= 0 ? 1 : -1;
				float width = frondLen * 0.1f * (float) Math.sin(Math.PI * Math.pow(s, 0.75));
				float lx = cx + nx * width * down;
				float ly = cy + ny * width * down;
				float ux = cx - nx * width * 0.5f * down;
				float uy = cy - ny * width * 0.5f * down;
				b.quad(edgeUX, edgeUY, SILHOUETTE, ux, uy, SILHOUETTE, lx, ly, SILHOUETTE, edgeLX, edgeLY, SILHOUETTE);
				if (i < steps) {
					float leaf = frondLen * 0.1f * (1 - s * 0.55f);
					float ldx = nx * down * 0.7f - tx * 0.45f;
					float ldy = ny * down * 0.7f - ty * 0.45f + 0.5f;
					float ll = (float) Math.sqrt(ldx * ldx + ldy * ldy);
					b.line(px, lx, ly, lx + ldx / ll * leaf, ly + ldy / ll * leaf, px * 2f, SILHOUETTE);
					b.line(px, ux, uy, ux - nx * down * leaf * 0.35f - tx * leaf * 0.3f,
						uy - ny * down * leaf * 0.35f - ty * leaf * 0.3f, px * 1.3f, SILHOUETTE);
				}
				edgeUX = ux;
				edgeUY = uy;
				edgeLX = lx;
				edgeLY = ly;
			}
		}
		b.disc(topX, topY, height * 0.025f, SILHOUETTE, SILHOUETTE, 12);
	}

	/** Faint CRT scanlines and a slow rolling bar, just enough to feel like an old tape. */
	private static void scanlines(Render2D.Batch b, Frame f) {
		float w = f.w();
		float h = f.h();
		float px = f.px();
		float gap = px * 3;
		int line = 0x12000000;
		for (float y = 0; y < h; y += gap) {
			b.rect(0, y, w, px, line, line, line, line);
		}
		float roll = (float) (((f.t() * 0.06) % 1.3) - 0.15) * h;
		int bright = 0x0AFFFFFF;
		b.vertical(0, roll - h * 0.05f, w, h * 0.05f, 0x00FFFFFF, bright);
		b.vertical(0, roll, w, h * 0.05f, bright, 0x00FFFFFF);
	}
}
