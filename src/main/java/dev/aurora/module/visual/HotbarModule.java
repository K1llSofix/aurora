package dev.aurora.module.visual;

import dev.aurora.gui.Theme;
import dev.aurora.gui.render.Render2D;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Items;

/**
 * The hotbar selection glides between slots instead of jumping, and the "glass" style redraws the
 * bar with Aurora's rounded panels and an accent highlight.
 */
public final class HotbarModule extends Module {
	private Animation slot = new Animation(0, 18);
	private boolean first = true;

	private final ModeSetting style = add(new ModeSetting("style", "glass", "glass", "vanilla"));
	private final NumberSetting speed = add(new NumberSetting("speed", 100, 30, 200, 10).suffix("%"))
		.onChange(v -> slot = new Animation(slot.peek(), 18f * v.floatValue() / 100f));

	public HotbarModule() {
		super("hotbar", Category.VISUAL, Items.BUNDLE, true);
	}

	public boolean glass() {
		return isEnabled() && style.is("glass");
	}

	/** Animated selection position in slots (fractional while moving). */
	public float selection(int selected) {
		if (first || !isEnabled()) {
			slot.snap(selected);
			first = false;
			return selected;
		}
		// Wrapping from slot 8 to 0 (or back) jumps instead of sweeping across the bar.
		if (Math.abs(slot.peek() - selected) > 4.5f) {
			slot.snap(selected);
		}
		return slot.target(selected).get();
	}

	public void drawBackground(GuiGraphicsExtractor g, float x, float y, float w, float h) {
		Render2D.shadow(g, x, y, w, h, 6, 8, 0x55000000);
		Render2D.roundRect(g, x, y, w, h, 6, Render2D.Paint.vertical(y, h, 0xB0141720, 0xC00B0D12));
		Render2D.roundOutline(g, x, y, w, h, 6, 0.7f, 0x26FFFFFF);
		// Faint slot separators.
		for (int i = 1; i < 9; i++) {
			Render2D.rect(g, x + 1 + i * 20 - 0.25f, y + 5, 0.5f, h - 10, 0x14FFFFFF);
		}
	}

	public void drawSelection(GuiGraphicsExtractor g, float x, float y) {
		float w = 22;
		float h = 22;
		Render2D.glow(g, x, y, w, h, 6, 6, ColorUtil.fade(Theme.accent(0.5f), 0.35f));
		Render2D.roundRect(g, x, y, w, h, 6, Render2D.Paint.diagonal(x, y, w, h,
			ColorUtil.fade(Theme.accent(), 0.30f), ColorUtil.fade(Theme.accent2(), 0.30f)));
		Render2D.roundOutline(g, x, y, w, h, 6, 1.2f, Render2D.Paint.diagonal(x, y, w, h, Theme.accent(), Theme.accent2()));
	}

	public void drawOffhand(GuiGraphicsExtractor g, float x, float y) {
		Render2D.shadow(g, x, y, 22, 22, 6, 8, 0x55000000);
		Render2D.roundRect(g, x, y, 22, 22, 6, Render2D.Paint.vertical(y, 22, 0xB0141720, 0xC00B0D12));
		Render2D.roundOutline(g, x, y, 22, 22, 6, 0.7f, 0x26FFFFFF);
	}
}
