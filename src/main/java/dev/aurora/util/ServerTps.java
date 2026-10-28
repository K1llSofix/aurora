package dev.aurora.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

import java.util.ArrayDeque;

/**
 * Estimates how many ticks a second the server manages. A server sends its game time every 20
 * ticks; dividing the ticks that passed by the real time between those updates over the last few
 * seconds gives its tick rate. In singleplayer the built-in server is asked directly.
 */
public final class ServerTps {
	private static final long WINDOW_NANOS = 10_000_000_000L;
	/** Without an update for this long the server is frozen or gone: no number is better than an old one. */
	private static final long STALE_NANOS = 6_000_000_000L;

	private record Sample(long nanos, long gameTime) {
	}

	private static final ArrayDeque<Sample> SAMPLES = new ArrayDeque<>();

	private ServerTps() {
	}

	/** A time update arrived (client thread). */
	public static void onTimeUpdate(long gameTime) {
		long now = System.nanoTime();
		Sample last = SAMPLES.peekLast();
		// Time going back means a new world: start over.
		if (last != null && gameTime < last.gameTime) {
			SAMPLES.clear();
		}
		SAMPLES.addLast(new Sample(now, gameTime));
		while (SAMPLES.size() > 2 && now - SAMPLES.peekFirst().nanos > WINDOW_NANOS) {
			SAMPLES.removeFirst();
		}
	}

	public static void reset() {
		SAMPLES.clear();
	}

	/** Ticks per second, or -1 while there is not enough to tell. */
	public static float tps() {
		Minecraft mc = Minecraft.getInstance();
		float nominal = nominal();
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) {
			float mspt = server.getCurrentSmoothedTickTime();
			return mspt <= 0 ? nominal : Math.min(nominal, 1000f / mspt);
		}
		Sample first = SAMPLES.peekFirst();
		Sample last = SAMPLES.peekLast();
		if (first == null || first == last || System.nanoTime() - last.nanos > STALE_NANOS) {
			return -1;
		}
		double seconds = (last.nanos - first.nanos) / 1e9;
		if (seconds < 0.5) {
			return -1;
		}
		return (float) Math.min(nominal, (last.gameTime - first.gameTime) / seconds);
	}

	/** The rate the server is meant to run at (20 unless changed with /tick rate). */
	public static float nominal() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 20f : mc.level.tickRateManager().tickrate();
	}
}
