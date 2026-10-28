package dev.aurora.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.Aurora;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.gui.widget.TextField;
import dev.aurora.gui.widget.Ui;
import dev.aurora.module.utility.WaypointsModule;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.Easing;
import dev.aurora.util.KeyNames;
import dev.aurora.util.Motion;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The points of the current world: rename, recolour, hide, delete, or add one where you stand. */
public final class WaypointsScreen extends Screen {
	private static final float ROW = 30;

	private final @Nullable Screen parent;
	private final WaypointsModule waypoints;
	private final long openedAt = System.currentTimeMillis();
	private final Map<String, Animation> hovers = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();
	private final TextField nameField = new TextField("", 32).plain();
	private WaypointsModule.@Nullable Point editing;
	private float scroll;

	private record Hit(String id, float x, float y, float w, float h, WaypointsModule.@Nullable Point point) {
	}

	public WaypointsScreen(@Nullable Screen parent, WaypointsModule waypoints) {
		super(Component.translatable("aurora.waypoints.title"));
		this.parent = parent;
		this.waypoints = waypoints;
	}

	private Animation hover(String id, boolean on) {
		return hovers.computeIfAbsent(id, k -> new Animation(0, 14)).target(on);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Render2D.rect(g, 0, 0, width, height, 0x8C05070D);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		hits.clear();
		float appear = Easing.OUT_CUBIC.clamped(Motion.progress(openedAt, 260));
		Ui.alpha = appear;
		float w = Math.min(380, width - 32);
		float h = Math.min(300, height - 24);
		float x = (width - w) / 2f;
		float y = (height - h) / 2f + (1 - appear) * 10;
		Render2D.shadow(g, x, y, w, h, 14, 20, Ui.c(0x8C000000));
		Render2D.roundRect(g, x, y, w, h, 14, Render2D.Paint.vertical(y, h, Ui.c(0xF5171A23), Ui.c(0xF50E1016)));
		Render2D.roundOutline(g, x, y, w, h, 14, 0.8f, Render2D.Paint.horizontal(x, w, Ui.c(ColorUtil.fade(Theme.accent(), 0.5f)), Ui.c(0x1FFFFFFF)));

		FontRenderer.SEMIBOLD.drawMiddle(g, I18n.get("aurora.waypoints.title"), x + 18, y + 20, 13, Ui.c(Theme.TEXT));
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(Aurora.waypointKey);
		String keyName = key.equals(InputConstants.UNKNOWN) ? "—" : KeyNames.of(key);
		String subtitle = I18n.get("aurora.waypoints.subtitle", WaypointsModule.worldName(), keyName);
		FontRenderer.MEDIUM.drawMiddle(g, FontRenderer.MEDIUM.ellipsize(subtitle, 7.5f, w - 60), x + 18, y + 35, 7.5f, Ui.c(Theme.TEXT_DIM));
		closeButton(g, mouseX, mouseY, x + w - 24, y + 14);

		float top = y + 50;
		float bottom = y + h - 44;
		list(g, mouseX, mouseY, x + 12, top, w - 24, bottom - top);
		float bw = (w - 36 - 8) / 2f;
		button(g, mouseX, mouseY, "add", I18n.get("aurora.waypoints.add_here"), x + 18, y + h - 34, bw, 22, true);
		button(g, mouseX, mouseY, "close", I18n.get("gui.done"), x + 26 + bw, y + h - 34, bw, 22, false);
		Ui.alpha = 1f;
	}

