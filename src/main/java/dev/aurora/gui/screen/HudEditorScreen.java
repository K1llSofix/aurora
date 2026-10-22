package dev.aurora.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.Aurora;
import dev.aurora.gui.HudLayer;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.gui.widget.Switch;
import dev.aurora.gui.widget.Ui;
import dev.aurora.module.HudModule;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.Easing;
import dev.aurora.util.Motion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Drag HUD elements around with magnetic guides; scroll to scale; right click for settings. */
public final class HudEditorScreen extends Screen {
	private static final float SNAP = 5f;
	private static final int LEFT = 1;
	private static final int RIGHT = 3;
	private static boolean snapping = true;
	private static boolean listOpen = true;

	private final @Nullable Screen parent;
	private final long openedAt = System.currentTimeMillis();
	private @Nullable HudModule selected;
	private @Nullable HudModule dragging;
	private float grabX, grabY;
	private final List<Float> guidesX = new ArrayList<>();
	private final List<Float> guidesY = new ArrayList<>();
	private final Map<Object, Animation> hovers = new HashMap<>();
	private final Map<HudModule, Switch> switches = new HashMap<>();
	private final Animation listAnim = new Animation(listOpen ? 1 : 0, 14);
	private long scaleHintUntil;

	// Toolbar geometry (from the last frame)
	private final List<Button> buttons = new ArrayList<>();

	private record Button(String id, float x, float y, float w, float h) {
	}

	public HudEditorScreen(@Nullable Screen parent, @Nullable HudModule focus) {
		super(Component.translatable("aurora.editor.title"));
		this.parent = parent;
		this.selected = focus;
	}

	public HudEditorScreen() {
		this(null, null);
	}

	private Animation hover(Object key, boolean on) {
		return hovers.computeIfAbsent(key, k -> new Animation(0, 16)).target(on);
	}

	private List<HudModule> active() {
		List<HudModule> list = new ArrayList<>();
		for (HudModule m : Aurora.modules().hud()) {
			if (m.isEnabled()) {
				list.add(m);
			}
		}
		return list;
	}

	private float ex(HudModule m) {
		return m.x(width);
	}

	private float ey(HudModule m) {
		return m.y(height);
	}

	private @Nullable HudModule elementAt(double mx, double my) {
		List<HudModule> list = active();
		for (int i = list.size() - 1; i >= 0; i--) {
			HudModule m = list.get(i);
			if (Ui.inside(mx, my, ex(m) - 2, ey(m) - 2, m.scaledWidth() + 4, m.scaledHeight() + 4)) {
				return m;
			}
		}
		return null;
	}

	// ======================================================================= rendering

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		float vis = Easing.OUT_CUBIC.clamped(Motion.progress(openedAt, 300));
		if (minecraft.level == null) {
			extractPanorama(g, a);
		}
		Render2D.rect(g, 0, 0, width, height, ColorUtil.fade(0x55000000, vis));
		// Faint layout grid
		int grid = ColorUtil.fade(0x0AFFFFFF, vis);
		Render2D.Batch lines = Render2D.batch();
		for (int x = 16; x < width; x += 16) {
			lines.rect(x, 0, 0.5f, height, grid, grid, grid, grid);
		}
		for (int y = 16; y < height; y += 16) {
			lines.rect(0, y, width, 0.5f, grid, grid, grid, grid);
		}
		lines.submit(g);
		int center = ColorUtil.fade(0x22FFFFFF, vis);
		Render2D.rect(g, width / 2f - 0.25f, 0, 0.5f, height, center);
		Render2D.rect(g, 0, height / 2f - 0.25f, width, 0.5f, center);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Ui.alpha = Easing.OUT_CUBIC.clamped(Motion.progress(openedAt, 300));
		HudModule hovered = dragging != null ? dragging : elementAt(mouseX, mouseY);
		boolean overUi = overUi(mouseX, mouseY);
		if (overUi) {
			hovered = null;
		}

