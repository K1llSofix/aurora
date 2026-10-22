package dev.aurora.util;

import java.util.ArrayDeque;
import java.util.Deque;

/** Counts mouse clicks over the last second. Fed by the mouse handler mixin. */
public final class CpsTracker {
	private static final Deque<Long> LEFT = new ArrayDeque<>();
	private static final Deque<Long> RIGHT = new ArrayDeque<>();

	private CpsTracker() {
	}

	/** SDL button numbering: 1 = left, 3 = right. */
	public static void onClick(int button) {
		long now = System.currentTimeMillis();
		if (button == 1) {
			LEFT.addLast(now);
		} else if (button == 3) {
			RIGHT.addLast(now);
		}
	}

	public static int left() {
		return count(LEFT);
	}

	public static int right() {
		return count(RIGHT);
	}

	private static int count(Deque<Long> clicks) {
		long cutoff = System.currentTimeMillis() - 1000;
		while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
			clicks.pollFirst();
		}
		return clicks.size();
	}
}
