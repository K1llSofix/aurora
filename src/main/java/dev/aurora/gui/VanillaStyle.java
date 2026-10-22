package dev.aurora.gui;

import dev.aurora.Aurora;
import dev.aurora.gui.render.Render2D;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Re-skins Minecraft's own screens (world list, server list, options, pause menu...) without
 * replacing them: the widget sprites and menu textures vanilla draws are intercepted and drawn as
 * Aurora's vector shapes instead. Every vanilla feature keeps working because only pixels change.
 */
public final class VanillaStyle {
	private static final float R = 6f;

	private static final Identifier MENU_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/menu_background.png");
	private static final Identifier INWORLD_MENU_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/inworld_menu_background.png");
	private static final Identifier MENU_LIST_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/menu_list_background.png");
	private static final Identifier INWORLD_MENU_LIST_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");
	private static final Identifier HEADER_SEPARATOR = Identifier.withDefaultNamespace("textures/gui/header_separator.png");
	private static final Identifier FOOTER_SEPARATOR = Identifier.withDefaultNamespace("textures/gui/footer_separator.png");
	private static final Identifier INWORLD_HEADER_SEPARATOR = Identifier.withDefaultNamespace("textures/gui/inworld_header_separator.png");
	private static final Identifier INWORLD_FOOTER_SEPARATOR = Identifier.withDefaultNamespace("textures/gui/inworld_footer_separator.png");
	private static final Identifier TAB_HEADER_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/tab_header_background.png");

	private VanillaStyle() {
	}

	public static boolean enabled() {
		return Aurora.client() != null && Aurora.client().vanillaStyle.on();
	}

	private static int a(int color, float alpha) {
		return alpha >= 1f ? color : ColorUtil.fade(color, alpha);
	}

	// ======================================================================= sprites

	/** Draws a vanilla widget sprite in Aurora style; returns false to let vanilla draw it. */
	public static boolean sprite(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, int tint) {
		if (!enabled() || !"minecraft".equals(id.getNamespace())) {
			return false;
		}
		float alpha = ColorUtil.alpha(tint) / 255f;
		switch (id.getPath()) {
			case "widget/button" -> button(g, x, y, w, h, alpha, false, true);
			case "widget/button_highlighted" -> button(g, x, y, w, h, alpha, true, true);
			case "widget/button_disabled" -> button(g, x, y, w, h, alpha, false, false);
			case "widget/slider" -> track(g, x, y, w, h, alpha, false);
			case "widget/slider_highlighted" -> track(g, x, y, w, h, alpha, true);
			case "widget/slider_handle" -> handle(g, x, y, w, h, alpha, false);
			case "widget/slider_handle_highlighted" -> handle(g, x, y, w, h, alpha, true);
			case "widget/text_field" -> field(g, x, y, w, h, alpha, false);
			case "widget/text_field_highlighted" -> field(g, x, y, w, h, alpha, true);
			case "widget/checkbox" -> checkbox(g, x, y, w, h, alpha, false, false);
			case "widget/checkbox_highlighted" -> checkbox(g, x, y, w, h, alpha, false, true);
			case "widget/checkbox_selected" -> checkbox(g, x, y, w, h, alpha, true, false);
			case "widget/checkbox_selected_highlighted" -> checkbox(g, x, y, w, h, alpha, true, true);
			case "widget/tab" -> tab(g, x, y, w, h, alpha, false, false);
			case "widget/tab_highlighted" -> tab(g, x, y, w, h, alpha, false, true);
			case "widget/tab_selected" -> tab(g, x, y, w, h, alpha, true, false);
			case "widget/tab_selected_highlighted" -> tab(g, x, y, w, h, alpha, true, true);
			case "widget/scroller_background" -> scrollTrack(g, x, y, w, h, alpha);
			case "widget/scroller" -> scrollThumb(g, x, y, w, h, alpha);
			case "tooltip/background" -> tooltipBackground(g, x, y, w, h, alpha);
			case "tooltip/frame" -> tooltipFrame(g, x, y, w, h, alpha);
			case "toast/advancement", "toast/recipe", "toast/system", "toast/tutorial", "toast/now_playing",
				 "friends/toast_background" -> toast(g, x, y, w, h, alpha);
			default -> {
				return false;
			}
		}
		return true;
	}

