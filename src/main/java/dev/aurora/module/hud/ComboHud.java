package dev.aurora.module.hud;

import dev.aurora.module.TextHudModule;
import dev.aurora.util.CombatTracker;
import net.minecraft.world.item.Items;

public final class ComboHud extends TextHudModule {
	public ComboHud() {
		super("combo", Items.GOLDEN_SWORD, false, 0, 0, 4, 162);
	}

	@Override
	protected String label() {
		return "COMBO";
	}

	@Override
	protected String value(boolean editing) {
		int combo = CombatTracker.combo();
		return editing && combo == 0 ? "3" : Integer.toString(combo);
	}
}
