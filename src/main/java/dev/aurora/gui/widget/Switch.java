package dev.aurora.gui.widget;

import dev.aurora.gui.Theme;
import dev.aurora.gui.render.Render2D;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** iOS-style toggle with a sliding knob and an accent gradient track. */
public final class Switch {
	public static final float W = 22;
	public static final float H = 12;

	private final Animation anim;
	private final Animation hover = new Animation(0, 18);

	public Switch(boolean initial) {
		anim = new Animation(initial ? 1 : 0, 15);
	}

	public void render(GuiGraphicsExtractor g, float x, float y, boolean on, boolean hovered) {
		float t = anim.target(on).get();
		float h = hover.target(hovered).get();
		int offTrack = ColorUtil.lerp(0x2BFFFFFF, 0x3DFFFFFF, h);
		if (t > 0.02f) {
			Render2D.glow(g, x, y, W, H, H / 2f, 5, Ui.c(ColorUtil.fade(Theme.accent(0.5f), 0.28f * t)));
		}
		if (t < 0.98f) {
			Render2D.roundRect(g, x, y, W, H, H / 2f, Ui.c(offTrack));
		}
		if (t > 0.02f) {
			Render2D.roundRect(g, x, y, W, H, H / 2f, Render2D.Paint.horizontal(x, W,
				Ui.c(ColorUtil.fade(Theme.accent(), t)), Ui.c(ColorUtil.fade(Theme.accent2(), t))));
		}
		float r = H / 2f - 1.6f;
		float cx = x + H / 2f + t * (W - H);
		float cy = y + H / 2f;
		Render2D.circle(g, cx, cy + 0.6f, r + 0.6f, Ui.c(0x50000000));
		Render2D.circle(g, cx, cy, r + h * 0.3f, Ui.c(0xFFFFFFFF));
	}

	public void snap(boolean on) {
		anim.snap(on ? 1 : 0);
	}
}
