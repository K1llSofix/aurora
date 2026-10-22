package dev.aurora.gui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.KeySetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.setting.Setting;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** One line in a module's settings page: a label on the left and a control on the right. */
public abstract class SettingRow {
	public static final float HEIGHT = 30;
	protected static final float LABEL_SIZE = 8.5f;

	protected final String label;
	protected final Runnable changed;
	protected float x, y, w;
	protected final Animation hover = new Animation(0, 18);
	private final Animation appear = new Animation(1, 14);

	protected SettingRow(String label, Runnable changed) {
		this.label = label;
		this.changed = changed;
	}

	public static SettingRow of(Setting<?> setting, Runnable changed) {
		String label = I18n.get(setting.translationKey());
		if (setting instanceof BooleanSetting b) {
			return new Toggle(label, b::on, v -> b.set(v), changed).visibleWhen(b::isVisible);
		}
		if (setting instanceof NumberSetting n) {
			return new Slider(label, n, changed).visibleWhen(n::isVisible);
		}
		if (setting instanceof ModeSetting m) {
			return new Mode(label, m, changed).visibleWhen(m::isVisible);
		}
		if (setting instanceof ColorSetting c) {
			return new ColorPicker(label, c, changed).visibleWhen(c::isVisible);
		}
		if (setting instanceof KeySetting k) {
			return new Keybind(label, k::get, k::set, changed).visibleWhen(k::isVisible);
		}
		throw new IllegalArgumentException("No row for " + setting.getClass());
	}

	private BooleanSupplier visible = () -> true;

	public SettingRow visibleWhen(BooleanSupplier v) {
		this.visible = v;
		return this;
	}

	public boolean isVisible() {
		return visible.getAsBoolean();
	}

	/** Fades a row in when it becomes visible (e.g. a dependent setting appears). */
	public float appearance() {
		return appear.target(isVisible() ? 1 : 0).get();
	}

	public float height() {
		return HEIGHT;
	}

	public void render(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w) {
		this.x = x;
		this.y = y;
		this.w = w;
		float hv = hover.target(Ui.inside(mx, my, x, y, w, HEIGHT) && !blocked).get();
		if (hv > 0.01f) {
			Render2D.roundRect(g, x + 2, y + 2, w - 4, HEIGHT - 4, 6, Ui.c(ColorUtil.withAlpha(0xFFFFFF, Math.round(7 * hv))));
		}
		FontRenderer.MEDIUM.drawMiddle(g, label, x + 12, y + HEIGHT / 2f, LABEL_SIZE, Ui.c(Theme.TEXT));
		renderControl(g, mx, my);
	}

	protected abstract void renderControl(GuiGraphicsExtractor g, int mx, int my);

	/** True while another row's popup covers this one, so hover effects are suppressed. */
	public static boolean blocked;

	public boolean mouseClicked(double mx, double my, int button) {
		return false;
	}

	public void mouseReleased() {
	}

	public boolean mouseDragged(double mx, double my) {
		return false;
	}

	public boolean keyPressed(KeyEvent event) {
		return false;
	}

	/** Rows with a popup (dropdowns) draw it here, above everything else. */
	public void renderOverlay(GuiGraphicsExtractor g, int mx, int my) {
	}

	public boolean overlayOpen() {
		return false;
	}

	/** Handles a click while the overlay is open; returns false to let it close and pass through. */
	public boolean overlayClicked(double mx, double my) {
		return false;
	}

	public void closeOverlay() {
	}

	public boolean capturesKeys() {
		return false;
	}

	/** Typed characters, for rows with a text field. */
	public boolean charTyped(CharacterEvent event) {
		return false;
	}

	/** True while a text field in this row has focus, so shortcuts must not fire. */
	public boolean typing() {
		return false;
	}

	// ======================================================================= toggle

	public static final class Toggle extends SettingRow {
		private final BooleanSupplier get;
		private final Consumer<Boolean> set;
		private final Switch sw;

		public Toggle(String label, BooleanSupplier get, Consumer<Boolean> set, Runnable changed) {
			super(label, changed);
			this.get = get;
			this.set = set;
			this.sw = new Switch(get.getAsBoolean());
		}