	private static void button(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha, boolean hot, boolean active) {
		if (!active) {
			Render2D.roundRect(g, x, y, w, h, R, a(0x99101218, alpha));
			Render2D.roundOutline(g, x, y, w, h, R, 0.7f, a(0x12FFFFFF, alpha));
			return;
		}
		Render2D.roundRect(g, x, y, w, h, R, Render2D.Paint.vertical(y, h, a(0xD91B1F28, alpha), a(0xD912151C, alpha)));
		if (hot) {
			Render2D.glow(g, x, y, w, h, R, 7, a(ColorUtil.fade(Theme.accent(0.5f), 0.30f), alpha));
			Render2D.roundRect(g, x, y, w, h, R, Render2D.Paint.horizontal(x, w,
				a(ColorUtil.fade(Theme.accent(), 0.62f), alpha), a(ColorUtil.fade(Theme.accent2(), 0.62f), alpha)));
			Render2D.roundOutline(g, x, y, w, h, R, 0.9f, Render2D.Paint.horizontal(x, w,
				a(Theme.accent(), alpha), a(Theme.accent2(), alpha)));
		} else {
			Render2D.roundOutline(g, x, y, w, h, R, 0.7f, a(0x2AFFFFFF, alpha));
		}
	}

	private static void track(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha, boolean hot) {
		Render2D.roundRect(g, x, y, w, h, R, Render2D.Paint.vertical(y, h, a(0xD9161920, alpha), a(0xD90F1117, alpha)));
		Render2D.roundOutline(g, x, y, w, h, R, 0.8f, a(hot ? ColorUtil.fade(Theme.accent(), 0.8f) : 0x26FFFFFF, alpha));
	}

	private static void handle(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha, boolean hot) {
		float hw = Math.min(w, 6);
		float hx = x + (w - hw) / 2f;
		float hy = y + 3;
		float hh = h - 6;
		if (hot) {
			Render2D.glow(g, hx, hy, hw, hh, hw / 2f, 6, a(ColorUtil.fade(Theme.accent(), 0.45f), alpha));
		}
		Render2D.roundRect(g, hx, hy, hw, hh, hw / 2f, Render2D.Paint.vertical(hy, hh, a(Theme.accent(), alpha), a(Theme.accent2(), alpha)));
		Render2D.roundOutline(g, hx, hy, hw, hh, hw / 2f, 0.6f, a(0x66FFFFFF, alpha));
	}

	private static void field(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha, boolean focused) {
		if (focused) {
			Render2D.glow(g, x, y, w, h, 5, 6, a(ColorUtil.fade(Theme.accent(), 0.25f), alpha));
		}
		Render2D.roundRect(g, x, y, w, h, 5, a(0xF20B0D12, alpha));
		Render2D.roundOutline(g, x, y, w, h, 5, 0.8f, a(focused ? Theme.accent() : 0x2EFFFFFF, alpha));
	}

	private static void checkbox(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha, boolean on, boolean hot) {
		float s = Math.min(w, h);
		float bx = x + (w - s) / 2f;
		float by = y + (h - s) / 2f;
		if (on) {
			Render2D.roundRect(g, bx, by, s, s, 4, Render2D.Paint.diagonal(bx, by, s, s, a(Theme.accent(), alpha), a(Theme.accent2(), alpha)));
			Render2D.checkmark(g, bx + s / 2f, by + s / 2f, s * 0.5f, Math.max(1.2f, s * 0.11f), a(Theme.onAccent(), alpha));
		} else {
			Render2D.roundRect(g, bx, by, s, s, 4, a(0xE60F1117, alpha));
		}
		Render2D.roundOutline(g, bx, by, s, s, 4, 0.8f, a(hot ? Theme.accent() : (on ? 0x40FFFFFF : 0x33FFFFFF), alpha));
	}

