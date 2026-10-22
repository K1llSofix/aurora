package dev.aurora.gui.background;

import dev.aurora.gui.render.Render2D;
import dev.aurora.util.ColorUtil;

import java.util.Arrays;

/**
 * A silhouette line (mountains, hills, a forest skyline) stored as points with increasing x. It is
 * filled downwards as columns with a one-pixel fading fringe on top, so even a jagged treeline is
 * anti-aliased, and can be drawn mirrored as a reflection in water.
 */
final class Ridge {
	private float[] xs = new float[512];
	private float[] ys = new float[512];
	private int n;
	private float minY;
	private float maxY;

	Ridge clear() {
		n = 0;
		minY = Float.MAX_VALUE;
		maxY = -Float.MAX_VALUE;
		return this;
	}

	void add(float x, float y) {
		if (n == xs.length) {
			xs = Arrays.copyOf(xs, n * 2);
			ys = Arrays.copyOf(ys, n * 2);
		}
		xs[n] = x;
		ys[n] = y;
		n++;
		minY = Math.min(minY, y);
		maxY = Math.max(maxY, y);
	}

	int size() {
		return n;
	}

	float x(int i) {
		return xs[i];
	}

	float y(int i) {
		return ys[i];
	}

	/** The highest point (smallest y). */
	float top() {
		return minY;
	}

	float bottom() {
		return maxY;
	}