		@Override
		protected void renderControl(GuiGraphicsExtractor g, int mx, int my) {
			sw.render(g, x + w - 12 - Switch.W, y + (HEIGHT - Switch.H) / 2f, get.getAsBoolean(), Ui.inside(mx, my, x, y, w, HEIGHT));
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (button == 1 && Ui.inside(mx, my, x, y, w, HEIGHT)) {
				set.accept(!get.getAsBoolean());
				changed.run();
				return true;
			}
			return false;
		}
	}

	// ======================================================================= slider

	public static final class Slider extends SettingRow {
		private static final float TRACK_W = 118;
		private final NumberSetting setting;
		private final Animation fill;
		private final Animation knob = new Animation(0, 18);
		private boolean dragging;

		Slider(String label, NumberSetting setting, Runnable changed) {
			super(label, changed);
			this.setting = setting;
			this.fill = new Animation(setting.fraction(), 22);
		}

		private float trackX() {
			return x + w - 12 - 30 - TRACK_W;
		}

		@Override
		protected void renderControl(GuiGraphicsExtractor g, int mx, int my) {
			float tx = trackX();
			float cy = y + HEIGHT / 2f;
			float f = fill.target(setting.fraction()).get();
			boolean hot = dragging || Ui.inside(mx, my, tx - 4, y, TRACK_W + 8, HEIGHT);
			float k = knob.target(hot ? 1 : 0).get();
			Render2D.roundRect(g, tx, cy - 2, TRACK_W, 4, 2, Ui.c(0x24FFFFFF));
			if (f > 0.001f) {
				Render2D.roundRect(g, tx, cy - 2, Math.max(4, TRACK_W * f), 4, 2, Ui.accentPaint(tx, TRACK_W));
			}
			float kx = tx + TRACK_W * f;
			if (k > 0.01f) {
				Render2D.circle(g, kx, cy, 7.5f, Ui.c(ColorUtil.fade(Theme.accent(f), 0.22f * k)));
			}
			Render2D.circle(g, kx, cy + 0.5f, 5f, Ui.c(0x55000000));
			Render2D.circle(g, kx, cy, 4.5f + k * 0.5f, Ui.c(0xFFFFFFFF));
			Render2D.circle(g, kx, cy, 1.8f, Ui.c(Theme.accent(f)));
			String value = setting.display();
			FontRenderer.SEMIBOLD.drawRight(g, value, x + w - 12, cy, 8, Ui.c(dragging ? Theme.accent() : Theme.TEXT_DIM));
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			float tx = trackX();
			if (button == 1 && Ui.inside(mx, my, tx - 6, y, TRACK_W + 12, HEIGHT)) {
				dragging = true;
				update(mx);
				return true;
			}
			return false;
		}

		@Override
		public boolean mouseDragged(double mx, double my) {
			if (dragging) {
				update(mx);
				return true;
			}
			return false;
		}

		@Override
		public void mouseReleased() {
			if (dragging) {
				dragging = false;
				changed.run();
			}
		}

		private void update(double mx) {
			double f = (mx - trackX()) / TRACK_W;
			setting.setFraction(Math.max(0, Math.min(1, f)));
		}
	}

	// ======================================================================= mode

	public static final class Mode extends SettingRow {
		private static final float ITEM_H = 17;
		private final ModeSetting setting;
		private final boolean segmented;
		private final Animation indicator;
		private final Animation popup = new Animation(0, 18);
		private boolean open;
		private float bx, by, bw, bh;

		Mode(String label, ModeSetting setting, Runnable changed) {
			super(label, changed);
			this.setting = setting;
			float total = 0;
			for (String o : setting.options()) {
				total += FontRenderer.MEDIUM.width(optionLabel(o), 7.5f) + 14;
			}
			this.segmented = setting.options().size() <= 3 && total <= 150;
			this.indicator = new Animation(setting.index(), 16);
		}

		private String optionLabel(String option) {
			return I18n.get(setting.optionKey(option));
		}

		@Override
		protected void renderControl(GuiGraphicsExtractor g, int mx, int my) {
			if (segmented) {
				renderSegmented(g, mx, my);
			} else {
				renderDropdownButton(g, mx, my);
			}
		}

