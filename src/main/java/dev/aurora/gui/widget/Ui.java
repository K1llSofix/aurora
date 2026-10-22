package dev.aurora.gui.widget;

import dev.aurora.gui.Theme;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Drawing helpers for the menus. {@link #alpha} is a global opacity multiplier the screen sets
 * while it fades pages in and out, so widgets never need to know about transitions.
 */
public final class Ui {
	public static float alpha = 1f;

	private Ui() {
	}

	public static int c(int color) {
		return alpha >= 1f ? color : ColorUtil.fade(color, alpha);
	}

	public static boolean inside(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	public static Render2D.Paint accentPaint(float x, float w) {
		return Render2D.Paint.horizontal(x, w, c(Theme.accent()), c(Theme.accent2()));
	}

	public static Render2D.Paint accentPaint(float x, float w, float strength) {
		return Render2D.Paint.horizontal(x, w, c(ColorUtil.fade(Theme.accent(), strength)), c(ColorUtil.fade(Theme.accent2(), strength)));
	}

	/** Text with a horizontal gradient, drawn glyph by glyph. */
	public static float gradientText(GuiGraphicsExtractor g, FontRenderer font, String text, float x, float centerY, float size, int from, int to) {
		float total = font.width(text, size);
		float pen = x;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			String ch = new String(Character.toChars(cp));
			i += Character.charCount(cp);
			float cw = font.width(ch, size);
			float t = total <= 0 ? 0 : (pen - x + cw / 2f) / total;
			font.drawMiddle(g, ch, pen, centerY, size, c(ColorUtil.lerp(from, to, t)));
			pen += cw;
		}
		return total;
	}

	// ---------------------------------------------------------------- icons (all centred on cx, cy)

	public static void iconGrid(GuiGraphicsExtractor g, float cx, float cy, float s, int color) {
		float cell = s * 0.42f;
		float gap = s * 0.16f;
		float x0 = cx - cell - gap / 2f;
		float y0 = cy - cell - gap / 2f;
		for (int i = 0; i < 4; i++) {
			float x = x0 + (i % 2) * (cell + gap);
			float y = y0 + (i / 2) * (cell + gap);
			Render2D.roundRect(g, x, y, cell, cell, cell * 0.3f, c(color));
		}
	}

	public static void iconHud(GuiGraphicsExtractor g, float cx, float cy, float s, int color) {
		float w = s;
		float h = s * 0.78f;
		Render2D.roundOutline(g, cx - w / 2f, cy - h / 2f, w, h, s * 0.18f, s * 0.11f, c(color));
		Render2D.roundRect(g, cx - w * 0.3f, cy - h * 0.18f, w * 0.26f, s * 0.11f, s * 0.05f, c(color));
		Render2D.roundRect(g, cx - w * 0.3f, cy + h * 0.06f, w * 0.42f, s * 0.11f, s * 0.05f, c(color));
	}

	public static void iconEye(GuiGraphicsExtractor g, float cx, float cy, float s, int color) {
		float r = s * 0.62f;
		float lw = s * 0.11f;
		Render2D.arc(g, cx, cy + r * 0.45f, r, lw, 215, 110, c(color));
		Render2D.arc(g, cx, cy - r * 0.45f, r, lw, 35, 110, c(color));
		Render2D.circle(g, cx, cy, s * 0.17f, c(color));
	}

	public static void iconBolt(GuiGraphicsExtractor g, float cx, float cy, float s, int color) {
		float h = s / 2f;
		Render2D.polyline(g, s * 0.12f, c(color),
			cx + h * 0.25f, cy - h, cx - h * 0.45f, cy + h * 0.1f, cx + h * 0.35f, cy + h * 0.05f, cx - h * 0.2f, cy + h);
	}

	public static void iconKeyboard(GuiGraphicsExtractor g, float cx, float cy, float s, int color) {
		float w = s * 1.1f;
		float h = s * 0.72f;
		float x = cx - w / 2f;
		float y = cy - h / 2f;
		Render2D.roundOutline(g, x, y, w, h, s * 0.16f, s * 0.1f, c(color));
		float key = s * 0.12f;
		for (int i = 0; i < 4; i++) {
			Render2D.roundRect(g, x + w * (0.2f + i * 0.2f) - key / 2f, y + h * 0.34f - key / 2f, key, key, key * 0.3f, c(color));
		}
		Render2D.roundRect(g, x + w * 0.28f, y + h * 0.62f, w * 0.44f, key, key * 0.4f, c(color));
	}

	public static void iconGear(GuiGraphicsExtractor g, float cx, float cy, float s, int color) {
		float r = s * 0.3f;
		Render2D.ring(g, cx, cy, r, s * 0.13f, c(color));
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * 2 * i / 8;
			float x1 = cx + (float) Math.cos(a) * (r + s * 0.05f);
			float y1 = cy + (float) Math.sin(a) * (r + s * 0.05f);
			float x2 = cx + (float) Math.cos(a) * (r + s * 0.2f);
			float y2 = cy + (float) Math.sin(a) * (r + s * 0.2f);
			Render2D.line(g, x1, y1, x2, y2, s * 0.14f, c(color));
		}
	}

	public static void iconMove(GuiGraphicsExtractor g, float cx, float cy, float s, int color) {
		float h = s / 2f;
		float lw = s * 0.11f;
		Render2D.line(g, cx - h, cy, cx + h, cy, lw, c(color));
		Render2D.line(g, cx, cy - h, cx, cy + h, lw, c(color));
		float a = s * 0.2f;
		Render2D.polyline(g, lw, c(color), cx - a, cy - h + a, cx, cy - h, cx + a, cy - h + a);
		Render2D.polyline(g, lw, c(color), cx - a, cy + h - a, cx, cy + h, cx + a, cy + h - a);
		Render2D.polyline(g, lw, c(color), cx - h + a, cy - a, cx - h, cy, cx - h + a, cy + a);
		Render2D.polyline(g, lw, c(color), cx + h - a, cy - a, cx + h, cy, cx + h - a, cy + a);
	}

	public static void iconSearch(GuiGraphicsExtractor g, float cx, float cy, float s, int color) {
		float r = s * 0.32f;
		float ox = cx - s * 0.08f;
		float oy = cy - s * 0.08f;
		Render2D.ring(g, ox, oy, r, s * 0.12f, c(color));
		Render2D.line(g, ox + r * 0.75f, oy + r * 0.75f, cx + s * 0.42f, cy + s * 0.42f, s * 0.13f, c(color));
	}

	public static void iconReset(GuiGraphicsExtractor g, float cx, float cy, float s, int color) {
		float r = s * 0.36f;
		Render2D.arc(g, cx, cy, r, s * 0.12f, -60, 290, c(color));
		double a = Math.toRadians(-60);
		float ax = cx + (float) Math.cos(a) * r;
		float ay = cy + (float) Math.sin(a) * r;
		Render2D.triangle(g, ax - s * 0.2f, ay - s * 0.02f, ax + s * 0.12f, ay - s * 0.16f, ax + s * 0.06f, ay + s * 0.2f, c(color));
	}

	public static void iconClose(GuiGraphicsExtractor g, float cx, float cy, float s, int color) {
		Render2D.cross(g, cx, cy, s * 0.6f, s * 0.12f, c(color));
	}

	/** Two sheets: copy to the clipboard. */
	public static void iconCopy(GuiGraphicsExtractor g, float cx, float cy, float s, int color) {
		float w = s * 0.52f;
		float h = s * 0.62f;
		float lw = s * 0.1f;
		float fx = cx - w / 2f + s * 0.12f;
		float fy = cy - h / 2f + s * 0.12f;
		Render2D.roundOutline(g, fx, fy, w, h, s * 0.1f, lw, c(color));
		Render2D.polyline(g, lw, c(color), fx - s * 0.22f, fy + h - s * 0.2f, fx - s * 0.22f, fy - s * 0.22f, fx + w - s * 0.2f, fy - s * 0.22f);
	}

	/** Stacked layers: profiles. */
	public static void iconLayers(GuiGraphicsExtractor g, float cx, float cy, float s, int color) {
		float w = s * 0.5f;
		float h = s * 0.22f;
		float lw = s * 0.1f;
		float y0 = cy - s * 0.2f;
		Render2D.polyline(g, lw, c(color), cx - w, y0, cx, y0 - h, cx + w, y0, cx, y0 + h, cx - w, y0);
		Render2D.polyline(g, lw, c(color), cx - w, y0 + s * 0.24f, cx, y0 + h + s * 0.24f, cx + w, y0 + s * 0.24f);
		Render2D.polyline(g, lw, c(color), cx - w, y0 + s * 0.46f, cx, y0 + h + s * 0.46f, cx + w, y0 + s * 0.46f);
	}

	/** A speedometer: performance. */
	public static void iconGauge(GuiGraphicsExtractor g, float cx, float cy, float s, int color) {
		float r = s * 0.42f;
		float oy = cy + s * 0.1f;
		Render2D.arc(g, cx, oy, r, s * 0.11f, 150, 240, c(color));
		double a = Math.toRadians(-40);
		Render2D.line(g, cx, oy, cx + (float) Math.cos(a) * r * 0.72f, oy + (float) Math.sin(a) * r * 0.72f, s * 0.12f, c(color));
		Render2D.circle(g, cx, oy, s * 0.09f, c(color));
	}

	/** An arrow into a tray: save. */
	public static void iconSave(GuiGraphicsExtractor g, float cx, float cy, float s, int color) {
		float lw = s * 0.11f;
		Render2D.line(g, cx, cy - s * 0.42f, cx, cy + s * 0.1f, lw, c(color));
		Render2D.polyline(g, lw, c(color), cx - s * 0.22f, cy - s * 0.1f, cx, cy + s * 0.12f, cx + s * 0.22f, cy - s * 0.1f);
		Render2D.polyline(g, lw, c(color), cx - s * 0.42f, cy + s * 0.08f, cx - s * 0.42f, cy + s * 0.4f, cx + s * 0.42f, cy + s * 0.4f, cx + s * 0.42f, cy + s * 0.08f);
	}

	/** The Aurora mark: a gradient tile with a four-point spark that breathes slowly. */
	public static void logo(GuiGraphicsExtractor g, float cx, float cy, float s) {
		float half = s / 2f;
		Render2D.glow(g, cx - half, cy - half, s, s, s * 0.3f, s * 0.45f, c(ColorUtil.fade(Theme.accent(0.5f), 0.35f)));
		Render2D.roundRect(g, cx - half, cy - half, s, s, s * 0.3f,
			Render2D.Paint.diagonal(cx - half, cy - half, s, s, c(Theme.accent()), c(Theme.accent2())));
		float t = (float) (System.nanoTime() / 1e9);
		float pulse = 1f + 0.06f * (float) Math.sin(t * 2.2);
		spark(g, cx, cy, s * 0.36f * pulse, s * 0.09f, c(0xFFFFFFFF));
		spark(g, cx + s * 0.24f, cy - s * 0.24f, s * 0.11f, s * 0.03f, c(0xD9FFFFFF));
	}

	/** A four-point star: long tips at 90° steps, pinched between them. */
	public static void spark(GuiGraphicsExtractor g, float cx, float cy, float r, float waist, int color) {
		float[] pts = new float[16];
		for (int i = 0; i < 8; i++) {
			double a = -Math.PI / 2 + Math.PI / 4 * i;
			float rad = (i % 2 == 0) ? r : waist;
			pts[i * 2] = cx + (float) Math.cos(a) * rad;
			pts[i * 2 + 1] = cy + (float) Math.sin(a) * rad;
		}
		Render2D.polygon(g, cx, cy, pts, color);
	}
}
