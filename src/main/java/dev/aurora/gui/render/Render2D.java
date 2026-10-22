package dev.aurora.gui.render;

import dev.aurora.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2f;

/**
 * Resolution independent vector drawing for the GUI: rounded rectangles, circles, arcs and lines
 * with one-pixel anti-aliased edges (the same "fringe" technique NanoVG uses), plus soft shadows.
 * All coordinates are float GUI units, so animated shapes move smoothly instead of snapping.
 */
public final class Render2D {
	private static final ShapeBuilder BUILDER = new ShapeBuilder();

	/**
	 * Multiplies the alpha of everything Render2D draws, e.g. to fade a whole animated scene in or
	 * out. Always restore it to 1 afterwards.
	 */
	public static float globalAlpha = 1f;
	private static float[] pointsA = new float[256];
	private static float[] pointsB = new float[256];

	private Render2D() {
	}

	/** Paints a colour as a function of position, which makes linear gradients exact. */
	@FunctionalInterface
	public interface Paint {
		int at(float x, float y);

		static Paint solid(int color) {
			return (x, y) -> color;
		}

		static Paint vertical(float top, float height, int topColor, int bottomColor) {
			return (x, y) -> ColorUtil.lerp(topColor, bottomColor, (y - top) / height);
		}

		static Paint horizontal(float left, float width, int leftColor, int rightColor) {
			return (x, y) -> ColorUtil.lerp(leftColor, rightColor, (x - left) / width);
		}

		static Paint diagonal(float left, float top, float width, float height, int from, int to) {
			return (x, y) -> ColorUtil.lerp(from, to, ((x - left) / width + (y - top) / height) * 0.5f);
		}
	}

	/** Size of one physical pixel in local GUI units, taking the current pose scale into account. */
	public static float pixel(GuiGraphicsExtractor g) {
		return 1f / (Minecraft.getInstance().getWindow().getGuiScale() * poseScale(g));
	}