		private float[] segmentWidths() {
			List<String> options = setting.options();
			float[] widths = new float[options.size()];
			for (int i = 0; i < options.size(); i++) {
				widths[i] = FontRenderer.MEDIUM.width(optionLabel(options.get(i)), 7.5f) + 14;
			}
			return widths;
		}

		private void renderSegmented(GuiGraphicsExtractor g, int mx, int my) {
			float[] widths = segmentWidths();
			float total = 0;
			for (float sw : widths) {
				total += sw;
			}
			bh = 17;
			bw = total + 4;
			bx = x + w - 12 - bw;
			by = y + (HEIGHT - bh) / 2f;
			Render2D.roundRect(g, bx, by, bw, bh, bh / 2f, Ui.c(Theme.INPUT));
			Render2D.roundOutline(g, bx, by, bw, bh, bh / 2f, 0.6f, Ui.c(Theme.BORDER));
			float pos = indicator.target(setting.index()).get();
			// Indicator: interpolate between segment positions.
			int i0 = (int) Math.floor(pos);
			int i1 = Math.min(widths.length - 1, i0 + 1);
			float t = pos - i0;
			float x0 = bx + 2;
			float start0 = x0, start1 = x0;
			for (int i = 0; i < i0; i++) start0 += widths[i];
			for (int i = 0; i < i1; i++) start1 += widths[i];
			float ix = start0 + (start1 - start0) * t;
			float iw = widths[i0] + (widths[i1] - widths[i0]) * t;
			Render2D.roundRect(g, ix, by + 2, iw, bh - 4, (bh - 4) / 2f, Ui.accentPaint(ix, iw));
			float sx = x0;
			List<String> options = setting.options();
			for (int i = 0; i < options.size(); i++) {
				boolean active = setting.index() == i;
				boolean hov = Ui.inside(mx, my, sx, by, widths[i], bh);
				int color = active ? Theme.onAccent() : (hov ? Theme.TEXT : Theme.TEXT_DIM);
				FontRenderer.MEDIUM.drawCentered(g, optionLabel(options.get(i)), sx + widths[i] / 2f, by + bh / 2f, 7.5f, Ui.c(color));
				sx += widths[i];
			}
		}

		private void renderDropdownButton(GuiGraphicsExtractor g, int mx, int my) {
			bw = 112;
			bh = 18;
			bx = x + w - 12 - bw;
			by = y + (HEIGHT - bh) / 2f;
			boolean hov = Ui.inside(mx, my, bx, by, bw, bh);
			Render2D.roundRect(g, bx, by, bw, bh, 6, Ui.c(hov || open ? 0xFF161A22 : Theme.INPUT));
			Render2D.roundOutline(g, bx, by, bw, bh, 6, 0.7f, Ui.c(open ? Theme.accent() : (hov ? Theme.BORDER_STRONG : Theme.BORDER)));
			String text = FontRenderer.MEDIUM.ellipsize(optionLabel(setting.get()), 8, bw - 26);
			FontRenderer.MEDIUM.drawMiddle(g, text, bx + 8, by + bh / 2f, 8, Ui.c(Theme.TEXT));
			float p = popup.target(open).get();
			g.pose().pushMatrix();
			g.pose().translate(bx + bw - 10, by + bh / 2f);
			g.pose().rotate((float) Math.toRadians(180 * p));
			Render2D.chevron(g, 0, 0, 6, 1.2f, 1, Ui.c(Theme.TEXT_DIM));
			g.pose().popMatrix();
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (button != 1) {
				return false;
			}
			if (segmented) {
				float[] widths = segmentWidths();
				float sx = bx + 2;
				for (int i = 0; i < widths.length; i++) {
					if (Ui.inside(mx, my, sx, by, widths[i], bh)) {
						setting.set(setting.options().get(i));
						changed.run();
						return true;
					}
					sx += widths[i];
				}
				return false;
			}
			if (Ui.inside(mx, my, bx, by, bw, bh)) {
				open = !open;
				return true;
			}
			return false;
		}

		private float popupHeight() {
			return setting.options().size() * ITEM_H + 6;
		}

