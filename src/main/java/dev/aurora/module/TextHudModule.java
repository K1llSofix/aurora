package dev.aurora.module;

import dev.aurora.gui.HudDraw;
import dev.aurora.setting.BooleanSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;

/** A one line "LABEL value" element, the bread and butter of a HUD. */
public abstract class TextHudModule extends HudModule {
	protected final BooleanSetting showLabel = add(new BooleanSetting("show_label", true));
	private String cachedLabel = "";
	private String cachedValue = "";

	protected TextHudModule(String id, Item icon, boolean enabledByDefault, float anchorX, float anchorY, float offsetX, float offsetY) {
		super(id, icon, enabledByDefault, anchorX, anchorY, offsetX, offsetY);
	}

	/** Short upper-case label such as "FPS"; may be empty. */
	protected abstract String label();

	protected abstract String value(boolean editing);

	/** Classic style puts the unit after the value ("144 FPS") like most PvP clients. */
	protected boolean labelAfterValueInClassic() {
		return true;
	}

	@Override
	public void layout(boolean editing) {
		cachedLabel = showLabel.on() ? label() : "";
		cachedValue = value(editing);
		float w = HudDraw.width(text(cachedLabel, cachedValue));
		if (HudDraw.modern() && !cachedLabel.isEmpty()) {
			w = HudDraw.width(cachedLabel) + 4 + HudDraw.width(cachedValue);
		}
		width = w + HudDraw.contentInset() + HudDraw.padX();
		height = HudDraw.rowHeight();
	}

	private String text(String label, String value) {
		if (label.isEmpty()) {
			return value;
		}
		if (HudDraw.modern()) {
			return label + " " + value;
		}
		return labelAfterValueInClassic() ? "[" + value + " " + label + "]" : "[" + label + ": " + value + "]";
	}

	/** Colour of the value; elements such as FPS colour it by how good it is. */
	protected int valueColor() {
		return HudDraw.textColor();
	}

	@Override
	public void render(GuiGraphicsExtractor g, float partialTick, boolean editing) {
		HudDraw.panel(g, 0, 0, width, height);
		// The text stays in the first row when an element adds more below it.
		float cy = Math.min(height, HudDraw.rowHeight()) / 2f;
		float x = HudDraw.contentInset();
		if (HudDraw.modern() && !cachedLabel.isEmpty()) {
			x += HudDraw.text(g, cachedLabel, x, cy, HudDraw.labelColor()) + 4;
			HudDraw.text(g, cachedValue, x, cy, valueColor());
		} else {
			HudDraw.text(g, text(cachedLabel, cachedValue), x, cy, valueColor());
		}
	}
}
