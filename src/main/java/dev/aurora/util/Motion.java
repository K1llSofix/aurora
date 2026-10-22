package dev.aurora.util;

/** Global animation speed knob, driven by the client settings ("Animations: fast / normal / off"). */
public final class Motion {
	private static float multiplier = 1f;

	private Motion() {
	}

	public static float speedMultiplier() {
		return multiplier;
	}

	/** 0 disables animations entirely (everything snaps); 1 is the default. */
	public static void setSpeedMultiplier(float m) {
		multiplier = m <= 0 ? 1000f : m;
	}

	/** Progress of a timed animation: elapsed / duration, scaled by the global multiplier, clamped to [0, 1]. */
	public static float progress(long startMillis, long durationMillis) {
		long elapsed = System.currentTimeMillis() - startMillis;
		float p = elapsed * multiplier / (float) durationMillis;
		return p < 0 ? 0 : (p > 1 ? 1 : p);
	}
}
