package dev.aurora.module.hud;

import dev.aurora.gui.HudDraw;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.module.HudModule;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

/** A horizontal heading tape, like the ones in flight HUDs and battle royale games. */
public final class CompassHud extends HudModule {
	private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	private final NumberSetting tapeWidth = add(new NumberSetting("width", 160, 100, 260, 10).suffix("px"));
	private final NumberSetting fov = add(new NumberSetting("range", 150, 90, 240, 10).suffix("°"));
	private final BooleanSetting degrees = add(new BooleanSetting("degrees", true));

	public CompassHud() {
		super("compass", Items.RECOVERY_COMPASS, false, 0.5f, 0, 0, 4);
	}

	@Override
	public void layout(boolean editing) {
		width = tapeWidth.floatValue();
		height = degrees.on() ? 26 : 16;
	}

	@Override
	public void render(GuiGraphicsExtractor g, float partialTick, boolean editing) {
		float yaw = mc.player == null ? 0 : mc.player.getViewYRot(partialTick);
		float heading = Mth.positiveModulo(yaw + 180f, 360f);
		float tapeH = 16;
		HudDraw.box(g, 0, 0, width, tapeH);

		float range = fov.floatValue();
		float pxPerDeg = width / range;
		float center = width / 2f;
		g.enableScissor(0, 0, Math.round(width), Math.round(tapeH));
		int first = (int) Math.floor((heading - range / 2f) / 15f) * 15;
		for (int deg = first; deg <= heading + range / 2f + 15; deg += 15) {
			float x = center + (deg - heading) * pxPerDeg;
			float edge = 1f - Math.abs(x - center) / center;
			float fade = Mth.clamp(edge * 1.6f, 0f, 1f);
			if (fade <= 0) {
				continue;
			}
			int norm = Math.floorMod(deg, 360);
			if (norm % 45 == 0) {
				String label = POINTS[norm / 45];
				boolean cardinal = norm % 90 == 0;
				int color = norm == 0 ? Theme.accent() : (cardinal ? HudDraw.textColor() : 0xFFA3ABBB);
				float size = cardinal ? 8.5f : 7f;
				FontRenderer.SEMIBOLD.drawCentered(g, label, x, tapeH / 2f, size, ColorUtil.fade(color, fade));
			} else {
				Render2D.rect(g, x - 0.5f, tapeH / 2f - 2.5f, 1f, 5f, ColorUtil.fade(0x66FFFFFF, fade));
			}
		}
		g.disableScissor();

		// Centre marker.
		Render2D.triangle(g, center - 3, 0, center + 3, 0, center, 3.5f, Theme.accent());
		if (degrees.on()) {
			String text = Math.round(heading) % 360 + "°";
			float tw = FontRenderer.SEMIBOLD.width(text, 7.5f) + 8;
			float by = tapeH + 2;
			Render2D.roundRect(g, center - tw / 2f, by, tw, 8, 3f,
				Render2D.Paint.horizontal(center - tw / 2f, tw, ColorUtil.fade(Theme.accent(), 0.9f), ColorUtil.fade(Theme.accent2(), 0.9f)));
			FontRenderer.SEMIBOLD.drawCentered(g, text, center, by + 4, 7f, Theme.onAccent());
		}
	}
}