		for (HudModule m : active()) {
			HudLayer.draw(g, m, a, true);
			float x = ex(m);
			float y = ey(m);
			float w = m.scaledWidth();
			float h = m.scaledHeight();
			float hv = hover(m, m == hovered || m == selected).get();
			if (hv > 0.01f) {
				Render2D.roundRect(g, x - 2, y - 2, w + 4, h + 4, 5, Ui.c(ColorUtil.fade(Theme.accent(), 0.07f * hv)));
				Render2D.roundOutline(g, x - 2, y - 2, w + 4, h + 4, 5, 1f, Ui.c(ColorUtil.fade(Theme.accent(), 0.4f + 0.6f * hv)));
			} else {
				Render2D.dashedRect(g, x - 2, y - 2, w + 4, h + 4, 3, 0.6f, Ui.c(0x40FFFFFF));
			}
		}

		// Guides while dragging
		if (dragging != null) {
			for (float gx : guidesX) {
				Render2D.rect(g, gx - 0.5f, 0, 1, height, Ui.c(ColorUtil.fade(Theme.accent(), 0.85f)));
			}
			for (float gy : guidesY) {
				Render2D.rect(g, 0, gy - 0.5f, width, 1, Ui.c(ColorUtil.fade(Theme.accent2(), 0.85f)));
			}
		}

		// Label for the selected / hovered element
		HudModule labelled = dragging != null ? dragging : (hovered != null ? hovered : selected);
		if (labelled != null && labelled.isEnabled()) {
			drawLabel(g, labelled);
		}

