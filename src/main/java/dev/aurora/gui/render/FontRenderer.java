package dev.aurora.gui.render;

import com.mojang.logging.LogUtils;
import dev.aurora.util.ColorUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2f;
import org.slf4j.Logger;

import java.awt.Font;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Smooth UI text in the Inter typeface. Sizes are in GUI units (an "em", like CSS px); the atlas
 * for the matching physical pixel size is created lazily, so text stays crisp at every GUI scale
 * and when panels are scaled during animations.
 */
public final class FontRenderer {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static final FontRenderer MEDIUM = new FontRenderer("Inter-Medium.ttf");
	public static final FontRenderer SEMIBOLD = new FontRenderer("Inter-SemiBold.ttf");
	public static final FontRenderer BOLD = new FontRenderer("Inter-Bold.ttf");

	private static final float CAP_HEIGHT = 0.727f;
	private static final float ASCENT = 0.969f;
	private static final float DESCENT = 0.242f;

	private final String file;
	private Font base;
	private Font fallback;
	private final Int2ObjectOpenHashMap<GlyphAtlas> atlases = new Int2ObjectOpenHashMap<>();

	private float[] data = new float[1024];
	private int[] colors = new int[256];

	private FontRenderer(String file) {
		this.file = file;
	}

	private GlyphAtlas atlas(int pixelSize) {
		pixelSize = Math.max(4, Math.min(160, pixelSize));
		GlyphAtlas atlas = atlases.get(pixelSize);
		if (atlas == null) {
			if (base == null) {
				load();
			}
			atlas = new GlyphAtlas(file, base, fallback, pixelSize);
			atlases.put(pixelSize, atlas);
		}
		return atlas;
	}

	private void load() {
		fallback = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
		try (InputStream in = FontRenderer.class.getResourceAsStream("/assets/aurora/fonts/" + file)) {
			if (in == null) {
				throw new IllegalStateException("missing font " + file);
			}
			base = Font.createFont(Font.TRUETYPE_FONT, in);
		} catch (Exception e) {
			LOGGER.error("Aurora: could not load {}, using the system sans-serif font", file, e);
			base = fallback;
		}
	}

	/** Drops all atlases, e.g. after the window's GUI scale changed a lot. */
	public void clear() {
		atlases.values().forEach(GlyphAtlas::close);
		atlases.clear();
	}

	public static void clearAll() {
		MEDIUM.clear();
		SEMIBOLD.clear();
		BOLD.clear();
	}

	private static int guiScale() {
		return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}

	// ---------------------------------------------------------------- measuring

