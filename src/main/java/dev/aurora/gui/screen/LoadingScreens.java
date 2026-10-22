package dev.aurora.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.Aurora;
import dev.aurora.gui.Theme;
import dev.aurora.gui.background.MenuBackground;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.gui.widget.Ui;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.KeyNames;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;

/**
 * The look of Aurora's loading screens: the game's startup splash, world loading, connecting to a
 * server and short "please wait" messages. They share the menu background, a glass card with a
 * spinning ring around the Aurora mark, a smooth progress bar and a rotating tip.
 */
public final class LoadingScreens {
	private static final int TIPS = 10;
	private static final long LAUNCH_SEED = System.currentTimeMillis();

	private LoadingScreens() {
	}

	public static boolean enabled() {
		return Aurora.client() != null && Aurora.client().loadingScreens.on();
	}

	// ======================================================================= pieces

	/** The menu background (scene, picture or a dark gradient) with a soft veil for contrast. */
	public static void background(GuiGraphicsExtractor g, int width, int height, float alpha) {
		float saved = Render2D.globalAlpha;
		Render2D.globalAlpha = alpha;
		if (!MenuBackground.render(g, width, height)) {
			Render2D.verticalGradient(g, 0, 0, width, height, 0xFF0B0E1A, 0xFF05060B);
		}
		Render2D.verticalGradient(g, 0, height * 0.35f, width, height * 0.65f, 0x0005070D, 0xA605070D);
		Render2D.globalAlpha = saved;
	}

	/** The Aurora mark with a ring spinning around it. */
	public static void spinner(GuiGraphicsExtractor g, float cx, float cy, float size) {
		float t = (float) (System.nanoTime() / 1e9);
		float ring = size * 0.9f;
		Render2D.ring(g, cx, cy, ring, 1.6f, Ui.c(0x1FFFFFFF));
		float start = (t * 300f) % 360f;
		float sweep = 70f + 50f * (float) Math.sin(t * 2.3f);
		Render2D.arc(g, cx, cy, ring, 1.8f, start, sweep, Ui.c(Theme.accent()));
		Render2D.arc(g, cx, cy, ring, 1.8f, start + 180f, sweep * 0.6f, Ui.c(Theme.accent2()));
		Ui.logo(g, cx, cy, size);
	}

	/**
	 * A rounded progress bar. With a negative progress it shows a highlight sweeping across
	 * instead, for waits of unknown length.
	 */
	public static void bar(GuiGraphicsExtractor g, float x, float y, float w, float h, float progress) {
		Render2D.roundRect(g, x, y, w, h, h / 2f, Ui.c(0x24FFFFFF));
		if (progress >= 0) {
			float fill = Math.max(h, w * Math.min(1f, progress));
			Render2D.glow(g, x, y, fill, h, h / 2f, 6, Ui.c(ColorUtil.fade(Theme.accent(0.5f), 0.35f)));
			Render2D.roundRect(g, x, y, fill, h, h / 2f, Ui.accentPaint(x, w));
		} else {
			float t = (float) ((System.nanoTime() / 1e9) % 1.4) / 1.4f;
			float seg = w * 0.32f;
			float sx = x - seg + (w + seg) * t;
			float x0 = Math.max(x, sx);
			float x1 = Math.min(x + w, sx + seg);
			if (x1 - x0 > 1) {
				Render2D.roundRect(g, x0, y, x1 - x0, h, h / 2f, Ui.accentPaint(x0, x1 - x0));
			}
		}
	}