		/** Below the button, or above it when the list would run off the bottom of the screen. */
		private float popupY() {
			float below = by + bh + 4;
			float above = by - 4 - popupHeight();
			int screen = Minecraft.getInstance().getWindow().getGuiScaledHeight();
			return below + popupHeight() > screen - 6 && above >= 6 ? above : below;
		}

		@Override
		public void renderOverlay(GuiGraphicsExtractor g, int mx, int my) {
			float p = popup.get();
			if (p <= 0.01f) {
				return;
			}
			float px = bx;
			float py = popupY();
			float ph = popupHeight();
			float saved = Ui.alpha;
			Ui.alpha = saved * p;
			g.pose().pushMatrix();
			// Slides out of the button: down when below it, up when above.
			g.pose().translate(0, (1 - p) * (py < by ? 6 : -6));
			Render2D.shadow(g, px, py, bw, ph, 7, 12, Ui.c(0x90000000));
			Render2D.roundRect(g, px, py, bw, ph, 7, Ui.c(0xFA151820));
			Render2D.roundOutline(g, px, py, bw, ph, 7, 0.7f, Ui.c(Theme.BORDER_STRONG));
			float iy = py + 3;
			for (String option : setting.options()) {
				boolean active = setting.is(option);
				boolean hov = Ui.inside(mx, my, px, iy, bw, ITEM_H);
				if (active) {
					Render2D.roundRect(g, px + 3, iy, bw - 6, ITEM_H, 5, Ui.accentPaint(px, bw, 0.22f));
				} else if (hov) {
					Render2D.roundRect(g, px + 3, iy, bw - 6, ITEM_H, 5, Ui.c(0x10FFFFFF));
				}
				FontRenderer.MEDIUM.drawMiddle(g, FontRenderer.MEDIUM.ellipsize(optionLabel(option), 8, bw - 26), px + 9, iy + ITEM_H / 2f, 8,
					Ui.c(active ? Theme.TEXT : Theme.TEXT_DIM));
				if (active) {
					Render2D.checkmark(g, px + bw - 12, iy + ITEM_H / 2f, 6, 1.2f, Ui.c(Theme.accent()));
				}
				iy += ITEM_H;
			}
			g.pose().popMatrix();
			Ui.alpha = saved;
		}

		@Override
		public boolean overlayOpen() {
			return open;
		}

		@Override
		public boolean overlayClicked(double mx, double my) {
			float px = bx;
			float py = popupY();
			if (Ui.inside(mx, my, px, py, bw, popupHeight())) {
				int index = (int) ((my - py - 3) / ITEM_H);
				if (index >= 0 && index < setting.options().size()) {
					setting.set(setting.options().get(index));
					changed.run();
				}
				open = false;
				return true;
			}
			if (Ui.inside(mx, my, bx, by, bw, bh)) {
				open = false;
				return true;
			}
			open = false;
			return false;
		}

		@Override
		public void closeOverlay() {
			open = false;
		}
	}

	// ======================================================================= colour

	public static final class ColorPicker extends SettingRow {
		private static final float EXPANDED = 104;
		private static final int[] PRESETS = {0xFFFFFFFF, 0xFFF87171, 0xFFFB923C, 0xFFFBBF24, 0xFF4ADE80,
			0xFF2DD4BF, 0xFF38BDF8, 0xFF6366F1, 0xFFA855F7, 0xFFF472B6, 0xFF111111};
		private final ColorSetting setting;
		private final Animation expand = new Animation(0, 14);
		private boolean expanded;
		private float hue, sat, val;
		private int drag; // 0 none, 1 sv, 2 hue, 3 alpha
		private final Switch chroma;
		private float svX, svY, svW, svH, hueY, alphaY, barH = 6;

		ColorPicker(String label, ColorSetting setting, Runnable changed) {
			super(label, changed);
			this.setting = setting;
			this.chroma = new Switch(setting.chroma());
			syncFromSetting();
		}

		private void syncFromSetting() {
			float[] hsv = ColorUtil.toHsv(setting.get());
			if (hsv[1] > 0.01f && hsv[2] > 0.01f) {
				hue = hsv[0];
			}
			sat = hsv[1];
			val = hsv[2];
		}

		/** The alpha bar is only there for colours that allow transparency. */
		private float expandedHeight() {
			return setting.allowAlpha() ? EXPANDED : EXPANDED - 12;
		}

