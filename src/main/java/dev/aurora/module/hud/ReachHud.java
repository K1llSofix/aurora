package dev.aurora.module.hud;

import dev.aurora.module.TextHudModule;
import dev.aurora.util.CombatTracker;
import net.minecraft.world.item.Items;

import java.util.Locale;

public final class ReachHud extends TextHudModule {
	public ReachHud() {
		super("reach", Items.IRON_SWORD, false, 0, 0, 4, 144);
	}

	@Override
	protected String label() {
		return "REACH";
	}

	@Override
	protected String value(boolean editing) {
		double reach = CombatTracker.lastReach();
		if (reach < 0 || CombatTracker.millisSinceReach() > 3000) {
			return editing ? "2.94 m" : "-";
		}
		return String.format(Locale.ROOT, "%.2f m", reach);
	}
}