	/** A glass card centred on (cx, cy) with the spinner, a title, a detail line and a bar. */
	public static void card(GuiGraphicsExtractor g, float cx, float cy, String title, String detail, float progress) {
		float w = Math.max(200, Math.min(320, FontRenderer.SEMIBOLD.width(title, 10) + 60));
		float h = 112;
		float x = cx - w / 2f;
		float y = cy - h / 2f;
		Render2D.shadow(g, x, y, w, h, 14, 18, Ui.c(0x73000000));
		Render2D.roundRect(g, x, y, w, h, 14, Render2D.Paint.vertical(y, h, Ui.c(0xD9171A23), Ui.c(0xD90E1016)));
		Render2D.roundOutline(g, x, y, w, h, 14, 0.8f, Render2D.Paint.horizontal(x, w,
			Ui.c(ColorUtil.fade(Theme.accent(), 0.5f)), Ui.c(0x1FFFFFFF)));
		spinner(g, cx, y + 30, 22);
		FontRenderer.SEMIBOLD.drawCentered(g, FontRenderer.SEMIBOLD.ellipsize(title, 10, w - 24), cx, y + 62, 10, Ui.c(Theme.TEXT));
		if (!detail.isEmpty()) {
			FontRenderer.MEDIUM.drawCentered(g, FontRenderer.MEDIUM.ellipsize(detail, 7.5f, w - 24), cx, y + 76, 7.5f, Ui.c(Theme.TEXT_DIM));
		}
		bar(g, x + 24, y + h - 20, w - 48, 4, progress);
	}

	/** A tip about Aurora at the bottom of the screen; the same one while a screen is open. */
	public static void tip(GuiGraphicsExtractor g, int width, int height, long seed) {
		String text = tipText((int) Math.floorMod(seed / 7919, TIPS));
		String label = I18n.get("aurora.tip.label");
		float lw = FontRenderer.SEMIBOLD.width(label, 7.5f);
		float tw = FontRenderer.MEDIUM.width(text, 7.5f);
		float x = width / 2f - (lw + 6 + tw) / 2f;
		float y = height - 20;
		FontRenderer.SEMIBOLD.drawMiddle(g, label, x, y, 7.5f, Ui.c(Theme.accent()));
		FontRenderer.MEDIUM.drawMiddle(g, text, x + lw + 6, y, 7.5f, Ui.c(Theme.TEXT_DIM));
	}

	private static String key(KeyMapping mapping) {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
		return key.equals(InputConstants.UNKNOWN) ? "—" : KeyNames.of(key);
	}

	private static String tipText(int index) {
		return switch (index) {
			case 0 -> I18n.get("aurora.tip.0", key(Aurora.menuKey));
			case 1 -> I18n.get("aurora.tip.1", key(Aurora.zoomKey));
			case 2 -> I18n.get("aurora.tip.2", key(Aurora.freelookKey));
			default -> I18n.get("aurora.tip." + index);
		};
	}

	// ======================================================================= startup splash

	/**
	 * The startup splash over the loading progress. {@code alpha} fades the whole overlay,
	 * {@code fadeOut} (0..1) fades just the progress bar like vanilla.
	 */
	public static void splash(GuiGraphicsExtractor g, int width, int height, float alpha, float fadeOut, float progress) {
		background(g, width, height, alpha);
		float saved = Ui.alpha;
		Ui.alpha = alpha;
		float cx = width / 2f;
		float cy = height / 2f - 10;
		float logo = Math.max(28, Math.min(44, height * 0.11f));
		Ui.logo(g, cx, cy - logo * 0.9f, logo);
		float size = Math.max(18, Math.min(30, height * 0.075f));
		float tw = FontRenderer.BOLD.width("AURORA", size);
		Ui.gradientText(g, FontRenderer.BOLD, "AURORA", cx - tw / 2f, cy + size * 0.55f, size, Theme.accent(), Theme.accent2());
		float barAlpha = 1f - Math.max(0f, Math.min(1f, fadeOut));
		if (barAlpha > 0.01f) {
			Ui.alpha = alpha * barAlpha;
			float bw = Math.min(220, width * 0.4f);
			float by = cy + size * 1.4f + 10;
			bar(g, cx - bw / 2f, by, bw, 4, progress);
			FontRenderer.MEDIUM.drawMiddle(g, I18n.get("aurora.loading.resources"), cx - bw / 2f, by + 13, 7, Ui.c(Theme.TEXT_DIM));
			FontRenderer.SEMIBOLD.drawRight(g, Math.round(progress * 100) + "%", cx + bw / 2f, by + 13, 7, Ui.c(Theme.TEXT));
			tip(g, width, height, LAUNCH_SEED);
		}
		Ui.alpha = saved;
	}
}
