package dev.aurora.gui.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2f;

import java.util.Arrays;

/**
 * Collects triangles for one shape and submits them as a single GUI element, so a rounded panel
 * with its anti-aliased fringe is one entry in the render state instead of dozens.
 *
 * <p>The GUI pipeline draws quads with back-face culling, so every triangle is emitted as a
 * degenerate quad (a, b, c, c) wound the same way vanilla winds its rectangles.
 */
final class ShapeBuilder {
	private float[] xy = new float[512];
	private int[] colors = new int[256];
	private int count;
	private float minX, minY, maxX, maxY;

	ShapeBuilder reset() {
		count = 0;
		minX = minY = Float.MAX_VALUE;
		maxX = maxY = -Float.MAX_VALUE;
		return this;
	}

	boolean isEmpty() {
		return count == 0;
	}

	private void vertex(float x, float y, int color) {
		if (Render2D.globalAlpha < 1f) {
			color = (Math.round((color >>> 24) * Render2D.globalAlpha) << 24) | (color & 0xFFFFFF);
		}
		if (count * 2 + 2 > xy.length) {
			xy = Arrays.copyOf(xy, xy.length * 2);
			colors = Arrays.copyOf(colors, colors.length * 2);
		}
		xy[count * 2] = x;
		xy[count * 2 + 1] = y;
		colors[count] = color;
		count++;
		if (x < minX) minX = x;
		if (y < minY) minY = y;
		if (x > maxX) maxX = x;
		if (y > maxY) maxY = y;
	}

	void triangle(float ax, float ay, int ca, float bx, float by, int cb, float cx, float cy, int cc) {
		if ((ca | cb | cc) >>> 24 == 0) {
			return; // fully transparent
		}
		float cross = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
		if (cross == 0) {
			return;
		}
		vertex(ax, ay, ca);
		if (cross < 0) {
			vertex(bx, by, cb);
			vertex(cx, cy, cc);
			vertex(cx, cy, cc);
		} else {
			vertex(cx, cy, cc);
			vertex(bx, by, cb);
			vertex(bx, by, cb);
		}
	}

	/** A convex quad given in either winding order. */
	void quad(float ax, float ay, int ca, float bx, float by, int cb, float cx, float cy, int cc, float dx, float dy, int cd) {
		triangle(ax, ay, ca, bx, by, cb, cx, cy, cc);
		triangle(ax, ay, ca, cx, cy, cc, dx, dy, cd);
	}

	/**
	 * A convex quad emitted as one primitive (4 vertices instead of two padded triangles), for
	 * shapes that are known to be convex such as gradient columns. Either winding order.
	 */
	void convexQuad(float ax, float ay, int ca, float bx, float by, int cb, float cx, float cy, int cc, float dx, float dy, int cd) {
		if ((ca | cb | cc | cd) >>> 24 == 0) {
			return;
		}
		float cross = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
		if (cross == 0) {
			triangle(ax, ay, ca, cx, cy, cc, dx, dy, cd);
			return;
		}
		vertex(ax, ay, ca);
		if (cross < 0) {
			vertex(bx, by, cb);
			vertex(cx, cy, cc);
			vertex(dx, dy, cd);
		} else {
			vertex(dx, dy, cd);
			vertex(cx, cy, cc);
			vertex(bx, by, cb);
		}
	}

	void submit(GuiGraphicsExtractor graphics) {
		if (count == 0) {
			return;
		}
		Matrix3x2f pose = new Matrix3x2f(graphics.pose());
		int x0 = (int) Math.floor(minX);
		int y0 = (int) Math.floor(minY);
		int x1 = (int) Math.ceil(maxX);
		int y1 = (int) Math.ceil(maxY);
		ScreenRectangle scissor = graphics.scissorStack.peek();
		ScreenRectangle bounds = new ScreenRectangle(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0)).transformMaxBounds(pose);
		if (scissor != null) {
			bounds = scissor.intersection(bounds);
			if (bounds == null) {
				return;
			}
		}
		graphics.guiRenderState.addGuiElement(new ShapeState(
			pose, Arrays.copyOf(xy, count * 2), Arrays.copyOf(colors, count), count, scissor, bounds));
	}
}