	private static void tab(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha, boolean selected, boolean hot) {
		float px = x + 2;
		float pw = w - 4;
		float py = y + 3;
		float ph = h - 5;
		if (selected) {
			Render2D.roundRect(g, px, py, pw, ph, R, Render2D.Paint.horizontal(px, pw,
				a(ColorUtil.fade(Theme.accent(), 0.30f), alpha), a(ColorUtil.fade(Theme.accent2(), 0.30f), alpha)));
			Render2D.roundRect(g, px + pw * 0.2f, y + h - 3, pw * 0.6f, 2, 1, Render2D.Paint.horizontal(px, pw,
				a(Theme.accent(), alpha), a(Theme.accent2(), alpha)));
		} else if (hot) {
			Render2D.roundRect(g, px, py, pw, ph, R, a(0x16FFFFFF, alpha));
		}
	}

	private static void scrollTrack(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha) {
		float tw = 3;
		Render2D.roundRect(g, x + (w - tw) / 2f, y + 2, tw, h - 4, tw / 2f, a(0x14FFFFFF, alpha));
	}

	private static void scrollThumb(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha) {
		float tw = 3;
		Render2D.roundRect(g, x + (w - tw) / 2f, y + 2, tw, h - 4, tw / 2f,
			Render2D.Paint.vertical(y, h, a(ColorUtil.fade(Theme.accent(), 0.85f), alpha), a(ColorUtil.fade(Theme.accent2(), 0.85f), alpha)));
	}

	/** Vanilla passes the 9-slice rectangle including a 9 px transparent margin. */
	private static void tooltipBackground(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha) {
		Render2D.shadow(g, x + 9, y + 9, w - 18, h - 18, 6, 10, a(0x80000000, alpha));
		Render2D.roundRect(g, x + 9, y + 9, w - 18, h - 18, 6, Render2D.Paint.vertical(y + 9, h - 18, a(0xF5171A22, alpha), a(0xF50F1117, alpha)));
	}

	private static void tooltipFrame(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha) {
		Render2D.roundOutline(g, x + 9, y + 9, w - 18, h - 18, 6, 0.8f, Render2D.Paint.diagonal(x, y, w, h,
			a(ColorUtil.fade(Theme.accent(), 0.7f), alpha), a(ColorUtil.fade(Theme.accent2(), 0.7f), alpha)));
	}

	/** Toasts (advancements, recipes, system messages, music): a glass card with an accent strip. */
	private static void toast(GuiGraphicsExtractor g, float x, float y, float w, float h, float alpha) {
		float r = Math.min(9, h / 2f);
		Render2D.shadow(g, x, y, w, h, r, 10, a(0x73000000, alpha));
		Render2D.roundRect(g, x, y, w, h, r, Render2D.Paint.vertical(y, h, a(0xF5191C25, alpha), a(0xF50F1117, alpha)));
		Render2D.roundRect(g, x + 3, y + 6, 2.5f, h - 12, 1.25f, Render2D.Paint.vertical(y + 6, h - 12,
			a(Theme.accent(), alpha), a(Theme.accent2(), alpha)));
		Render2D.roundOutline(g, x, y, w, h, r, 0.8f, Render2D.Paint.horizontal(x, w,
			a(ColorUtil.fade(Theme.accent(), 0.55f), alpha), a(0x1FFFFFFF, alpha)));
	}

	// ======================================================================= textures

