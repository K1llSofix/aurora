package dev.aurora.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A slider. Values snap to {@code step}; the suffix is appended in the UI (e.g. "%", "px", "x"). */
public final class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private String suffix = "";

	public NumberSetting(String id, double defaultValue, double min, double max, double step) {
		super(id, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
	}

	public NumberSetting suffix(String suffix) {
		this.suffix = suffix;
		return this;
	}

	private java.util.function.DoubleFunction<String> formatter;

	/** Custom value text, e.g. a time of day instead of a raw number. */
	public NumberSetting format(java.util.function.DoubleFunction<String> formatter) {
		this.formatter = formatter;
		return this;
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public double step() {
		return step;
	}

	public float floatValue() {
		return value.floatValue();
	}

	public int intValue() {
		return (int) Math.round(value);
	}

	/** Position of the value along the slider, 0 at min and 1 at max. */
	public float fraction() {
		return (float) ((value - min) / (max - min));
	}

	public void setFraction(double f) {
		set(min + (max - min) * f);
	}

	@Override
	protected Double sanitize(Double v) {
		double snapped = Math.round((v - min) / step) * step + min;
		snapped = Math.max(min, Math.min(max, snapped));
		// Kill floating point noise such as 0.30000000000000004.
		return Math.round(snapped * 10000.0) / 10000.0;
	}

	public String display() {
		if (formatter != null) {
			return formatter.apply(value);
		}
		boolean whole = step >= 1 && step == Math.floor(step);
		String number;
		if (whole) {
			number = Long.toString(Math.round(value));
		} else {
			int decimals = step >= 0.1 ? 1 : 2;
			number = String.format(java.util.Locale.ROOT, "%." + decimals + "f", value);
		}
		return number + suffix;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsDouble());
		}
	}
}
