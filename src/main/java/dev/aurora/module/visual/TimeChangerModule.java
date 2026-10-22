package dev.aurora.module.visual;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** Overrides the time of day the client renders. Purely visual: the server is not affected. */
public final class TimeChangerModule extends Module {
	private final NumberSetting time = add(new NumberSetting("time", 12, 0, 23.75, 0.25)
		.format(h -> String.format(Locale.ROOT, "%02d:%02d", (int) Math.floor(h), (int) Math.round((h - Math.floor(h)) * 60))));
	private final BooleanSetting cycle = add(new BooleanSetting("cycle", false));

	public TimeChangerModule() {
		super("time_changer", Category.VISUAL, Items.SUNFLOWER, false);
	}

	/** Ticks into the day (0 = 06:00), with a fraction for smooth motion. */
	public double dayTicks() {
		if (cycle.on()) {
			// A whole day in two minutes.
			return (System.currentTimeMillis() % 120_000L) / 120_000.0 * 24000.0;
		}
		double hours = time.get();
		return ((hours - 6.0 + 24.0) % 24.0) * 1000.0;
	}

	/** Keeps the day count (moon phase) of the real clock and replaces the time within the day. */
	public long totalTicks(long real) {
		long day = Math.floorDiv(real, 24000L);
		return day * 24000L + (long) dayTicks();
	}

	public float partialTick() {
		double t = dayTicks();
		return (float) (t - Math.floor(t));
	}
}