	/** Menu backgrounds and separators; returns false to let vanilla draw the texture. */
	public static boolean texture(GuiGraphicsExtractor g, Identifier id, int x0, int y0, int x1, int y1) {
		if (!enabled()) {
			return false;
		}
		float w = x1 - x0;
		float h = y1 - y0;
		if (id.equals(MENU_BACKGROUND) || id.equals(INWORLD_MENU_BACKGROUND)) {
			boolean inWorld = id.equals(INWORLD_MENU_BACKGROUND);
			Render2D.verticalGradient(g, x0, y0, w, h, inWorld ? 0x8C06080E : 0xA606080E, inWorld ? 0xB306080E : 0xD906080E);
			if (w > g.guiWidth() * 0.8f && h > g.guiHeight() * 0.8f) {
				ambient(g, x0, y0, w, h);
			}
			return true;
		}
		if (id.equals(MENU_LIST_BACKGROUND) || id.equals(INWORLD_MENU_LIST_BACKGROUND)) {
			Render2D.rect(g, x0, y0, w, h, 0x1A000000);
			return true;
		}
		if (id.equals(HEADER_SEPARATOR) || id.equals(FOOTER_SEPARATOR)
			|| id.equals(INWORLD_HEADER_SEPARATOR) || id.equals(INWORLD_FOOTER_SEPARATOR)) {
			boolean header = id.equals(HEADER_SEPARATOR) || id.equals(INWORLD_HEADER_SEPARATOR);
			float ly = header ? y1 - 1 : y0;
			float mid = x0 + w / 2f;
			int edge = 0x00FFFFFF;
			int center = ColorUtil.fade(Theme.accent(0.5f), 0.45f);
			Render2D.horizontalGradient(g, x0, ly, w / 2f, 1, edge, center);
			Render2D.horizontalGradient(g, mid, ly, w / 2f, 1, center, edge);
			return true;
		}
		if (id.equals(TAB_HEADER_BACKGROUND)) {
			Render2D.rect(g, x0, y0, w, h, 0x33000000);
			return true;
		}
		return false;
	}

	/** Two slow accent glows, the same mood as the Aurora menu. */
	private static void ambient(GuiGraphicsExtractor g, float x, float y, float w, float h) {
		float t = (float) (System.nanoTime() / 1e9);
		float gx = x + w * (0.22f + 0.06f * (float) Math.sin(t * 0.35));
		float gy = y + h * 0.18f;
		Render2D.blob(g, gx - 70, gy - 30, 140, 60, 30, 120, ColorUtil.fade(Theme.accent(), 0.07f));
		float gx2 = x + w * (0.78f + 0.05f * (float) Math.cos(t * 0.3));
		float gy2 = y + h * 0.82f;
		Render2D.blob(g, gx2 - 70, gy2 - 30, 140, 60, 30, 120, ColorUtil.fade(Theme.accent2(), 0.07f));
	}

	// ======================================================================= lists

	/** Rounded accent highlight for the selected entry of any list. */
	public static void selection(GuiGraphicsExtractor g, float x, float y, float w, float h, boolean focused) {
		Render2D.roundRect(g, x, y, w, h, R, Render2D.Paint.horizontal(x, w,
			ColorUtil.fade(Theme.accent(), 0.20f), ColorUtil.fade(Theme.accent2(), 0.12f)));
		Render2D.roundOutline(g, x, y, w, h, R, focused ? 1.2f : 0.8f, Render2D.Paint.horizontal(x, w,
			ColorUtil.fade(Theme.accent(), focused ? 1f : 0.6f), ColorUtil.fade(Theme.accent2(), focused ? 1f : 0.6f)));
	}

	/** Card behind each world / server / language row. */
	public static void card(GuiGraphicsExtractor g, float x, float y, float w, float h, boolean hovered) {
		Render2D.roundRect(g, x, y, w, h, R, Render2D.Paint.vertical(y, h,
			hovered ? 0x8C1E222C : 0x66151820, hovered ? 0x8C171A22 : 0x66101218));
		Render2D.roundOutline(g, x, y, w, h, R, 0.7f, hovered ? 0x30FFFFFF : 0x14FFFFFF);
	}
}
