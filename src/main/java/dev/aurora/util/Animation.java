package dev.aurora.util;

/**
 * A value that glides towards its target with exponential smoothing. The step depends on real
 * elapsed time, so it looks the same at 30 and at 300 frames per second.
 */
public final class Animation {
	private float value;
	private float target;
	private final float speed;
	private long lastNanos = System.nanoTime();

	/** @param speed roughly "how many times per second the gap shrinks by e"; 10-20 feels snappy. */
	public Animation(float initial, float speed) {
		this.value = initial;
		this.target = initial;
		this.speed = speed;
	}

	public Animation target(float target) {
		this.target = target;
		return this;
	}

	public Animation target(boolean on) {
		return target(on ? 1f : 0f);
	}

	public float get() {
		long now = System.nanoTime();
		float dt = Math.min((now - lastNanos) / 1_000_000_000f, 0.1f);
		lastNanos = now;
		float k = 1f - (float) Math.exp(-speed * dt * Motion.speedMultiplier());
		value += (target - value) * k;
		if (Math.abs(target - value) < 0.0005f) {
			value = target;
		}
		return value;
	}

	/** Reads the current value without advancing the clock. */
	public float peek() {
		return value;
	}

	public float getTarget() {
		return target;
	}

	public void snap(float v) {
		value = v;
		target = v;
		lastNanos = System.nanoTime();
	}

	public boolean settled() {
		return value == target;
	}
}
