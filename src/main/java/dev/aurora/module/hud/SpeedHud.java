package dev.aurora.module.hud;

import dev.aurora.module.TextHudModule;
import net.minecraft.world.item.Items;

import java.util.Locale;

public final class SpeedHud extends TextHudModule {
	private double smoothed;

	public SpeedHud() {
		super("speed", Items.SUGAR, false, 0, 0, 4, 126);
	}

	@Override
	public void tick() {
		if (mc.player == null) {
			smoothed = 0;
			return;
		}
		double dx = mc.player.getX() - mc.player.xo;
		double dz = mc.player.getZ() - mc.player.zo;
		double perSecond = Math.sqrt(dx * dx + dz * dz) * 20.0;
		smoothed += (perSecond - smoothed) * 0.35;
	}

	@Override
	protected String label() {
		return "SPEED";
	}

	@Override
	protected String value(boolean editing) {
		return String.format(Locale.ROOT, "%.2f m/s", editing && mc.player == null ? 5.61 : smoothed);
	}
}
