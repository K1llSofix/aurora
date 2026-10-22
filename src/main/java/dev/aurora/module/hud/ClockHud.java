package dev.aurora.module.hud;

import dev.aurora.module.TextHudModule;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.ModeSetting;
import net.minecraft.world.item.Items;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class ClockHud extends TextHudModule {
	private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter H24_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a");
	private static final DateTimeFormatter H12_SECONDS = DateTimeFormatter.ofPattern("h:mm:ss a");

	private final ModeSetting format = add(new ModeSetting("format", "24h", "24h", "12h"));
	private final BooleanSetting seconds = add(new BooleanSetting("seconds", false));
	private final long started = System.currentTimeMillis();
	private final BooleanSetting session = add(new BooleanSetting("session", false));

	public ClockHud() {
		super("clock", Items.CLOCK, false, 0, 0, 4, 180);
	}

	@Override
	protected String label() {
		return session.on() ? "SESSION" : "TIME";
	}

	@Override
	protected String value(boolean editing) {
		if (session.on()) {
			long s = (System.currentTimeMillis() - started) / 1000;
			return s >= 3600
				? String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
				: String.format("%d:%02d", s / 60, s % 60);
		}
		boolean h12 = format.is("12h");
		DateTimeFormatter f = h12 ? (seconds.on() ? H12_SECONDS : H12) : (seconds.on() ? H24_SECONDS : H24);
		return LocalTime.now().format(f);
	}
}