		@Override
		public float height() {
			return HEIGHT + expandedHeight() * expand.peek();
		}

		@Override
		protected void renderControl(GuiGraphicsExtractor g, int mx, int my) {
			float e = expand.target(expanded).get();
			if (drag == 0) {
				syncFromSetting();
			}
			float sw = 26, sh = 14;
			float sx = x + w - 12 - sw;
			float sy = y + (HEIGHT - sh) / 2f;
			Render2D.checkerboard(g, sx, sy, sw, sh, 3.5f, Ui.c(0xFFD9D9D9), Ui.c(0xFF9A9A9A));
			Render2D.roundRect(g, sx, sy, sw, sh, 0, Ui.c(setting.color()));
			Render2D.roundOutline(g, sx - 1, sy - 1, sw + 2, sh + 2, 4, 1.5f, Ui.c(Theme.PANEL));
			Render2D.roundOutline(g, sx - 1, sy - 1, sw + 2, sh + 2, 4, 0.6f, Ui.c(Theme.BORDER_STRONG));
			String hex = setting.chroma() ? "Chroma" : ColorUtil.toHex(setting.get(), setting.allowAlpha()).substring(1);
			FontRenderer.MEDIUM.drawRight(g, hex, sx - 7, y + HEIGHT / 2f, 7.5f, Ui.c(Theme.TEXT_MUTED));

			if (e <= 0.01f) {
				return;
			}
			float saved = Ui.alpha;
			Ui.alpha = saved * e;
			float top = y + HEIGHT;
			g.enableScissor(Math.round(x), Math.round(top), Math.round(x + w), Math.round(top + expandedHeight() * e));
			svX = x + 12;
			svY = top + 2;
			svW = Math.min(170, w - 130);
			svH = 56;
			int pure = ColorUtil.hsv(hue, 1, 1, 255);
			Render2D.horizontalGradient(g, svX, svY, svW, svH, Ui.c(0xFFFFFFFF), Ui.c(pure));
			Render2D.verticalGradient(g, svX, svY, svW, svH, Ui.c(0x00000000), Ui.c(0xFF000000));
			Render2D.roundOutline(g, svX - 1, svY - 1, svW + 2, svH + 2, 3, 1.2f, Ui.c(Theme.PANEL));
			float cx = svX + sat * svW;
			float cy = svY + (1 - val) * svH;
			Render2D.ring(g, cx, cy, 3.6f, 1.6f, Ui.c(0xFFFFFFFF));
			Render2D.ring(g, cx, cy, 5f, 0.8f, Ui.c(0x66000000));

			hueY = svY + svH + 6;
			Render2D.hueBar(g, svX, hueY, svW, barH);
			Render2D.roundOutline(g, svX - 1, hueY - 1, svW + 2, barH + 2, 3, 1.2f, Ui.c(Theme.PANEL));
			knob(g, svX + hue * svW, hueY + barH / 2f);

			alphaY = hueY + barH + 6;
			if (setting.allowAlpha()) {
				Render2D.checkerboard(g, svX, alphaY, svW, barH, 3, Ui.c(0xFFD9D9D9), Ui.c(0xFF9A9A9A));
				int rgb = setting.get() & 0xFFFFFF;
				Render2D.horizontalGradient(g, svX, alphaY, svW, barH, Ui.c(rgb), Ui.c(0xFF000000 | rgb));
				Render2D.roundOutline(g, svX - 1, alphaY - 1, svW + 2, barH + 2, 3, 1.2f, Ui.c(Theme.PANEL));
				knob(g, svX + ColorUtil.alpha(setting.get()) / 255f * svW, alphaY + barH / 2f);
			}

			// Right column: chroma toggle and presets.
			float rx = svX + svW + 16;
			FontRenderer.MEDIUM.drawMiddle(g, I18n.get("aurora.ui.chroma"), rx, svY + 7, 8, Ui.c(Theme.TEXT));
			chroma.render(g, rx, svY + 15, setting.chroma(), Ui.inside(mx, my, rx, svY + 13, Switch.W, Switch.H + 4));
			FontRenderer.MEDIUM.drawMiddle(g, I18n.get("aurora.ui.presets"), rx, svY + 41, 8, Ui.c(Theme.TEXT));
			float px = rx;
			float py = svY + 50;
			for (int i = 0; i < PRESETS.length; i++) {
				float dx = px + (i % 6) * 13 + 5;
				float dy = py + (i / 6) * 13 + 5;
				boolean hov = Ui.inside(mx, my, dx - 5, dy - 5, 10, 10);
				Render2D.circle(g, dx, dy, hov ? 5f : 4.4f, Ui.c(PRESETS[i]));
				Render2D.ring(g, dx, dy, hov ? 5f : 4.4f, 0.7f, Ui.c(0x40FFFFFF));
			}
			g.disableScissor();
			Ui.alpha = saved;
		}

