package dev.aurora.module.hud;

import dev.aurora.gui.HudDraw;
import dev.aurora.gui.Theme;
import dev.aurora.module.TextHudModule;
import dev.aurora.util.ServerTps;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** The server's tick rate: next to the ping it tells whether the server or your connection lags. */
public final class TpsHud extends TextHudModule {
	public TpsHud() {
		super("tps", Items.REPEATER, false, 0, 0, 4, 124);
	}

	@Override
	protected String label() {
		return "TPS";
	}

	@Override
	protected String value(boolean editing) {
		if (mc.level == null) {
			return editing ? "20.0" : "-";
		}
		float tps = ServerTps.tps();
		return tps < 0 ? "…" : String.format(Locale.ROOT, "%.1f", tps);
	}

	@Override
	protected int valueColor() {
		float tps = mc.level == null ? -1 : ServerTps.tps();
		if (tps < 0) {
			return HudDraw.textColor();
		}
		float ratio = tps / ServerTps.nominal();
		return ratio >= 0.95f ? Theme.SUCCESS : ratio >= 0.75f ? Theme.WARNING : Theme.DANGER;
	}
}
