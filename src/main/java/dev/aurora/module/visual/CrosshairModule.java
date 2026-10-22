package dev.aurora.module.visual;

import dev.aurora.gui.Theme;
import dev.aurora.gui.render.Render2D;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** A fully configurable crosshair drawn with anti-aliased vector shapes. */
public final class CrosshairModule extends Module {
	private final ModeSetting style = add(new ModeSetting("style", "cross_dot",
		"cross", "cross_dot", "dot", "circle", "tee", "chevron"));
	private final ColorSetting color = add(new ColorSetting("color", 0xFFFFFFFF, true));
	private final NumberSetting size = add(new NumberSetting("size", 4.5, 1, 12, 0.5).suffix("px"));
	private final NumberSetting gap = add(new NumberSetting("gap", 2, 0, 8, 0.5).suffix("px"));
	private final NumberSetting thickness = add(new NumberSetting("thickness", 1, 0.5, 3, 0.25).suffix("px"));
	private final BooleanSetting outline = add(new BooleanSetting("outline", true));
	private final BooleanSetting dynamic = add(new BooleanSetting("dynamic", true));
	private final BooleanSetting targetColor = add(new BooleanSetting("target_color", true));
	private final BooleanSetting cooldown = add(new BooleanSetting("cooldown", true));

	private final Animation spread = new Animation(0, 12);
	private final Animation onTarget = new Animation(0, 14);
	private final Animation cooldownAlpha = new Animation(0, 10);

	public CrosshairModule() {
		super("crosshair", Category.VISUAL, Items.CROSSBOW, true);
	}

	/** Whether we draw instead of vanilla this frame. */
	public boolean replaces() {
		return isEnabled() && mc.player != null && mc.options.getCameraType().isFirstPerson()
			&& mc.gameMode != null && mc.gameMode.getPlayerMode() != GameType.SPECTATOR
			&& !mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR);
	}

	public void render(GuiGraphicsExtractor g) {
		g.nextStratum();
		float px = Render2D.pixel(g);
		// Centre on the physical pixel grid so 1px lines stay sharp.
		float cx = Math.round(g.guiWidth() / 2f / px) * px;
		float cy = Math.round(g.guiHeight() / 2f / px) * px;

		float targetSpread = 0;
		if (dynamic.on() && mc.player != null) {
			boolean moving = mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.0025;
			targetSpread = spread(mc.player.isSprinting(), moving, !mc.player.onGround());
		}
		float s = spread.target(targetSpread).get();
		boolean target = targetColor.on() && mc.crosshairPickEntity instanceof LivingEntity living && living.isAlive();
		float t = onTarget.target(target ? 1 : 0).get();
		float strength = 1f;
		float a = 0f;
		if (cooldown.on() && mc.player != null) {
			strength = mc.player.getAttackStrengthScale(0f);
			a = cooldownAlpha.target(strength < 1f ? 1 : 0).get();
		}
		paint(g, cx, cy, px * 2, s, t, strength, a);
	}

	/** How far the lines move apart, in setting pixels, for the player's movement. */
	public static float spread(boolean sprinting, boolean moving, boolean airborne) {
		return (sprinting ? 2.5f : moving ? 1.2f : 0f) + (airborne ? 1.5f : 0f);
	}

	public boolean dynamicSpread() {
		return dynamic.on();
	}

	public boolean highlightsTargets() {
		return targetColor.on();
	}

	public boolean showsCooldown() {
		return cooldown.on();
	}

	/**
	 * Draws the crosshair centred on (cx, cy). {@code unit} is the size of one setting pixel, so the
	 * menu preview can draw it magnified; the other values are the animated movement spread, how
	 * much the "on target" colour shows, and the attack cooldown (strength 0..1, ring opacity).
	 */
	public void paint(GuiGraphicsExtractor g, float cx, float cy, float unit, float spread, float onTarget,
					  float cooldownStrength, float cooldownAlpha) {
		int main = ColorUtil.lerp(color.color(), ColorUtil.withAlpha(Theme.DANGER, ColorUtil.alpha(color.color())), onTarget);
		int border = ColorUtil.withAlpha(0x000000, Math.round(ColorUtil.alpha(main) * 0.75f));

		float th = thickness.floatValue() * unit;
		float len = size.floatValue() * unit;
		float gp = (gap.floatValue() + spread) * unit;
		if (outline.on()) {
			draw(g, cx, cy, len, gp, th, unit, border);
		}
		draw(g, cx, cy, len, gp, th, 0, main);

		if (cooldownAlpha > 0.01f) {
			float radius = gp + len + 4 * unit;
			Render2D.arc(g, cx, cy, radius, 1.5f * unit, 0, 360, ColorUtil.withAlpha(0x000000, Math.round(70 * cooldownAlpha)));
			Render2D.arc(g, cx, cy, radius, 1.5f * unit, -90, 360 * cooldownStrength,
				ColorUtil.fade(ColorUtil.lerp(Theme.accent(), Theme.accent2(), cooldownStrength), cooldownAlpha));
		}
	}

	/** Draws the chosen style; {@code grow} thickens every stroke for the outline pass. */
	private void draw(GuiGraphicsExtractor g, float cx, float cy, float len, float gap, float th, float grow, int c) {
		float half = th / 2f + grow / 2f;
		switch (style.get()) {
			case "dot" -> Render2D.circle(g, cx, cy, Math.max(th, len * 0.35f) + grow / 2f, c);
			case "circle" -> {
				Render2D.ring(g, cx, cy, gap + len * 0.6f, th + grow, c);
				Render2D.circle(g, cx, cy, th * 0.9f + grow / 2f, c);
			}
			case "chevron" -> {
				float w = len + gap * 0.5f;
				Render2D.polyline(g, th + grow, c, cx - w, cy + gap + w * 0.8f, cx, cy + gap, cx + w, cy + gap + w * 0.8f);
			}
			default -> {
				boolean tee = style.is("tee");
				// right, left, bottom, top
				Render2D.rect(g, cx + gap - grow / 2f, cy - half, len + grow, half * 2, c);
				Render2D.rect(g, cx - gap - len - grow / 2f, cy - half, len + grow, half * 2, c);
				Render2D.rect(g, cx - half, cy + gap - grow / 2f, half * 2, len + grow, c);
				if (!tee) {
					Render2D.rect(g, cx - half, cy - gap - len - grow / 2f, half * 2, len + grow, c);
				}
				if (style.is("cross_dot")) {
					Render2D.rect(g, cx - half, cy - half, half * 2, half * 2, c);
				}
			}
		}
	}
}
