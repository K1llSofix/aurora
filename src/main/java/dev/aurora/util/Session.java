package dev.aurora.util;

import net.minecraft.client.Minecraft;

/** How long the player has been in the current world or server. */
public final class Session {
	private static long joinedAt;

	private Session() {
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			joinedAt = 0;
		} else if (joinedAt == 0) {
			joinedAt = System.currentTimeMillis();
		}
	}

	/** When the player entered the current world or server (epoch millis), 0 outside one. */
	public static long joinedAt() {
		return joinedAt;
	}

	public static long millis() {
		return joinedAt == 0 ? 0 : System.currentTimeMillis() - joinedAt;
	}

	/** 5:07 or 1:05:07. */
	public static String format(long millis) {
		long s = millis / 1000;
		return s >= 3600
			? String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
			: String.format("%d:%02d", s / 60, s % 60);
	}
}
