package dev.aurora.gui.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Glyphs of one font at one pixel size, rasterised on demand with Java2D into GPU pages. Glyphs
 * are drawn 1:1 with physical pixels, so text is as sharp as the OS renders it, at any GUI scale.
 */
final class GlyphAtlas {
	private static final int PADDING = 1;
	private static final FontRenderContext FRC = new FontRenderContext(null, true, false);

	record Glyph(int page, float u0, float v0, float u1, float v1, int width, int height,
				 int offsetX, int offsetY, int advance) {
		boolean visible() {
			return width > 0;
		}
	}

	private final String label;
	private final Font font;
	private final Font fallback;
	private final int pageSize;
	final int pixelSize;
	final int ascent;
	final int descent;
	private final Int2ObjectOpenHashMap<Glyph> glyphs = new Int2ObjectOpenHashMap<>();
	private final List<Page> pages = new ArrayList<>();

	private static final class Page {
		final NativeImage image;
		final DynamicTexture texture;
		final TextureSetup setup;
		int penX = 0;
		int penY = 0;
		int rowHeight = 0;
		boolean dirty;

		Page(String label, int size, GpuSampler sampler) {
			image = new NativeImage(size, size, true);
			texture = new DynamicTexture(() -> label, image);
			setup = TextureSetup.singleTexture(texture.getTextureView(), sampler);
		}
	}

	GlyphAtlas(String label, Font baseFont, Font fallbackFont, int pixelSize) {
		this.label = label + "@" + pixelSize;
		this.pixelSize = pixelSize;
		this.font = baseFont.deriveFont((float) pixelSize);
		this.fallback = fallbackFont.deriveFont((float) pixelSize);
		this.pageSize = pixelSize <= 22 ? 512 : 1024;
		var metrics = font.getLineMetrics("Hgjy", FRC);
		this.ascent = Math.round(metrics.getAscent());
		this.descent = Math.round(metrics.getDescent());
	}

	TextureSetup page(int index) {
		return pages.get(index).setup;
	}

	Glyph glyph(int codepoint) {
		Glyph glyph = glyphs.get(codepoint);
		if (glyph == null) {
			glyph = rasterize(codepoint);
			glyphs.put(codepoint, glyph);
		}
		return glyph;
	}

	/** Pushes pages that received new glyphs to the GPU. Called once per text draw. */
	void flush() {
		for (Page page : pages) {
			if (page.dirty) {
				page.texture.upload();
				page.dirty = false;
			}
		}
	}

	private Glyph rasterize(int codepoint) {
		Font f = font.canDisplay(codepoint) ? font : fallback;
		String s = new String(Character.toChars(codepoint));
		GlyphVector vector = f.createGlyphVector(FRC, s);
		int advance = Math.round(vector.getGlyphMetrics(0).getAdvanceX());
		Rectangle bounds = vector.getPixelBounds(FRC, 0, 0);
		if (bounds.width <= 0 || bounds.height <= 0) {
			return new Glyph(0, 0, 0, 0, 0, 0, 0, 0, 0, advance);
		}

		int w = bounds.width + PADDING * 2;
		int h = bounds.height + PADDING * 2;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setColor(Color.WHITE);
		g.drawGlyphVector(vector, PADDING - bounds.x, PADDING - bounds.y);
		g.dispose();

		Page page = pageFor(w, h);
		int pageIndex = pages.indexOf(page);
		int px = page.penX;
		int py = page.penY;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int alpha = img.getRGB(x, y) >>> 24;
				if (alpha != 0) {
					// A gentle gamma lift: light-on-dark text otherwise looks thinner than designed.
					alpha = Math.min(255, Math.round((float) Math.pow(alpha / 255f, 0.82f) * 255f));
					page.image.setPixel(px + x, py + y, (alpha << 24) | 0xFFFFFF);
				}
			}
		}
		page.penX += w + 1;
		page.rowHeight = Math.max(page.rowHeight, h);
		page.dirty = true;

		float inv = 1f / pageSize;
		return new Glyph(pageIndex, px * inv, py * inv, (px + w) * inv, (py + h) * inv, w, h,
			bounds.x - PADDING, bounds.y - PADDING, advance);
	}

	private Page pageFor(int w, int h) {
		Page page = pages.isEmpty() ? null : pages.get(pages.size() - 1);
		if (page != null && page.penX + w > pageSize) {
			page.penX = 0;
			page.penY += page.rowHeight + 1;
			page.rowHeight = 0;
		}
		if (page == null || page.penY + h > pageSize) {
			GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			page = new Page("aurora_font_" + label + "_" + pages.size(), pageSize, sampler);
			pages.add(page);
		}
		return page;
	}

	void close() {
		for (Page page : pages) {
			page.texture.close();
		}
		pages.clear();
		glyphs.clear();
	}
}
