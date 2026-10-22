package dev.aurora.gui;

import dev.aurora.Aurora;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.module.ClientSettings;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared look of HUD elements. Three styles: "modern" (rounded glass pill with an accent bar),
 * "classic" (flat dark box, Minecraft feel) and "minimal" (text only).
 */
public final class HudDraw {
	public static final float TEXT_SIZE = 9f;
	public static final float PAD_X = 5f;
	public static final float ROW = 15f;

	private HudDraw() {
	}

	private static ClientSettings cs() {
		return Aurora.client();
	}

	public static boolean modern() {
		return cs().hudStyle.is("modern");
	}

	public static boolean classic() {
		return cs().hudStyle.is("classic");
	}

	public static boolean minimal() {
		return cs().hudStyle.is("minimal");
	}

	private static boolean vanillaFont() {
		return cs().hudFont.is("minecraft");
	}

	public static int textColor() {
		return cs().hudText.color();
	}

	public static int labelColor() {
		return Theme.accent();
	}

	public static float width(String text) {
		if (vanillaFont()) {
			return Minecraft.getInstance().font.width(text);
		}
		return FontRenderer.SEMIBOLD.width(text, TEXT_SIZE);
	}

	/** Height of one text row inside an element. */
	public static float rowHeight() {
		return minimal() ? 10f : (classic() ? 12f : ROW);
	}

	public static float padX() {
		return minimal() ? 0f : (classic() ? 3f : PAD_X);
	}

	/** Draws text vertically centred on {@code centerY}; returns its width. */
	public static float text(GuiGraphicsExtractor g, String text, float x, float centerY, int color) {
		boolean shadow = cs().hudShadow.on();
		if (vanillaFont()) {
			var font = Minecraft.getInstance().font;
			g.text(font, text, Math.round(x), Math.round(centerY - 4), color, shadow);
			return font.width(text);
		}
		return FontRenderer.SEMIBOLD.drawMiddle(g, text, x, centerY, TEXT_SIZE, color, shadow);
	}

	/** Background for an element of the given size, according to the selected style. */
	public static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h) {
		ClientSettings c = cs();
		box(g, x, y, w, h);
		if (modern() && c.hudAccent.on()) {
			float barH = Math.max(5, h - 8);
			Render2D.roundRect(g, x + 1.5f, y + (h - barH) / 2f, 1.5f, barH, 0.75f,
				Render2D.Paint.vertical(y, h, Theme.accent(), Theme.accent2()));
		}
	}

	/** The background alone, without the accent bar: for tiles such as keystroke keys. */
	public static void box(GuiGraphicsExtractor g, float x, float y, float w, float h) {
		if (minimal()) {
			return;
		}
		if (classic()) {
			Render2D.rect(g, x, y, w, h, 0x6F000000);
			return;
		}
		float opacity = cs().hudOpacity.floatValue() / 100f;
		if (opacity > 0) {
			int top = ColorUtil.withAlpha(0x12151C, Math.round(255 * opacity));
			int bottom = ColorUtil.withAlpha(0x0B0D12, Math.round(255 * Math.min(1f, opacity * 1.1f)));
			Render2D.roundRect(g, x, y, w, h, 4f, Render2D.Paint.vertical(y, h, top, bottom));
			Render2D.roundOutline(g, x, y, w, h, 4f, 0.5f, ColorUtil.withAlpha(0xFFFFFF, Math.round(28 * opacity)));
		}
	}

	/** Left inset of text inside a panel, leaving room for the accent bar. */
	public static float contentInset() {
		if (modern() && cs().hudAccent.on()) {
			return PAD_X + 2f;
		}
		return padX();
	}
}