		private void knob(GuiGraphicsExtractor g, float kx, float ky) {
			Render2D.roundRect(g, kx - 2.5f, ky - 5, 5, 10, 2.5f, Ui.c(0x66000000));
			Render2D.roundRect(g, kx - 2, ky - 4.5f, 4, 9, 2, Ui.c(0xFFFFFFFF));
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (button != 1) {
				return false;
			}
			if (Ui.inside(mx, my, x, y, w, HEIGHT)) {
				expanded = !expanded;
				return true;
			}
			if (!expanded || expand.peek() < 0.5f) {
				return false;
			}
			if (Ui.inside(mx, my, svX - 2, svY - 2, svW + 4, svH + 4)) {
				drag = 1;
			} else if (Ui.inside(mx, my, svX - 3, hueY - 3, svW + 6, barH + 6)) {
				drag = 2;
			} else if (setting.allowAlpha() && Ui.inside(mx, my, svX - 3, alphaY - 3, svW + 6, barH + 6)) {
				drag = 3;
			}
			if (drag != 0) {
				setting.setChroma(false);
				update(mx, my);
				return true;
			}
			float rx = svX + svW + 16;
			if (Ui.inside(mx, my, rx, svY + 13, Switch.W, Switch.H + 4)) {
				setting.setChroma(!setting.chroma());
				changed.run();
				return true;
			}
			float py = svY + 50;
			for (int i = 0; i < PRESETS.length; i++) {
				float dx = rx + (i % 6) * 13 + 5;
				float dy = py + (i / 6) * 13 + 5;
				if (Ui.inside(mx, my, dx - 5, dy - 5, 10, 10)) {
					int alpha = ColorUtil.alpha(setting.get());
					setting.setChroma(false);
					setting.set(ColorUtil.withAlpha(PRESETS[i], alpha));
					syncFromSetting();
					changed.run();
					return true;
				}
			}
			return Ui.inside(mx, my, x, y, w, height());
		}

		@Override
		public boolean mouseDragged(double mx, double my) {
			if (drag != 0) {
				update(mx, my);
				return true;
			}
			return false;
		}

		@Override
		public void mouseReleased() {
			if (drag != 0) {
				drag = 0;
				changed.run();
			}
		}

		private void update(double mx, double my) {
			float fx = (float) Math.max(0, Math.min(1, (mx - svX) / svW));
			int alpha = ColorUtil.alpha(setting.get());
			switch (drag) {
				case 1 -> {
					sat = fx;
					val = (float) Math.max(0, Math.min(1, 1 - (my - svY) / svH));
				}
				case 2 -> hue = Math.min(0.999f, fx);
				case 3 -> alpha = Math.round(fx * 255);
				default -> {
				}
			}
			setting.set(ColorUtil.hsv(hue, sat, val, alpha));
		}
	}

	// ======================================================================= keybind

	public static final class Keybind extends SettingRow {
		private final IntSupplier get;
		private final IntConsumer set;
		private boolean listening;
		private final Animation pulse = new Animation(0, 10);

		public Keybind(String label, IntSupplier get, IntConsumer set, Runnable changed) {
			super(label, changed);
			this.get = get;
			this.set = set;
		}

		private String keyName() {
			int key = get.getAsInt();
			if (key == KeySetting.NONE) {
				return I18n.get("aurora.ui.none");
			}
			return dev.aurora.util.KeyNames.ofKeyboard(key);
		}

