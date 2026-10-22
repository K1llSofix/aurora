package dev.aurora.module.hud;

import dev.aurora.gui.HudDraw;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.Render2D;
import dev.aurora.module.HudModule;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PotionHud extends HudModule {
	private static final String[] ROMAN = {"", "", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	private final BooleanSetting bars = add(new BooleanSetting("bars", true));
	private final BooleanSetting blink = add(new BooleanSetting("blink", true));
	private final BooleanSetting hideVanilla = add(new BooleanSetting("hide_vanilla", true));

	private final List<MobEffectInstance> effects = new ArrayList<>();
	/** Longest duration seen per effect, so the bar shows how much of it is left. */
	private final Map<Holder<MobEffect>, Integer> maxDuration = new HashMap<>();

	public PotionHud() {
		super("potions", Items.DRAGON_BREATH, true, 1, 0, -4, 4);
	}

	public boolean hidesVanillaEffects() {
		return isEnabled() && hideVanilla.on();
	}

	private void collect(boolean editing) {
		effects.clear();
		if (mc.player != null) {
			for (MobEffectInstance e : mc.player.getActiveEffects()) {
				if (e.showIcon()) {
					effects.add(e);
				}
			}
		}
		if (effects.isEmpty() && editing) {
			effects.add(new MobEffectInstance(MobEffects.SPEED, 20 * 95, 1));
			effects.add(new MobEffectInstance(MobEffects.STRENGTH, 20 * 7, 0));
			effects.add(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 240, 0));
		}
		effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
		for (MobEffectInstance e : effects) {
			maxDuration.merge(e.getEffect(), e.getDuration(), Math::max);
		}
		maxDuration.keySet().removeIf(h -> effects.stream().noneMatch(e -> e.getEffect().equals(h)));
	}

	@Override
	public boolean hasContent() {
		collect(false);
		return !effects.isEmpty();
	}

	private static String name(MobEffectInstance e) {
		String name = e.getEffect().value().getDisplayName().getString();
		int amp = e.getAmplifier() + 1;
		if (amp >= 2) {
			name += " " + (amp < ROMAN.length ? ROMAN[amp] : Integer.toString(amp));
		}
		return name;
	}

	private static String duration(MobEffectInstance e) {
		if (e.isInfiniteDuration()) {
			return "∞";
		}
		int seconds = e.getDuration() / 20;
		return seconds >= 3600
			? String.format("%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
			: String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	private float rowHeight() {
		return HudDraw.minimal() ? 22f : 27f;
	}

	@Override
	public void layout(boolean editing) {
		collect(editing);
		float textW = 0;
		for (MobEffectInstance e : effects) {
			textW = Math.max(textW, Math.max(HudDraw.width(name(e)), HudDraw.width(duration(e))));
		}
		width = 18 + 6 + textW + HudDraw.padX() * 2 + 2;
		height = Math.max(1, effects.size()) * rowHeight() + (effects.size() > 1 ? 0 : 0);
	}

	@Override
	public void render(GuiGraphicsExtractor g, float partialTick, boolean editing) {
		boolean right = anchoredRight();
		float pad = HudDraw.padX();
		float rh = rowHeight();
		HudDraw.box(g, 0, 0, width, height);
		float y = 0;
		for (MobEffectInstance e : effects) {
			float alpha = 1f;
			if (blink.on() && !e.isInfiniteDuration() && e.getDuration() < 200) {
				double t = System.currentTimeMillis() / 1000.0;
				alpha = 0.45f + 0.55f * (float) (0.5 + 0.5 * Math.cos(t * Math.PI * 2.4));
			}
			float iconX = right ? width - pad - 18 : pad;
			float iconY = y + (rh - 18) / 2f - (bars.on() && !HudDraw.minimal() ? 1.5f : 0);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(e.getEffect()),
				Math.round(iconX), Math.round(iconY), 18, 18, ColorUtil.withAlpha(0xFFFFFF, Math.round(255 * alpha)));

			String name = name(e);
			String time = duration(e);
			int nameColor = ColorUtil.fade(HudDraw.textColor(), alpha);
			int timeColor = ColorUtil.fade(e.getDuration() < 200 && !e.isInfiniteDuration() ? Theme.DANGER : 0xFFA3ABBB, alpha);
			float textX = right ? iconX - 5 : iconX + 18 + 5;
			float nameY = y + rh * 0.3f;
			float timeY = y + rh * 0.6f;
			if (right) {
				HudDraw.text(g, name, textX - HudDraw.width(name), nameY, nameColor);
				HudDraw.text(g, time, textX - HudDraw.width(time), timeY, timeColor);
			} else {
				HudDraw.text(g, name, textX, nameY, nameColor);
				HudDraw.text(g, time, textX, timeY, timeColor);
			}

			if (bars.on() && !HudDraw.minimal() && !e.isInfiniteDuration()) {
				int max = Math.max(1, maxDuration.getOrDefault(e.getEffect(), e.getDuration()));
				float fraction = Math.min(1f, e.getDuration() / (float) max);
				float bx = pad + 1;
				float bw = width - pad * 2 - 2;
				float by = y + rh - 4f;
				Render2D.roundRect(g, bx, by, bw, 1.5f, 0.75f, 0x22FFFFFF);
				int effectColor = 0xFF000000 | e.getEffect().value().getColor();
				float fw = bw * fraction;
				float fx = right ? bx + bw - fw : bx;
				Render2D.roundRect(g, fx, by, fw, 1.5f, 0.75f, ColorUtil.fade(ColorUtil.shade(effectColor, 0.25f), alpha));
			}
			y += rh;
		}
	}
}