	public static float poseScale(GuiGraphicsExtractor g) {
		Matrix3x2f m = g.pose();
		float sx = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01());
		float sy = (float) Math.sqrt(m.m10() * m.m10() + m.m11() * m.m11());
		return Math.max((sx + sy) * 0.5f, 0.001f);
	}

	// ---------------------------------------------------------------- rectangles

	private static final ShapeBuilder BATCH_BUILDER = new ShapeBuilder();

	/**
	 * Collects many shapes into a single GUI element (e.g. hundreds of strips or particles). It has
	 * its own buffer, so ordinary Render2D calls made while a batch is open do not disturb it.
	 */
	public static Batch batch() {
		return new Batch(BATCH_BUILDER.reset());
	}

	public static final class Batch {
		private final ShapeBuilder builder;

		private Batch(ShapeBuilder builder) {
			this.builder = builder;
		}

		/** Corner colours clockwise from the top-left. */
		public Batch rect(float x, float y, float w, float h, int topLeft, int topRight, int bottomRight, int bottomLeft) {
			if (w > 0 && h > 0) {
				builder.convexQuad(x, y, topLeft, x, y + h, bottomLeft, x + w, y + h, bottomRight, x + w, y, topRight);
			}
			return this;
		}

		public Batch vertical(float x, float y, float w, float h, int top, int bottom) {
			return rect(x, y, w, h, top, top, bottom, bottom);
		}

		/** A convex quad (in either winding order). */
		public Batch quad(float ax, float ay, int ca, float bx, float by, int cb, float cx, float cy, int cc, float dx, float dy, int cd) {
			builder.convexQuad(ax, ay, ca, bx, by, cb, cx, cy, cc, dx, dy, cd);
			return this;
		}

		public Batch triangle(float ax, float ay, int ca, float bx, float by, int cb, float cx, float cy, int cc) {
			builder.triangle(ax, ay, ca, bx, by, cb, cx, cy, cc);
			return this;
		}

		/** An anti-aliased line; {@code aa} is one physical pixel ({@link #pixel}). */
		public Batch line(float aa, float x1, float y1, float x2, float y2, float width, int color) {
			addLine(builder, aa, x1, y1, x2, y2, width, color);
			return this;
		}

		/** An anti-aliased star-shaped polygon, points clockwise on screen (see {@link Render2D#polygon}). */
		public Batch polygon(float aa, float cx, float cy, float[] pts, int color) {
			addPolygon(builder, aa, cx, cy, pts, color);
			return this;
		}

		/** A disc with a radial gradient from the centre colour to the rim colour (soft lights). */
		public Batch disc(float cx, float cy, float radius, int center, int rim, int segments) {
			double step = Math.PI * 2 / segments;
			for (int i = 0; i < segments; i++) {
				float x0 = cx + (float) Math.cos(step * i) * radius;
				float y0 = cy + (float) Math.sin(step * i) * radius;
				float x1 = cx + (float) Math.cos(step * (i + 1)) * radius;
				float y1 = cy + (float) Math.sin(step * (i + 1)) * radius;
				builder.triangle(cx, cy, center, x0, y0, rim, x1, y1, rim);
			}
			return this;
		}

		/** A band between two circles, colour fading from the inner to the outer radius (halos). */
		public Batch ring(float cx, float cy, float inner, float outer, int innerColor, int outerColor, int segments) {
			double step = Math.PI * 2 / segments;
			for (int i = 0; i < segments; i++) {
				float c0 = (float) Math.cos(step * i), s0 = (float) Math.sin(step * i);
				float c1 = (float) Math.cos(step * (i + 1)), s1 = (float) Math.sin(step * (i + 1));
				builder.quad(cx + c0 * inner, cy + s0 * inner, innerColor, cx + c0 * outer, cy + s0 * outer, outerColor,
					cx + c1 * outer, cy + s1 * outer, outerColor, cx + c1 * inner, cy + s1 * inner, innerColor);
			}
			return this;
		}

		public void submit(GuiGraphicsExtractor g) {
			builder.submit(g);
		}
	}

	/** A crisp axis-aligned rectangle without anti-aliasing. */
	public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
			return;
		}
		ShapeBuilder b = BUILDER.reset();
		b.quad(x, y, color, x, y + h, color, x + w, y + h, color, x + w, y, color);
		b.submit(g);
	}

	public static void gradientRect(GuiGraphicsExtractor g, float x, float y, float w, float h, int topLeft, int topRight, int bottomRight, int bottomLeft) {
		if (w <= 0 || h <= 0) {
			return;
		}
		ShapeBuilder b = BUILDER.reset();
		b.quad(x, y, topLeft, x, y + h, bottomLeft, x + w, y + h, bottomRight, x + w, y, topRight);
		b.submit(g);
	}

	public static void verticalGradient(GuiGraphicsExtractor g, float x, float y, float w, float h, int top, int bottom) {
		gradientRect(g, x, y, w, h, top, top, bottom, bottom);
	}

	public static void horizontalGradient(GuiGraphicsExtractor g, float x, float y, float w, float h, int left, int right) {
		gradientRect(g, x, y, w, h, left, right, right, left);
	}

	public static void roundRect(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int color) {
		roundRect(g, x, y, w, h, r, r, r, r, Paint.solid(color));
	}

	public static void roundRect(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, Paint paint) {
		roundRect(g, x, y, w, h, r, r, r, r, paint);
	}

	/** Radii are given clockwise from the top-left corner. */
	public static void roundRect(GuiGraphicsExtractor g, float x, float y, float w, float h,
								 float rTL, float rTR, float rBR, float rBL, Paint paint) {
		if (w <= 0 || h <= 0) {
			return;
		}
		float aa = pixel(g);
		float max = Math.min(w, h) / 2f;
		rTL = Math.min(rTL, max);
		rTR = Math.min(rTR, max);
		rBR = Math.min(rBR, max);
		rBL = Math.min(rBL, max);
		int seg = segments(Math.max(Math.max(rTL, rTR), Math.max(rBR, rBL)) + aa, aa);

		int n = outline(0, x, y, w, h, rTL, rTR, rBR, rBL, -aa * 0.5f, seg);
		int m = outline(1, x, y, w, h, rTL, rTR, rBR, rBL, aa * 0.5f, seg);
		if (n != m) {
			return;
		}
		ShapeBuilder b = BUILDER.reset();
		float cx = x + w / 2f;
		float cy = y + h / 2f;
		int centerColor = paint.at(cx, cy);
		float[] in = pointsA;
		float[] out = pointsB;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float ax = in[i * 2], ay = in[i * 2 + 1];
			float bx = in[j * 2], by = in[j * 2 + 1];
			int ca = paint.at(ax, ay);
			int cb = paint.at(bx, by);
			b.triangle(cx, cy, centerColor, ax, ay, ca, bx, by, cb);
			float oax = out[i * 2], oay = out[i * 2 + 1];
			float obx = out[j * 2], oby = out[j * 2 + 1];
			b.quad(ax, ay, ca, oax, oay, ColorUtil.withAlpha(ca, 0), obx, oby, ColorUtil.withAlpha(cb, 0), bx, by, cb);
		}
		b.submit(g);
	}

	/** A rounded border of the given thickness that lies inside the rectangle. */
	public static void roundOutline(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float thickness, int color) {
		roundOutline(g, x, y, w, h, r, thickness, Paint.solid(color));
	}

	public static void roundOutline(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float thickness, Paint paint) {
		if (w <= 0 || h <= 0 || thickness <= 0) {
			return;
		}
		float aa = pixel(g);
		r = Math.min(r, Math.min(w, h) / 2f);
		int seg = segments(r + aa, aa);
		float[] offsets;
		float[] alphas;
		if (thickness > aa) {
			offsets = new float[]{aa * 0.5f, -aa * 0.5f, -thickness + aa * 0.5f, -thickness - aa * 0.5f};
			alphas = new float[]{0f, 1f, 1f, 0f};
		} else {
			float a = thickness / aa;
			offsets = new float[]{aa * 0.5f, -thickness * 0.5f, -thickness - aa * 0.5f};
			alphas = new float[]{0f, a, 0f};
		}
		rings(g, x, y, w, h, r, seg, offsets, alphas, paint);
	}

	/**
	 * A soft drop shadow around (not under) a rounded rectangle. The falloff is a few linear steps
	 * shaped like a gaussian, which is indistinguishable from a real blur at these sizes.
	 */
	public static void shadow(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float blur, int color) {
		if (blur <= 0 || (color >>> 24) == 0) {
			return;
		}
		float aa = pixel(g);
		r = Math.min(r, Math.min(w, h) / 2f);
		int seg = segments(r + blur, aa);
		float[] offsets = {-0.5f, blur * 0.12f, blur * 0.32f, blur * 0.6f, blur};
		float[] alphas = {1f, 0.72f, 0.38f, 0.12f, 0f};
		rings(g, x, y, w, h, r, seg, offsets, alphas, Paint.solid(color));
	}

	/** A coloured halo, e.g. behind a focused field or an enabled toggle. */
	public static void glow(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float size, int color) {
		shadow(g, x, y, w, h, r, size, color);
	}

	/** A soft filled light: the rectangle itself plus its halo, for ambient background glows. */
	public static void blob(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, float size, int color) {
		roundRect(g, x, y, w, h, r, color);
		shadow(g, x, y, w, h, r, size, color);
	}

	private static void rings(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int seg,
							  float[] offsets, float[] alphas, Paint paint) {
		ShapeBuilder b = BUILDER.reset();
		int n = outline(0, x, y, w, h, r, r, r, r, offsets[0], seg);
		for (int level = 1; level < offsets.length; level++) {
			int m = outline(1, x, y, w, h, r, r, r, r, offsets[level], seg);
			if (m != n) {
				return;
			}
			float aIn = alphas[level - 1];
			float aOut = alphas[level];
			float[] in = pointsA;
			float[] out = pointsB;
			for (int i = 0; i < n; i++) {
				int j = (i + 1) % n;
				float ax = in[i * 2], ay = in[i * 2 + 1];
				float bx = in[j * 2], by = in[j * 2 + 1];
				float oax = out[i * 2], oay = out[i * 2 + 1];
				float obx = out[j * 2], oby = out[j * 2 + 1];
				b.quad(ax, ay, ColorUtil.fade(paint.at(ax, ay), aIn),
					oax, oay, ColorUtil.fade(paint.at(oax, oay), aOut),
					obx, oby, ColorUtil.fade(paint.at(obx, oby), aOut),
					bx, by, ColorUtil.fade(paint.at(bx, by), aIn));
			}
			// The outer ring of this level is the inner ring of the next one.
			float[] tmp = pointsA;
			pointsA = pointsB;
			pointsB = tmp;
		}
		b.submit(g);
	}

	// ---------------------------------------------------------------- circles, arcs, lines

	public static void circle(GuiGraphicsExtractor g, float cx, float cy, float radius, int color) {
		roundRect(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
	}

	public static void circle(GuiGraphicsExtractor g, float cx, float cy, float radius, Paint paint) {
		roundRect(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, radius, radius, radius, paint);
	}

	public static void ring(GuiGraphicsExtractor g, float cx, float cy, float radius, float thickness, int color) {
		arc(g, cx, cy, radius, thickness, 0, 360, color);
	}

	/**
	 * An arc centred on {@code radius}, {@code thickness} wide. Angles are in degrees, 0 pointing
	 * right and positive sweeping clockwise on screen.
	 */
	public static void arc(GuiGraphicsExtractor g, float cx, float cy, float radius, float thickness, float startDeg, float sweepDeg, int color) {
		if (sweepDeg == 0 || thickness <= 0 || (color >>> 24) == 0) {
			return;
		}
		float aa = pixel(g);
		float sweep = Math.max(-360f, Math.min(360f, sweepDeg));
		int steps = Math.max(6, (int) Math.ceil(Math.abs(sweep) / 360f * Math.max(24, (radius / aa) * 0.9f)));
		steps = Math.min(steps, 160);
		float half = thickness / 2f;
		float[] radii;
		float[] alphas;
		if (thickness > aa) {
			radii = new float[]{radius + half + aa * 0.5f, radius + half - aa * 0.5f, radius - half + aa * 0.5f, radius - half - aa * 0.5f};
			alphas = new float[]{0, 1, 1, 0};
		} else {
			radii = new float[]{radius + aa * 0.5f, radius, radius - aa * 0.5f};
			alphas = new float[]{0, thickness / aa, 0};
		}
		ShapeBuilder b = BUILDER.reset();
		double start = Math.toRadians(startDeg);
		double step = Math.toRadians(sweep) / steps;
		for (int s = 0; s < steps; s++) {
			double a0 = start + step * s;
			double a1 = a0 + step;
			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			for (int k = 0; k < radii.length - 1; k++) {
				float ro = Math.max(0, radii[k]);
				float ri = Math.max(0, radii[k + 1]);
				int co = ColorUtil.fade(color, alphas[k]);
				int ci = ColorUtil.fade(color, alphas[k + 1]);
				b.quad(cx + c0 * ro, cy + s0 * ro, co,
					cx + c1 * ro, cy + s1 * ro, co,
					cx + c1 * ri, cy + s1 * ri, ci,
					cx + c0 * ri, cy + s0 * ri, ci);
			}
		}
		b.submit(g);
	}

	/** An anti-aliased line segment with square-ish ends. */
	public static void line(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float width, int color) {
		ShapeBuilder b = BUILDER.reset();
		addLine(b, pixel(g), x1, y1, x2, y2, width, color);
		b.submit(g);
	}

	/** Connected line segments; points are x0, y0, x1, y1, ... */
	public static void polyline(GuiGraphicsExtractor g, float width, int color, float... points) {
		ShapeBuilder b = BUILDER.reset();
		float aa = pixel(g);
		for (int i = 0; i + 3 < points.length; i += 2) {
			addLine(b, aa, points[i], points[i + 1], points[i + 2], points[i + 3], width, color);
		}
		b.submit(g);
	}

	private static void addLine(ShapeBuilder b, float aa, float x1, float y1, float x2, float y2, float width, int color) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1e-4f) {
			return;
		}
		dx /= len;
		dy /= len;
		// Extend the ends slightly so joints of a polyline overlap instead of leaving a notch.
		float ext = width * 0.5f;
		x1 -= dx * ext;
		y1 -= dy * ext;
		x2 += dx * ext;
		y2 += dy * ext;
		float nx = -dy;
		float ny = dx;
		float alpha = Math.min(1f, width / aa);
		float inner = Math.max(width * 0.5f - aa * 0.5f, 0f);
		float outer = Math.max(width * 0.5f, aa * 0.5f) + aa * 0.5f;
		int solid = ColorUtil.fade(color, alpha);
		int clear = ColorUtil.withAlpha(color, 0);
		if (inner > 0) {
			b.quad(x1 + nx * inner, y1 + ny * inner, solid, x2 + nx * inner, y2 + ny * inner, solid,
				x2 - nx * inner, y2 - ny * inner, solid, x1 - nx * inner, y1 - ny * inner, solid);
		}
		b.quad(x1 + nx * outer, y1 + ny * outer, clear, x2 + nx * outer, y2 + ny * outer, clear,
			x2 + nx * inner, y2 + ny * inner, solid, x1 + nx * inner, y1 + ny * inner, solid);
		b.quad(x1 - nx * inner, y1 - ny * inner, solid, x2 - nx * inner, y2 - ny * inner, solid,
			x2 - nx * outer, y2 - ny * outer, clear, x1 - nx * outer, y1 - ny * outer, clear);
	}

	/**
	 * An anti-aliased star-shaped polygon (every point visible from the centre), points listed
	 * clockwise on screen as x0, y0, x1, y1, ...
	 */
	public static void polygon(GuiGraphicsExtractor g, float cx, float cy, float[] pts, int color) {
		ShapeBuilder b = BUILDER.reset();
		addPolygon(b, pixel(g), cx, cy, pts, color);
		b.submit(g);
	}

	private static void addPolygon(ShapeBuilder b, float pixel, float cx, float cy, float[] pts, int color) {
		int n = pts.length / 2;
		if (n < 3) {
			return;
		}
		float aa = pixel * 0.5f;
		float[] inner = new float[n * 2];
		float[] outer = new float[n * 2];
		for (int i = 0; i < n; i++) {
			int p = (i + n - 1) % n;
			int q = (i + 1) % n;
			float x = pts[i * 2], y = pts[i * 2 + 1];
			float e0x = x - pts[p * 2], e0y = y - pts[p * 2 + 1];
			float e1x = pts[q * 2] - x, e1y = pts[q * 2 + 1] - y;
			float l0 = (float) Math.sqrt(e0x * e0x + e0y * e0y);
			float l1 = (float) Math.sqrt(e1x * e1x + e1y * e1y);
			float n0x = l0 == 0 ? 0 : e0y / l0, n0y = l0 == 0 ? 0 : -e0x / l0;
			float n1x = l1 == 0 ? 0 : e1y / l1, n1y = l1 == 0 ? 0 : -e1x / l1;
			float mx = n0x + n1x, my = n0y + n1y;
			float ml = (float) Math.sqrt(mx * mx + my * my);
			if (ml < 1e-4f) {
				mx = n0x;
				my = n0y;
			} else {
				mx /= ml;
				my /= ml;
			}
			float dot = Math.max(0.3f, mx * n0x + my * n0y);
			float ox = mx * aa / dot, oy = my * aa / dot;
			inner[i * 2] = x - ox;
			inner[i * 2 + 1] = y - oy;
			outer[i * 2] = x + ox;
			outer[i * 2 + 1] = y + oy;
		}
		int clear = ColorUtil.withAlpha(color, 0);
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			b.triangle(cx, cy, color, inner[i * 2], inner[i * 2 + 1], color, inner[j * 2], inner[j * 2 + 1], color);
			b.quad(inner[i * 2], inner[i * 2 + 1], color, outer[i * 2], outer[i * 2 + 1], clear,
				outer[j * 2], outer[j * 2 + 1], clear, inner[j * 2], inner[j * 2 + 1], color);
		}
	}

	// ---------------------------------------------------------------- textures

	/** A tinted textured rectangle (e.g. a picture), with float coordinates and any sampler. */
	public static void texture(GuiGraphicsExtractor g, TextureSetup setup, float x0, float y0, float x1, float y1,
							   float u0, float v0, float u1, float v1, int color) {
		if (globalAlpha < 1f) {
			color = (Math.round((color >>> 24) * globalAlpha) << 24) | (color & 0xFFFFFF);
		}
		if ((color >>> 24) == 0 || x1 <= x0 || y1 <= y0) {
			return;
		}
		Matrix3x2f pose = new Matrix3x2f(g.pose());
		int bx = (int) Math.floor(x0);
		int by = (int) Math.floor(y0);
		ScreenRectangle scissor = g.scissorStack.peek();
		ScreenRectangle bounds = new ScreenRectangle(bx, by, Math.max(1, (int) Math.ceil(x1) - bx),
			Math.max(1, (int) Math.ceil(y1) - by)).transformMaxBounds(pose);
		if (scissor != null) {
			bounds = scissor.intersection(bounds);
			if (bounds == null) {
				return;
			}
		}
		float[] data = {x0, y0, u0, v0, x0, y1, u0, v1, x1, y1, u1, v1, x1, y0, u1, v0};
		g.guiRenderState.addGuiElement(new TexturedQuadsState(pose, setup, data,
			new int[]{color, color, color, color}, 4, scissor, bounds));
	}

	public static void triangle(GuiGraphicsExtractor g, float ax, float ay, float bx, float by, float cx, float cy, int color) {
		ShapeBuilder b = BUILDER.reset();
		b.triangle(ax, ay, color, bx, by, color, cx, cy, color);
		b.submit(g);
	}

	// ---------------------------------------------------------------- icons drawn with lines

	public static void checkmark(GuiGraphicsExtractor g, float cx, float cy, float size, float width, int color) {
		float s = size / 2f;
		polyline(g, width, color, cx - s, cy, cx - s * 0.25f, cy + s * 0.7f, cx + s, cy - s * 0.65f);
	}

	public static void cross(GuiGraphicsExtractor g, float cx, float cy, float size, float width, int color) {
		float s = size / 2f;
		line(g, cx - s, cy - s, cx + s, cy + s, width, color);
		line(g, cx - s, cy + s, cx + s, cy - s, width, color);
	}

	/** A chevron pointing in a direction: 0 right, 1 down, 2 left, 3 up. */
	public static void chevron(GuiGraphicsExtractor g, float cx, float cy, float size, float width, int direction, int color) {
		float s = size / 2f;
		float h = s * 0.55f;
		switch (direction & 3) {
			case 0 -> polyline(g, width, color, cx - h, cy - s, cx + h, cy, cx - h, cy + s);
			case 1 -> polyline(g, width, color, cx - s, cy - h, cx, cy + h, cx + s, cy - h);
			case 2 -> polyline(g, width, color, cx + h, cy - s, cx - h, cy, cx + h, cy + s);
			default -> polyline(g, width, color, cx - s, cy + h, cx, cy - h, cx + s, cy + h);
		}
	}

	public static void plus(GuiGraphicsExtractor g, float cx, float cy, float size, float width, int color) {
		float s = size / 2f;
		line(g, cx - s, cy, cx + s, cy, width, color);
		line(g, cx, cy - s, cx, cy + s, width, color);
	}

	/** A dashed rectangle outline, used for HUD element frames in the editor. */
	public static void dashedRect(GuiGraphicsExtractor g, float x, float y, float w, float h, float dash, float thickness, int color) {
		ShapeBuilder b = BUILDER.reset();
		float period = dash * 2;
		for (float p = 0; p < w; p += period) {
			float len = Math.min(dash, w - p);
			addRect(b, x + p, y, len, thickness, color);
			addRect(b, x + p, y + h - thickness, len, thickness, color);
		}
		for (float p = 0; p < h; p += period) {
			float len = Math.min(dash, h - p);
			addRect(b, x, y + p, thickness, len, color);
			addRect(b, x + w - thickness, y + p, thickness, len, color);
		}
		b.submit(g);
	}

	/** Transparency checkerboard (behind colour swatches), submitted as one element. */
	public static void checkerboard(GuiGraphicsExtractor g, float x, float y, float w, float h, float cell, int light, int dark) {
		ShapeBuilder b = BUILDER.reset();
		addRect(b, x, y, w, h, light);
		int cols = (int) Math.ceil(w / cell);
		int rows = (int) Math.ceil(h / cell);
		for (int r = 0; r < rows; r++) {
			for (int c = (r & 1); c < cols; c += 2) {
				float cx = x + c * cell;
				float cy = y + r * cell;
				addRect(b, cx, cy, Math.min(cell, x + w - cx), Math.min(cell, y + h - cy), dark);
			}
		}
		b.submit(g);
	}

	/** The six-stop hue spectrum used by colour pickers, as one element. */
	public static void hueBar(GuiGraphicsExtractor g, float x, float y, float w, float h) {
		ShapeBuilder b = BUILDER.reset();
		int[] stops = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
		float seg = w / 6f;
		for (int i = 0; i < 6; i++) {
			float sx = x + seg * i;
			b.quad(sx, y, stops[i], sx, y + h, stops[i], sx + seg, y + h, stops[i + 1], sx + seg, y, stops[i + 1]);
		}
		b.submit(g);
	}

	private static void addRect(ShapeBuilder b, float x, float y, float w, float h, int color) {
		b.quad(x, y, color, x, y + h, color, x + w, y + h, color, x + w, y, color);
	}

	// ---------------------------------------------------------------- geometry helpers

	private static int segments(float radius, float pixel) {
		float px = radius / pixel;
		if (px <= 0.5f) {
			return 1;
		}
		return Math.max(2, Math.min(18, Math.round((float) Math.sqrt(px) * 1.7f)));
	}

	/**
	 * Writes the outline of a rounded rectangle grown by {@code offset} into buffer 0 or 1, clockwise
	 * on screen starting at the top-left corner. Every corner always contributes seg + 1 points so
	 * outlines at different offsets line up point for point.
	 */
	private static int outline(int buffer, float x, float y, float w, float h,
							   float rTL, float rTR, float rBR, float rBL, float offset, int seg) {
		int needed = 4 * (seg + 1) * 2;
		if (pointsA.length < needed) {
			pointsA = new float[needed * 2];
			pointsB = new float[needed * 2];
		}
		float[] out = buffer == 0 ? pointsA : pointsB;
		float x0 = x - offset;
		float y0 = y - offset;
		float x1 = x + w + offset;
		float y1 = y + h + offset;
		int idx = 0;
		idx = corner(out, idx, x0, y0, Math.max(0, rTL + offset), 1, 1, 180, seg);
		idx = corner(out, idx, x1, y0, Math.max(0, rTR + offset), -1, 1, 270, seg);
		idx = corner(out, idx, x1, y1, Math.max(0, rBR + offset), -1, -1, 0, seg);
		idx = corner(out, idx, x0, y1, Math.max(0, rBL + offset), 1, -1, 90, seg);
		return idx / 2;
	}

	/** (px, py) is the sharp corner; sx, sy point from it towards the inside of the rectangle. */
	private static int corner(float[] out, int idx, float px, float py, float r, int sx, int sy, float startDeg, int seg) {
		float cx = px + sx * r;
		float cy = py + sy * r;
		for (int i = 0; i <= seg; i++) {
			double a = Math.toRadians(startDeg + 90.0 * i / seg);
			out[idx++] = cx + (float) Math.cos(a) * r;
			out[idx++] = cy + (float) Math.sin(a) * r;
		}
		return idx;
	}
}
