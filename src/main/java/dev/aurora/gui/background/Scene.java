package dev.aurora.gui.background;

import dev.aurora.gui.render.Render2D;
import dev.aurora.util.ColorUtil;

/** An animated, procedurally drawn menu background. */
interface Scene {
	void render(Frame f);

	/** A vertical gradient through several colour stops; {@code stops} are fractions of the height. */
	static void sky(Render2D.Batch b, Frame f, float[] stops, int[] colors) {
		for (int i = 0; i + 1 < stops.length; i++) {
			float y0 = stops[i] * f.h();
			float y1 = stops[i + 1] * f.h();
			b.vertical(0, y0, f.w(), y1 - y0, colors[i], colors[i + 1]);
		}
	}

	/** Darkens the edges of the picture, which pulls the eye to the middle like a camera lens. */
	static void vignette(Render2D.Batch b, Frame f, int color, float top, float bottom, float sides) {
		float w = f.w();
		float h = f.h();
		int clear = color & 0xFFFFFF;
		if (top > 0) {
			b.vertical(0, 0, w, h * 0.2f, ColorUtil.fade(color, top), clear);
		}
		if (bottom > 0) {
			b.vertical(0, h * 0.72f, w, h * 0.28f + 1, clear, ColorUtil.fade(color, bottom));
		}
		if (sides > 0) {
			float sw = Math.min(w * 0.16f, h * 0.3f);
			int edge = ColorUtil.fade(color, sides);
			b.rect(0, 0, sw, h, edge, clear, clear, edge);
			b.rect(w - sw, 0, sw, h, clear, edge, edge, clear);
		}
	}
}