	public float width(String text, float size) {
		int scale = guiScale();
		GlyphAtlas atlas = atlas(Math.round(size * scale));
		int px = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			px += atlas.glyph(cp).advance();
		}
		return px / (float) scale;
	}

	public float lineHeight(float size) {
		return (ASCENT + DESCENT) * size;
	}

	public float capHeight(float size) {
		return CAP_HEIGHT * size;
	}

	/** Cuts the text to fit, ending with an ellipsis. */
	public String ellipsize(String text, float size, float maxWidth) {
		if (width(text, size) <= maxWidth) {
			return text;
		}
		String dots = "…";
		float budget = maxWidth - width(dots, size);
		int end = text.length();
		while (end > 0 && width(text.substring(0, end), size) > budget) {
			end--;
		}
		return text.substring(0, end).stripTrailing() + dots;
	}

	/** Greedy word wrap. */
	public java.util.List<String> wrap(String text, float size, float maxWidth) {
		java.util.List<String> lines = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (width(candidate, size) <= maxWidth || line.isEmpty()) {
				line.setLength(0);
				line.append(candidate);
			} else {
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	// ---------------------------------------------------------------- drawing

	/** Draws with the top of the line box at {@code y}. */
	public float draw(GuiGraphicsExtractor g, String text, float x, float y, float size, int color) {
		return drawBaseline(g, text, x, y + ASCENT * size, size, color, false);
	}

	public float drawWithShadow(GuiGraphicsExtractor g, String text, float x, float y, float size, int color) {
		return drawBaseline(g, text, x, y + ASCENT * size, size, color, true);
	}

	/** Draws with capital letters vertically centred on {@code centerY}: the usual need in a UI row. */
	public float drawMiddle(GuiGraphicsExtractor g, String text, float x, float centerY, float size, int color) {
		return drawBaseline(g, text, x, centerY + CAP_HEIGHT * size / 2f, size, color, false);
	}

	public float drawMiddle(GuiGraphicsExtractor g, String text, float x, float centerY, float size, int color, boolean shadow) {
		return drawBaseline(g, text, x, centerY + CAP_HEIGHT * size / 2f, size, color, shadow);
	}

	public void drawCentered(GuiGraphicsExtractor g, String text, float centerX, float centerY, float size, int color) {
		drawMiddle(g, text, centerX - width(text, size) / 2f, centerY, size, color);
	}

	public void drawCentered(GuiGraphicsExtractor g, String text, float centerX, float centerY, float size, int color, boolean shadow) {
		drawMiddle(g, text, centerX - width(text, size) / 2f, centerY, size, color, shadow);
	}

	public void drawRight(GuiGraphicsExtractor g, String text, float rightX, float centerY, float size, int color) {
		drawMiddle(g, text, rightX - width(text, size), centerY, size, color);
	}

	/**
	 * Draws a string on a baseline and returns its advance in GUI units. Each colour code style
	 * run is not supported on purpose: UI strings here are plain.
	 */
	public float drawBaseline(GuiGraphicsExtractor g, String text, float x, float baseline, float size, int color, boolean shadow) {
		if (text.isEmpty() || (color >>> 24) == 0) {
			return 0;
		}
		Matrix3x2f pose = new Matrix3x2f(g.pose());
		int guiScale = guiScale();
		float pxPerUnit = guiScale * Render2D.poseScale(g);
		GlyphAtlas atlas = atlas(Math.round(size * pxPerUnit));
		// The atlas may be a pixel bigger or smaller than requested; scale its glyphs to match.
		float unitsPerPx = size / atlas.pixelSize;

		// Snap the pen origin to the physical pixel grid so glyph texels land exactly on pixels.
		boolean axisAligned = Math.abs(pose.m01()) < 1e-4f && Math.abs(pose.m10()) < 1e-4f;
		if (axisAligned) {
			float sx = (pose.m00() * x + pose.m20()) * guiScale;
			float sy = (pose.m11() * baseline + pose.m21()) * guiScale;
			x += (Math.round(sx) - sx) / (guiScale * pose.m00());
			baseline += (Math.round(sy) - sy) / (guiScale * pose.m11());
		}

		int shadowColor = ColorUtil.argb(Math.round(ColorUtil.alpha(color) * 0.42f), 0, 0, 0);
		float shadowOffset = Math.max(1f, Math.round(pxPerUnit * 0.5f)) * unitsPerPx;

		int vertices = 0;
		int pageInUse = -1;
		float pen = 0;
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			GlyphAtlas.Glyph glyph = atlas.glyph(cp);
			if (glyph.visible()) {
				if (pageInUse != -1 && glyph.page() != pageInUse) {
					// Rare: the string spans two atlas pages. Flush what we have and continue.
					submit(g, pose, atlas.page(pageInUse), vertices, minX, minY, maxX, maxY);
					vertices = 0;
					minX = minY = Float.MAX_VALUE;
					maxX = maxY = -Float.MAX_VALUE;
				}
				pageInUse = glyph.page();
				float gx = x + (pen + glyph.offsetX()) * unitsPerPx;
				float gy = baseline + glyph.offsetY() * unitsPerPx;
				float gw = glyph.width() * unitsPerPx;
				float gh = glyph.height() * unitsPerPx;
				if (shadow) {
					vertices = quad(vertices, gx + shadowOffset, gy + shadowOffset, gw, gh, glyph, shadowColor);
				}
				vertices = quad(vertices, gx, gy, gw, gh, glyph, color);
				minX = Math.min(minX, gx);
				minY = Math.min(minY, gy);
				maxX = Math.max(maxX, gx + gw + (shadow ? shadowOffset : 0));
				maxY = Math.max(maxY, gy + gh + (shadow ? shadowOffset : 0));
			}
			pen += glyph.advance();
		}
		atlas.flush();
		if (vertices > 0) {
			submit(g, pose, atlas.page(pageInUse), vertices, minX, minY, maxX, maxY);
		}
		return pen * unitsPerPx;
	}

	private int quad(int v, float x, float y, float w, float h, GlyphAtlas.Glyph glyph, int color) {
		ensure(v + 4);
		put(v, x, y, glyph.u0(), glyph.v0(), color);
		put(v + 1, x, y + h, glyph.u0(), glyph.v1(), color);
		put(v + 2, x + w, y + h, glyph.u1(), glyph.v1(), color);
		put(v + 3, x + w, y, glyph.u1(), glyph.v0(), color);
		return v + 4;
	}

	private void put(int v, float x, float y, float u, float vv, int color) {
		int o = v * 4;
		data[o] = x;
		data[o + 1] = y;
		data[o + 2] = u;
		data[o + 3] = vv;
		colors[v] = color;
	}

	private void ensure(int vertices) {
		if (vertices > colors.length) {
			colors = Arrays.copyOf(colors, Math.max(vertices, colors.length * 2));
			data = Arrays.copyOf(data, colors.length * 4);
		}
	}

	private void submit(GuiGraphicsExtractor g, Matrix3x2f pose, TextureSetup setup, int vertices,
						float minX, float minY, float maxX, float maxY) {
		ScreenRectangle scissor = g.scissorStack.peek();
		int x0 = (int) Math.floor(minX);
		int y0 = (int) Math.floor(minY);
		ScreenRectangle bounds = new ScreenRectangle(x0, y0,
			Math.max(1, (int) Math.ceil(maxX) - x0), Math.max(1, (int) Math.ceil(maxY) - y0)).transformMaxBounds(pose);
		if (scissor != null) {
			bounds = scissor.intersection(bounds);
			if (bounds == null) {
				return;
			}
		}
		g.guiRenderState.addGuiElement(new TexturedQuadsState(pose, setup,
			Arrays.copyOf(data, vertices * 4), Arrays.copyOf(colors, vertices), vertices, scissor, bounds));
	}
}