	private void list(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w, float h) {
		List<WaypointsModule.Point> points = waypoints.points();
		if (points.isEmpty()) {
			List<String> lines = FontRenderer.MEDIUM.wrap(I18n.get("aurora.waypoints.empty"), 8, w - 40);
			float ly = y + h / 2f - lines.size() * 5;
			for (String line : lines) {
				FontRenderer.MEDIUM.drawCentered(g, line, x + w / 2f, ly, 8, Ui.c(Theme.TEXT_DIM));
				ly += 11;
			}
			return;
		}
		float content = points.size() * (ROW + 4);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, content - h)));
		g.enableScissor(Math.round(x - 2), Math.round(y), Math.round(x + w + 2), Math.round(y + h));
		float ry = y - scroll;
		for (WaypointsModule.Point point : points) {
			if (ry + ROW >= y && ry <= y + h) {
				row(g, mx, my, point, x, ry, w);
			}
			ry += ROW + 4;
		}
		g.disableScissor();
	}

	private void row(GuiGraphicsExtractor g, int mx, int my, WaypointsModule.Point point, float x, float y, float w) {
		String id = "row" + System.identityHashCode(point);
		float hv = hover(id, Ui.inside(mx, my, x, y, w, ROW)).get();
		Render2D.roundRect(g, x, y, w, ROW, 9, Ui.c(ColorUtil.lerp(0x66161922, 0x991D212B, hv)));
		Render2D.roundOutline(g, x, y, w, ROW, 9, 0.7f, Ui.c(ColorUtil.lerp(0x14FFFFFF, 0x2EFFFFFF, hv)));
		int dim = point.visible ? 0xFF : 0x80;

		// The colour dot: a click gives the next colour.
		float dx = x + 15;
		float dy = y + ROW / 2f;
		Render2D.circle(g, dx, dy, 5.5f, Ui.c(ColorUtil.withAlpha(point.color, dim)));
		Render2D.ring(g, dx, dy, 6.5f, 0.8f, Ui.c(0x40FFFFFF));
		if (!point.death) {
			hits.add(new Hit("color", dx - 8, dy - 8, 16, 16, point));
		}

		float tx = x + 28;
		float nameW = w - 28 - 52;
		if (editing == point) {
			nameField.x = tx - 2;
			nameField.y = y + 3;
			nameField.w = nameW;
			nameField.h = 14;
			nameField.render(g, mx, my);
		} else {
			String name = FontRenderer.SEMIBOLD.ellipsize(point.name, 9, nameW);
			FontRenderer.SEMIBOLD.drawMiddle(g, name, tx, y + 10, 9, Ui.c(ColorUtil.withAlpha(Theme.TEXT, dim)));
			hits.add(new Hit("rename", tx, y + 2, Math.min(nameW, FontRenderer.SEMIBOLD.width(name, 9) + 6), 16, point));
		}
		String where = point.x + " " + point.y + " " + point.z + " · " + dimensionName(point.dimension);
		if (minecraft.player != null && point.dimension.equals(minecraft.level.dimension().identifier().toString())) {
			where += " · " + WaypointsModule.formatDistance(minecraft.player.position().distanceTo(point.center()));
		}
		FontRenderer.MEDIUM.drawMiddle(g, FontRenderer.MEDIUM.ellipsize(where, 7, nameW), tx, y + 21.5f, 7, Ui.c(Theme.TEXT_DIM));

		// Show or hide, then delete.
		float ex = x + w - 36;
		float cy = y + ROW / 2f;
		boolean overEye = Ui.inside(mx, my, ex - 8, cy - 8, 16, 16);
		Ui.iconEye(g, ex, cy, 10, point.visible ? (overEye ? Theme.TEXT : Theme.TEXT_DIM) : Theme.TEXT_MUTED);
		if (!point.visible) {
			Render2D.line(g, ex - 5, cy + 5, ex + 5, cy - 5, 1.2f, Ui.c(Theme.TEXT_MUTED));
		}
		if (overEye) {
			g.setTooltipForNextFrame(Component.translatable(point.visible ? "aurora.waypoints.hide" : "aurora.waypoints.show"), mx, my);
		}
		hits.add(new Hit("visible", ex - 8, cy - 8, 16, 16, point));
		float rx = x + w - 15;
		boolean overDelete = Ui.inside(mx, my, rx - 8, cy - 8, 16, 16);
		if (overDelete) {
			Render2D.circle(g, rx, cy, 8, Ui.c(ColorUtil.fade(Theme.DANGER, 0.18f)));
			g.setTooltipForNextFrame(Component.translatable("aurora.waypoints.delete"), mx, my);
		}
		Render2D.cross(g, rx, cy, 6.5f, 1.3f, Ui.c(overDelete ? Theme.DANGER : ColorUtil.fade(Theme.TEXT_MUTED, Math.max(0.5f, hv))));
		hits.add(new Hit("delete", rx - 8, cy - 8, 16, 16, point));
	}

	private static String dimensionName(String id) {
		String key = "aurora.waypoints.dimension." + id.replace(':', '.');
		return Language.getInstance().has(key) ? I18n.get(key) : id.substring(id.indexOf(':') + 1);
	}

	private void button(GuiGraphicsExtractor g, int mx, int my, String id, String label, float x, float y, float w, float h, boolean primary) {
		boolean hov = Ui.inside(mx, my, x, y, w, h);
		float hv = hover(id, hov).get();
		if (primary) {
			Render2D.roundRect(g, x, y, w, h, 8, Ui.accentPaint(x, w, 0.75f + 0.25f * hv));
		} else {
			Render2D.roundRect(g, x, y, w, h, 8, Ui.c(ColorUtil.lerp(0xCC161922, 0xE61F2430, hv)));
			Render2D.roundOutline(g, x, y, w, h, 8, 0.7f, Ui.c(ColorUtil.lerp(0x24FFFFFF, 0x47FFFFFF, hv)));
		}
		String text = FontRenderer.SEMIBOLD.ellipsize(label, 8, w - 12);
		FontRenderer.SEMIBOLD.drawCentered(g, text, x + w / 2f, y + h / 2f, 8, Ui.c(primary ? Theme.onAccent() : Theme.TEXT));
		hits.add(new Hit(id, x, y, w, h, null));
	}

	private void closeButton(GuiGraphicsExtractor g, int mx, int my, float cx, float cy) {
		float hv = hover("x", Ui.inside(mx, my, cx - 9, cy - 9, 18, 18)).get();
		if (hv > 0.01f) {
			Render2D.circle(g, cx, cy, 9, Ui.c(ColorUtil.fade(0xFFFFFFFF, 0.08f * hv)));
		}
		Render2D.cross(g, cx, cy, 7, 1.3f, Ui.c(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv)));
		hits.add(new Hit("close", cx - 9, cy - 9, 18, 18, null));
	}

	// ======================================================================= actions

	private void startRename(WaypointsModule.Point point) {
		finishRename();
		editing = point;
		nameField.setValue(point.name);
		nameField.setFocused(true);
		minecraft.textInputManager().startTextInput(nameField);
	}

	private void finishRename() {
		if (editing != null) {
			String name = nameField.value().trim();
			if (!name.isEmpty()) {
				editing.name = name;
				waypoints.store();
			}
		}
		editing = null;
		nameField.setFocused(false);
		minecraft.textInputManager().stopTextInput(nameField);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (editing != null && nameField.mouseClicked(mx, my)) {
			return true;
		}
		finishRename();
		for (Hit hit : hits) {
			if (event.button() != 1 || !Ui.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
				continue;
			}
			WaypointsModule.Point point = hit.point();
			switch (hit.id()) {
				case "close" -> onClose();
				case "add" -> waypoints.addHere();
				case "rename" -> startRename(point);
				case "color" -> {
					int next = 0;
					for (int i = 0; i < WaypointsModule.COLORS.length; i++) {
						if (WaypointsModule.COLORS[i] == point.color) {
							next = (i + 1) % WaypointsModule.COLORS.length;
						}
					}
					point.color = WaypointsModule.COLORS[next];
					waypoints.store();
				}
				case "visible" -> {
					point.visible = !point.visible;
					waypoints.store();
				}
				case "delete" -> {
					waypoints.points().remove(point);
					waypoints.store();
				}
				default -> {
				}
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll -= (float) scrollY * 20;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (editing != null) {
			if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
				finishRename();
				return true;
			}
			if (event.isEscape()) {
				editing = null;
				finishRename();
				return true;
			}
			if (nameField.keyPressed(event)) {
				return true;
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return editing != null && nameField.charTyped(event);
	}

	@Override
	public void onClose() {
		finishRename();
		minecraft.gui.setScreen(parent);
	}
}
