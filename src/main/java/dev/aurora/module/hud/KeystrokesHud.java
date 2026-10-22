package dev.aurora.module.hud;

import dev.aurora.gui.HudDraw;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.module.HudModule;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.CpsTracker;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Items;

public final class KeystrokesHud extends HudModule {
	private final BooleanSetting mouse = add(new BooleanSetting("mouse", true));
	private final BooleanSetting cps = add(new BooleanSetting("cps", true)).visibleWhen(mouse::on);
	private final BooleanSetting space = add(new BooleanSetting("space", true));
	private final NumberSetting size = add(new NumberSetting("key_size", 22, 16, 32, 1).suffix("px"));
	private final NumberSetting gap = add(new NumberSetting("gap", 2, 0, 6, 1).suffix("px"));

	private final Animation[] anims = {
		new Animation(0, 22), new Animation(0, 22), new Animation(0, 22), new Animation(0, 22),
		new Animation(0, 22), new Animation(0, 22), new Animation(0, 22)
	};

	public KeystrokesHud() {
		super("keystrokes", Items.TRIPWIRE_HOOK, true, 1, 1, -4, -4);
	}

	@Override
	public void layout(boolean editing) {
		float k = size.floatValue();
		float gp = gap.floatValue();
		width = k * 3 + gp * 2;
		float h = k * 2 + gp;
		if (mouse.on()) {
			h += gp + k;
		}
		if (space.on()) {
			h += gp + k * 0.55f;
		}
		height = h;
	}

	@Override
	public void render(GuiGraphicsExtractor g, float partialTick, boolean editing) {
		float k = size.floatValue();
		float gp = gap.floatValue();
		var o = mc.options;
		key(g, 0, o.keyUp, k + gp, 0, k, k);
		key(g, 1, o.keyLeft, 0, k + gp, k, k);
		key(g, 2, o.keyDown, k + gp, k + gp, k, k);
		key(g, 3, o.keyRight, (k + gp) * 2, k + gp, k, k);
		float y = (k + gp) * 2;
		if (mouse.on()) {
			float half = (width - gp) / 2f;
			mouseKey(g, 4, o.keyAttack, "LMB", CpsTracker.left(), 0, y, half, k);
			mouseKey(g, 5, o.keyUse, "RMB", CpsTracker.right(), half + gp, y, half, k);
			y += k + gp;
		}
		if (space.on()) {
			float h = k * 0.55f;
			float p = anims[6].target(o.keyJump.isDown()).get();
			keyBackground(g, 0, y, width, h, p);
			float barW = width * 0.38f;
			int color = ColorUtil.lerp(HudDraw.textColor(), pressedText(), p);
			Render2D.roundRect(g, (width - barW) / 2f, y + h / 2f - 0.75f, barW, 1.5f, 0.75f, color);
		}
	}

	private void key(GuiGraphicsExtractor g, int index, KeyMapping mapping, float x, float y, float w, float h) {
		float p = anims[index].target(mapping.isDown()).get();
		keyBackground(g, x, y, w, h, p);
		String label = keyLabel(mapping);
		int color = ColorUtil.lerp(HudDraw.textColor(), pressedText(), p);
		HudDraw.text(g, label, x + (w - HudDraw.width(label)) / 2f, y + h / 2f, color);
	}

	private void mouseKey(GuiGraphicsExtractor g, int index, KeyMapping mapping, String label, int clicks,
						  float x, float y, float w, float h) {
		float p = anims[index].target(mapping.isDown()).get();
		keyBackground(g, x, y, w, h, p);
		int color = ColorUtil.lerp(HudDraw.textColor(), pressedText(), p);
		if (cps.on()) {
			HudDraw.text(g, label, x + (w - HudDraw.width(label)) / 2f, y + h * 0.36f, color);
			String c = clicks + " CPS";
			float small = 6.5f;
			int dim = ColorUtil.fade(color, 0.7f);
			FontRenderer.MEDIUM.drawCentered(g, c, x + w / 2f, y + h * 0.72f, small, dim);
		} else {
			HudDraw.text(g, label, x + (w - HudDraw.width(label)) / 2f, y + h / 2f, color);
		}
	}

	/**
	 * Letter keys are named by physical position (scancode), so W A S D read the same on every
	 * keyboard layout instead of showing e.g. Cyrillic letters.
	 */
	private static String keyLabel(KeyMapping mapping) {
		var key = KeyMappingHelper.getBoundKeyOf(mapping);
		int code = key.getValue();
		if (key.getType() == InputConstants.Type.KEYBOARD) {
			if (code >= InputConstants.KEY_A && code <= InputConstants.KEY_Z) {
				return String.valueOf((char) ('A' + code - InputConstants.KEY_A));
			}
			if (code >= InputConstants.KEY_1 && code <= InputConstants.KEY_9) {
				return String.valueOf((char) ('1' + code - InputConstants.KEY_1));
			}
		}
		String label = mapping.getTranslatedKeyMessage().getString();
		return label.length() > 3 ? label.substring(0, 3) : label;
	}

	private int pressedText() {
		return HudDraw.classic() ? 0xFF111111 : Theme.onAccent();
	}

	private void keyBackground(GuiGraphicsExtractor g, float x, float y, float w, float h, float pressed) {
		if (HudDraw.minimal()) {
			if (pressed > 0.01f) {
				Render2D.roundRect(g, x, y, w, h, 3f, ColorUtil.fade(Theme.accent(), pressed * 0.35f));
			}
			return;
		}
		if (HudDraw.classic()) {
			Render2D.rect(g, x, y, w, h, ColorUtil.lerp(0x66000000, 0xCCFFFFFF, pressed));
			return;
		}
		HudDraw.box(g, x, y, w, h);
		if (pressed > 0.01f) {
			int a = ColorUtil.fade(Theme.accent(), pressed);
			int b = ColorUtil.fade(Theme.accent2(), pressed);
			Render2D.roundRect(g, x, y, w, h, 4f, Render2D.Paint.diagonal(x, y, w, h, a, b));
		}
	}
}