		g.nextStratum();
		drawElementList(g, mouseX, mouseY);
		drawToolbar(g, mouseX, mouseY);
		Ui.alpha = 1f;
	}

	private void drawLabel(GuiGraphicsExtractor g, HudModule m) {
		boolean showScale = System.currentTimeMillis() < scaleHintUntil || m == dragging;
		String text = m.name() + (showScale ? "  ·  " + Math.round(m.scale() * 100) + "%" : "");
		float tw = FontRenderer.SEMIBOLD.width(text, 7.5f) + 12;
		float x = Math.max(2, Math.min(width - tw - 2, ex(m) + m.scaledWidth() / 2f - tw / 2f));
		float y = ey(m) - 17;
		if (y < 2) {
			y = ey(m) + m.scaledHeight() + 5;
		}
		Render2D.shadow(g, x, y, tw, 13, 5, 6, Ui.c(0x70000000));
		Render2D.roundRect(g, x, y, tw, 13, 5, Ui.accentPaint(x, tw));
		FontRenderer.SEMIBOLD.drawCentered(g, text, x + tw / 2f, y + 6.5f, 7.5f, Ui.c(Theme.onAccent()));
	}

	private float listX() {
		float p = listAnim.peek();
		return 8 - (1 - p) * 150;
	}

	private static final float LIST_W = 140;
	private static final float LIST_ROW = 17;
	private static final float LIST_HEAD = 24;
	private final Animation listScroll = new Animation(0, 16);
	private float listScrollTarget;

	/** {x, y, w, h, contentHeight} of the element list, sized to stay clear of the toolbar. */
	private float[] listBox() {
		int n = Aurora.modules().hud().size();
		float content = LIST_HEAD + n * LIST_ROW + 6;
		float h = Math.min(content, height - 70);
		float y = Math.max(8, (height - 56 - h) / 2f);
		return new float[]{listX(), y, LIST_W, h, content};
	}

	private void drawElementList(GuiGraphicsExtractor g, int mx, int my) {
		float p = listAnim.target(listOpen).get();
		List<HudModule> all = Aurora.modules().hud();
		float[] box = listBox();
		float rowH = LIST_ROW;
		float x = box[0], y = box[1], w = box[2], h = box[3];
		float maxScroll = Math.max(0, box[4] - h);
		listScrollTarget = Math.max(0, Math.min(maxScroll, listScrollTarget));
		float scroll = listScroll.target(listScrollTarget).get();
		if (p > 0.01f) {
			float saved = Ui.alpha;
			Ui.alpha = saved * p;
			Render2D.shadow(g, x, y, w, h, 10, 16, Ui.c(0x90000000));
			Render2D.roundRect(g, x, y, w, h, 10, Ui.c(0xF2111318));
			Render2D.roundOutline(g, x, y, w, h, 10, 0.7f, Ui.c(Theme.BORDER));
			FontRenderer.SEMIBOLD.drawMiddle(g, I18n.get("aurora.editor.elements").toUpperCase(Locale.ROOT), x + 10, y + 13, 6.5f, Ui.c(Theme.TEXT_MUTED));
			g.enableScissor(Math.round(x), Math.round(y + LIST_HEAD - 2), Math.round(x + w), Math.round(y + h - 3));
			float ry = y + LIST_HEAD - scroll;
			for (HudModule m : all) {
				boolean hov = Ui.inside(mx, my, x + 4, ry, w - 8, rowH) && my > y + LIST_HEAD - 2 && my < y + h - 3;
				float hv = hover("row:" + m.id(), hov).get();
				if (hv > 0.01f) {
					Render2D.roundRect(g, x + 4, ry, w - 8, rowH, 5, Ui.c(ColorUtil.withAlpha(0xFFFFFF, Math.round(10 * hv))));
				}
				FontRenderer.MEDIUM.drawMiddle(g, FontRenderer.MEDIUM.ellipsize(m.name(), 7.5f, w - 50), x + 10, ry + rowH / 2f, 7.5f,
					Ui.c(m.isEnabled() ? Theme.TEXT : Theme.TEXT_MUTED));
				Switch sw = switches.computeIfAbsent(m, k -> new Switch(k.isEnabled()));
				g.pose().pushMatrix();
				g.pose().translate(x + w - 10 - Switch.W * 0.8f, ry + rowH / 2f - Switch.H * 0.4f);
				g.pose().scale(0.8f, 0.8f);
				sw.render(g, 0, 0, m.isEnabled(), hov);
				g.pose().popMatrix();
				ry += rowH;
			}
			g.disableScissor();
			if (maxScroll > 0) {
				float trackH = h - LIST_HEAD - 8;
				float thumbH = Math.max(14, trackH * h / box[4]);
				float thumbY = y + LIST_HEAD + (trackH - thumbH) * (scroll / maxScroll);
				Render2D.roundRect(g, x + w - 5, thumbY, 2, thumbH, 1, Ui.c(0x30FFFFFF));
			}
			Ui.alpha = saved;
		}
		// Tab that opens / closes the list.
		float tabX = x + (p > 0.5f ? w + 4 : 150 * (1 - p) + 0);
		tabX = p > 0.01f ? x + w + 4 : 4;
		float tabY = y + h / 2f - 14;
		boolean tabHov = Ui.inside(mx, my, tabX, tabY, 14, 28);
		float th = hover("tab", tabHov).get();
		Render2D.roundRect(g, tabX, tabY, 14, 28, 5, Ui.c(ColorUtil.lerp(0xE6111318, 0xF21B1F28, th)));
		Render2D.roundOutline(g, tabX, tabY, 14, 28, 5, 0.6f, Ui.c(Theme.BORDER));
		Render2D.chevron(g, tabX + 7, tabY + 14, 6, 1.3f, listOpen ? 2 : 0, Ui.c(Theme.TEXT_DIM));
		buttons.removeIf(b -> b.id.equals("tab"));
		buttons.add(new Button("tab", tabX, tabY, 14, 28));
	}

	private void drawToolbar(GuiGraphicsExtractor g, int mx, int my) {
		buttons.removeIf(b -> !b.id.equals("tab"));
		String[] ids = {"mods", "snap", "reset", "done"};
		String[] labels = {
			I18n.get("aurora.editor.mods"),
			I18n.get("aurora.editor.snap") + ": " + I18n.get(snapping ? "aurora.ui.on" : "aurora.ui.off"),
			I18n.get("aurora.editor.reset"),
			I18n.get("aurora.editor.done")
		};
		float bh = 22;
		float gap = 6;
		float total = -gap;
		float[] widths = new float[ids.length];
		for (int i = 0; i < ids.length; i++) {
			widths[i] = FontRenderer.SEMIBOLD.width(labels[i], 8) + 22;
			total += widths[i] + gap;
		}
		float pad = 5;
		float bx = (width - total) / 2f - pad;
		float by = height - bh - 14 - pad;
		Render2D.shadow(g, bx, by, total + pad * 2, bh + pad * 2, 11, 16, Ui.c(0x90000000));
		Render2D.roundRect(g, bx, by, total + pad * 2, bh + pad * 2, 11, Ui.c(0xF0111318));
		Render2D.roundOutline(g, bx, by, total + pad * 2, bh + pad * 2, 11, 0.7f, Ui.c(Theme.BORDER));
		float x = bx + pad;
		float y = by + pad;
		for (int i = 0; i < ids.length; i++) {
			boolean hov = Ui.inside(mx, my, x, y, widths[i], bh);
			float hv = hover("btn:" + ids[i], hov).get();
			boolean primary = ids[i].equals("done");
			if (primary) {
				Render2D.roundRect(g, x, y, widths[i], bh, 7, Render2D.Paint.horizontal(x, widths[i],
					Ui.c(ColorUtil.shade(Theme.accent(), hv * 0.12f)), Ui.c(ColorUtil.shade(Theme.accent2(), hv * 0.12f))));
			} else {
				Render2D.roundRect(g, x, y, widths[i], bh, 7, Ui.c(ColorUtil.lerp(0xFF1A1E27, 0xFF242936, hv)));
			}
			int color = primary ? Theme.onAccent() : (ids[i].equals("snap") && snapping ? Theme.TEXT : ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv));
			FontRenderer.SEMIBOLD.drawCentered(g, labels[i], x + widths[i] / 2f, y + bh / 2f, 8, Ui.c(color));
			buttons.add(new Button(ids[i], x, y, widths[i], bh));
			x += widths[i] + gap;
		}
		String hint = I18n.get("aurora.editor.hint");
		FontRenderer.MEDIUM.drawCentered(g, hint, width / 2f, by - 9, 7, Ui.c(0xB0FFFFFF), true);
	}

	private boolean overUi(double mx, double my) {
		for (Button b : buttons) {
			if (Ui.inside(mx, my, b.x - 6, b.y - 6, b.w + 12, b.h + 12)) {
				return true;
			}
		}
		if (listOpen) {
			float[] box = listBox();
			return Ui.inside(mx, my, box[0], box[1], box[2], box[3]);
		}
		return false;
	}

	// ======================================================================= input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();
		for (Button b : buttons) {
			if (Ui.inside(mx, my, b.x, b.y, b.w, b.h)) {
				switch (b.id) {
					case "mods" -> openMenu(null);
					case "snap" -> snapping = !snapping;
					case "reset" -> {
						if (selected != null) {
							selected.resetPosition();
						} else {
							Aurora.modules().hud().forEach(HudModule::resetPosition);
						}
						Aurora.config().markDirty();
					}
					case "done" -> onClose();
					case "tab" -> listOpen = !listOpen;
					default -> {
					}
				}
				return true;
			}
		}
		if (listOpen) {
			List<HudModule> all = Aurora.modules().hud();
			float[] box = listBox();
			if (Ui.inside(mx, my, box[0], box[1], box[2], box[3])) {
				if (my >= box[1] + LIST_HEAD - 2 && my < box[1] + box[3] - 3) {
					int index = (int) ((my - box[1] - LIST_HEAD + listScroll.peek()) / LIST_ROW);
					if (index >= 0 && index < all.size()) {
						all.get(index).toggle();
						Aurora.config().markDirty();
					}
				}
				return true;
			}
		}
		HudModule hit = elementAt(mx, my);
		if (hit != null) {
			selected = hit;
			if (button == RIGHT) {
				openMenu(hit);
				return true;
			}
			if (button == LEFT) {
				dragging = hit;
				grabX = (float) mx - ex(hit);
				grabY = (float) my - ey(hit);
			}
			return true;
		}
		selected = null;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) {
			return super.mouseDragged(event, dx, dy);
		}
		float x = (float) event.x() - grabX;
		float y = (float) event.y() - grabY;
		float w = dragging.scaledWidth();
		float h = dragging.scaledHeight();
		guidesX.clear();
		guidesY.clear();
		boolean free = event.hasAltDown();
		if (snapping && !free) {
			x = snapAxis(x, w, width, true);
			y = snapAxis(y, h, height, false);
		}
		x = Math.max(0, Math.min(width - w, x));
		y = Math.max(0, Math.min(height - h, y));
		dragging.setPosition(x, y, width, height);
		return true;
	}

	/** Snaps an element's start, centre or end to screen edges, the centre line or other elements. */
	private float snapAxis(float pos, float size, float screen, boolean horizontal) {
		List<Float> targets = new ArrayList<>();
		targets.add(0f);
		targets.add(screen / 2f);
		targets.add(screen);
		for (HudModule other : active()) {
			if (other == dragging) {
				continue;
			}
			float o = horizontal ? ex(other) : ey(other);
			float os = horizontal ? other.scaledWidth() : other.scaledHeight();
			targets.add(o);
			targets.add(o + os / 2f);
			targets.add(o + os);
		}
		float[] anchors = {0, size / 2f, size};
		float best = Float.MAX_VALUE;
		float bestOffset = 0;
		float bestLine = -1;
		for (float anchor : anchors) {
			for (float t : targets) {
				float d = t - (pos + anchor);
				if (Math.abs(d) < SNAP && Math.abs(d) < Math.abs(best)) {
					best = d;
					bestOffset = d;
					bestLine = t;
				}
			}
		}
		if (bestLine >= 0) {
			(horizontal ? guidesX : guidesY).add(bestLine);
			return pos + bestOffset;
		}
		return pos;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			guidesX.clear();
			guidesY.clear();
			Aurora.config().markDirty();
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (listOpen) {
			float[] box = listBox();
			if (Ui.inside(x, y, box[0], box[1], box[2], box[3])) {
				listScrollTarget -= (float) scrollY * 20;
				return true;
			}
		}
		HudModule m = elementAt(x, y);
		if (m == null) {
			m = selected;
		}
		if (m != null && scrollY != 0) {
			float cx = ex(m) + m.scaledWidth() / 2f;
			float cy = ey(m) + m.scaledHeight() / 2f;
			m.setScale(m.scale() + (scrollY > 0 ? 0.05f : -0.05f));
			// Keep the element centred on the same spot while it grows or shrinks.
			m.setPosition(cx - m.scaledWidth() / 2f, cy - m.scaledHeight() / 2f, width, height);
			scaleHintUntil = System.currentTimeMillis() + 1200;
			selected = m;
			Aurora.config().markDirty();
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (selected != null) {
			float step = event.hasShiftDown() ? 10 : 1;
			float dx = 0, dy = 0;
			if (event.isLeft()) dx = -step;
			if (event.isRight()) dx = step;
			if (event.isUp()) dy = -step;
			if (event.isDown()) dy = step;
			if (dx != 0 || dy != 0) {
				selected.setPosition(ex(selected) + dx, ey(selected) + dy, width, height);
				Aurora.config().markDirty();
				return true;
			}
			if (event.key() == InputConstants.KEY_R) {
				selected.resetPosition();
				Aurora.config().markDirty();
				return true;
			}
			if (event.key() == InputConstants.KEY_DELETE || event.key() == InputConstants.KEY_BACKSPACE) {
				selected.setEnabled(false);
				selected = null;
				Aurora.config().markDirty();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private void openMenu(@Nullable HudModule focus) {
		Aurora.config().save();
		minecraft.gui.setScreen(new ModMenuScreen(parent instanceof ModMenuScreen ? null : parent, focus));
	}

	@Override
	public void onClose() {
		Aurora.config().save();
		minecraft.gui.setScreen(parent instanceof ModMenuScreen ? new ModMenuScreen(null) : parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