	/** Height of the line at x, interpolated between points. */
	float yAt(float x) {
		if (n == 0) {
			return 0;
		}
		int lo = 0;
		int hi = n - 1;
		if (x <= xs[0]) {
			return ys[0];
		}
		if (x >= xs[hi]) {
			return ys[hi];
		}
		while (hi - lo > 1) {
			int mid = (lo + hi) >>> 1;
			if (xs[mid] <= x) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		float span = xs[hi] - xs[lo];
		float t = span <= 0 ? 0 : (x - xs[lo]) / span;
		return ys[lo] + (ys[hi] - ys[lo]) * t;
	}

	private static int shade(float y, float yTop, int top, float yLow, int low) {
		return ColorUtil.lerp(top, low, (y - yTop) / (yLow - yTop));
	}

	private static float fringe(Frame f, float x0, float y0, float x1, float y1) {
		float slope = (y1 - y0) / Math.max(1e-4f, x1 - x0);
		return Math.min(f.px() * (float) Math.sqrt(1 + slope * slope), f.px() * 8);
	}

	/**
	 * Fills everything below the line down to {@code bottom}, moved by (dx, dy). The colour is a
	 * screen-space gradient from {@code top} at {@code yTop} to {@code low} at {@code yLow}, so
	 * valleys take on the lower colour like haze settling between hills. Each column is split at
	 * {@code yLow}: above it the gradient is linear and interpolates exactly, below it the colour
	 * is flat, so neighbouring columns of different height never show seams.
	 */
	void fill(Render2D.Batch b, Frame f, float dx, float dy, float bottom, float yTop, int top, float yLow, int low) {
		float split = Math.min(bottom, yLow);
		// Columns whose flat part starts exactly at yLow share one rectangle down to the bottom.
		float runStart = Float.NaN;
		float runEnd = 0;
		for (int i = 0; i + 1 < n; i++) {
			float x0 = xs[i] + dx;
			float x1 = xs[i + 1] + dx;
			if (x1 < 0 || x0 > f.w() || x1 <= x0) {
				continue;
			}
			float y0 = ys[i] + dy;
			float y1 = ys[i + 1] + dy;
			int c0 = shade(y0, yTop, top, yLow, low);
			int c1 = shade(y1, yTop, top, yLow, low);
			float s0 = Math.min(bottom, Math.max(y0, yLow));
			float s1 = Math.min(bottom, Math.max(y1, yLow));
			if (y0 < s0 || y1 < s1) {
				b.quad(x0, y0, c0, x1, y1, c1, x1, s1, low, x0, s0, low);
			}
			if (s0 == split && s1 == split) {
				if (Float.isNaN(runStart)) {
					runStart = x0;
				}
				runEnd = x1;
			} else {
				if (!Float.isNaN(runStart)) {
					b.rect(runStart, split, runEnd - runStart, bottom - split, low, low, low, low);
					runStart = Float.NaN;
				}
				b.quad(x0, s0, low, x1, s1, low, x1, bottom, low, x0, bottom, low);
			}
			float fr = fringe(f, x0, y0, x1, y1);
			b.quad(x0, y0 - fr, c0 & 0xFFFFFF, x1, y1 - fr, c1 & 0xFFFFFF, x1, y1, c1, x0, y0, c0);
		}
		if (!Float.isNaN(runStart)) {
			b.rect(runStart, split, runEnd - runStart, bottom - split, low, low, low, low);
		}
	}

	/**
	 * The mirror image of {@link #fill} in water whose surface is at {@code water}. A {@code squash}
	 * below 1 shortens the reflection, as seen from a viewpoint above the water.
	 */
	void reflect(Render2D.Batch b, Frame f, float dx, float dy, float water, float squash,
				 float yTop, int top, float yLow, int low, float alpha) {
		int flat = ColorUtil.fade(low, alpha);
		float split = water + (water - Math.min(water, yLow)) * squash;
		float runStart = Float.NaN;
		float runEnd = 0;
		for (int i = 0; i + 1 < n; i++) {
			float x0 = xs[i] + dx;
			float x1 = xs[i + 1] + dx;
			if (x1 < 0 || x0 > f.w() || x1 <= x0) {
				continue;
			}
			float y0 = Math.min(water, ys[i] + dy);
			float y1 = Math.min(water, ys[i + 1] + dy);
			float r0 = water + (water - y0) * squash;
			float r1 = water + (water - y1) * squash;
			// Where the gradient ends, mirrored; between there and the surface the colour is flat.
			float m0 = water + (water - Math.min(water, Math.max(y0, yLow))) * squash;
			float m1 = water + (water - Math.min(water, Math.max(y1, yLow))) * squash;
			int c0 = ColorUtil.fade(shade(y0, yTop, top, yLow, low), alpha);
			int c1 = ColorUtil.fade(shade(y1, yTop, top, yLow, low), alpha);
			if (m0 == split && m1 == split) {
				if (Float.isNaN(runStart)) {
					runStart = x0;
				}
				runEnd = x1;
			} else {
				if (!Float.isNaN(runStart)) {
					b.rect(runStart, water, runEnd - runStart, split - water, flat, flat, flat, flat);
					runStart = Float.NaN;
				}
				b.quad(x0, water, flat, x1, water, flat, x1, m1, flat, x0, m0, flat);
			}
			if (r0 > m0 || r1 > m1) {
				b.quad(x0, m0, flat, x1, m1, flat, x1, r1, c1, x0, r0, c0);
			}
			float fr = fringe(f, x0, r0, x1, r1);
			b.quad(x0, r0, c0, x1, r1, c1, x1, r1 + fr, c1 & 0xFFFFFF, x0, r0 + fr, c0 & 0xFFFFFF);
		}
		if (!Float.isNaN(runStart)) {
			b.rect(runStart, water, runEnd - runStart, split - water, flat, flat, flat, flat);
		}
	}

	// ======================================================================= shapes

	/**
	 * Height above the ground of a stylised spruce at horizontal distance {@code d} from its trunk:
	 * the upper outline of {@code tiers} stacked triangles, which gives the stepped branch layers.
	 * Negative outside the tree.
	 */
	static float spruce(float d, float halfWidth, float height, int tiers) {
		d = Math.abs(d);
		if (d >= halfWidth) {
			return -1;
		}
		float best = -1;
		for (int k = 0; k < tiers; k++) {
			float frac = (k + 1f) / tiers;
			float tierW = halfWidth * (0.32f + 0.68f * frac);
			if (d >= tierW) {
				continue;
			}
			float tierTop = height * (1 - k * (0.82f / tiers));
			float tierBottom = height * (1 - frac) * 0.9f;
			best = Math.max(best, tierTop - (tierTop - tierBottom) * (d / tierW));
		}
		return best;
	}

	/** Trees standing on a ridge, for {@link #forest}. */
	record Tree(float x, float halfWidth, float height, int tiers) {
	}

	/**
	 * Builds a skyline from the ground line {@code ground} plus trees: at every sample the higher
	 * of the ground and any tree. Tree tips are sampled exactly so they stay sharp.
	 */
	void forest(Ridge ground, java.util.List<Tree> trees, float x0, float x1, float step) {
		clear();
		int next = 0;
		float x = x0;
		while (x <= x1 + step) {
			// Insert the exact tip of any tree we are about to pass.
			while (next < trees.size() && trees.get(next).x() < x) {
				Tree tree = trees.get(next++);
				if (tree.x() > x - step) {
					add(tree.x(), skyline(ground, trees, tree.x(), next - 1));
				}
			}
			add(x, skyline(ground, trees, x, next));
			x += step;
		}
	}

	private static float skyline(Ridge ground, java.util.List<Tree> trees, float x, int near) {
		float y = ground.yAt(x);
		int from = Math.max(0, near - 6);
		int to = Math.min(trees.size(), near + 6);
		for (int i = from; i < to; i++) {
			Tree tree = trees.get(i);
			float above = spruce(x - tree.x(), tree.halfWidth(), tree.height(), tree.tiers());
			if (above >= 0) {
				y = Math.min(y, ground.yAt(tree.x()) - above);
			}
		}
		return y;
	}
}