		@Override
		protected void renderControl(GuiGraphicsExtractor g, int mx, int my) {
			String text = listening ? I18n.get("aurora.ui.press_key") : keyName();
			float bw = Math.max(54, FontRenderer.SEMIBOLD.width(text, 7.5f) + 18);
			float bh = 18;
			float bx = x + w - 12 - bw;
			float by = y + (HEIGHT - bh) / 2f;
			float p = pulse.target(listening).get();
			boolean hov = Ui.inside(mx, my, bx, by, bw, bh);
			if (p > 0.01f) {
				float wave = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 160.0));
				Render2D.glow(g, bx, by, bw, bh, 6, 6, Ui.c(ColorUtil.fade(Theme.accent(), (0.15f + 0.2f * wave) * p)));
			}
			Render2D.roundRect(g, bx, by, bw, bh, 6, Ui.c(hov ? 0xFF161A22 : Theme.INPUT));
			Render2D.roundOutline(g, bx, by, bw, bh, 6, 0.7f, Ui.c(ColorUtil.lerp(hov ? Theme.BORDER_STRONG : Theme.BORDER, Theme.accent(), p)));
			FontRenderer.SEMIBOLD.drawCentered(g, text, bx + bw / 2f, by + bh / 2f, 7.5f,
				Ui.c(listening ? Theme.accent() : (get.getAsInt() == KeySetting.NONE ? Theme.TEXT_MUTED : Theme.TEXT)));
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (listening) {
				listening = false;
				return true;
			}
			if (button == 1 && Ui.inside(mx, my, x, y, w, HEIGHT)) {
				listening = true;
				return true;
			}
			if (button == 3 && Ui.inside(mx, my, x, y, w, HEIGHT)) {
				set.accept(KeySetting.NONE);
				changed.run();
				return true;
			}
			return false;
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (!listening) {
				return false;
			}
			if (event.isEscape() || event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) {
				set.accept(KeySetting.NONE);
			} else {
				set.accept(event.key());
			}
			listening = false;
			changed.run();
			return true;
		}

		@Override
		public boolean capturesKeys() {
			return listening;
		}
	}

	// ======================================================================= button row

	/** A row with a single action button, e.g. "Edit position". */
	public static final class Action extends SettingRow {
		private final String buttonText;
		private final Runnable action;
		private float bx, by, bw, bh;

		public Action(String label, String buttonText, Runnable action) {
			super(label, () -> {
			});
			this.buttonText = buttonText;
			this.action = action;
		}

		@Override
		protected void renderControl(GuiGraphicsExtractor g, int mx, int my) {
			bw = FontRenderer.SEMIBOLD.width(buttonText, 7.5f) + 20;
			bh = 18;
			bx = x + w - 12 - bw;
			by = y + (HEIGHT - bh) / 2f;
			boolean hov = Ui.inside(mx, my, bx, by, bw, bh);
			Render2D.roundRect(g, bx, by, bw, bh, 6, Ui.accentPaint(bx, bw, hov ? 0.32f : 0.2f));
			Render2D.roundOutline(g, bx, by, bw, bh, 6, 0.7f, Ui.c(ColorUtil.fade(Theme.accent(), hov ? 0.9f : 0.5f)));
			FontRenderer.SEMIBOLD.drawCentered(g, buttonText, bx + bw / 2f, by + bh / 2f, 7.5f, Ui.c(Theme.TEXT));
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (button == 1 && Ui.inside(mx, my, bx, by, bw, bh)) {
				action.run();
				return true;
			}
			return false;
		}
	}

	// ======================================================================= section header

	public static final class Header extends SettingRow {
		public Header(String label) {
			super(label, () -> {
			});
		}

		@Override
		public float height() {
			return 20;
		}

		@Override
		public void render(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w) {
			this.x = x;
			this.y = y;
			this.w = w;
			FontRenderer.SEMIBOLD.drawMiddle(g, label.toUpperCase(java.util.Locale.ROOT), x + 12, y + 12, 7, Ui.c(Theme.TEXT_MUTED));
		}

		@Override
		protected void renderControl(GuiGraphicsExtractor g, int mx, int my) {
		}
	}

	public static @Nullable SettingRow nullRow() {
		return null;
	}
}
