package dev.aurora.module.hud;

import dev.aurora.module.TextHudModule;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.util.CpsTracker;
import net.minecraft.world.item.Items;

public final class CpsHud extends TextHudModule {
	private final BooleanSetting showRight = add(new BooleanSetting("show_right", true));

	public CpsHud() {
		super("cps", Items.FLINT_AND_STEEL, true, 0, 0, 4, 22);
	}

	@Override
	protected String label() {
		return "CPS";
	}

	@Override
	protected String value(boolean editing) {
		return showRight.on() ? CpsTracker.left() + " | " + CpsTracker.right() : Integer.toString(CpsTracker.left());
	}
}
