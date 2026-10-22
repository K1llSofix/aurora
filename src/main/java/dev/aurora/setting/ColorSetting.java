package dev.aurora.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.aurora.util.ColorUtil;

/**
 * An ARGB colour with an optional "chroma" mode that cycles the hue over time while keeping the
 * chosen saturation, brightness and alpha.
 */
public final class ColorSetting extends Setting<Integer> {
	private final boolean allowAlpha;
	private boolean chroma;
	private float chromaSpeed = 1f;

	public ColorSetting(String id, int defaultArgb, boolean allowAlpha) {
		super(id, defaultArgb);
		this.allowAlpha = allowAlpha;
	}

	public ColorSetting(String id, int defaultArgb) {
		this(id, defaultArgb, true);
	}

	public boolean allowAlpha() {
		return allowAlpha;
	}

	public boolean chroma() {
		return chroma;
	}

	public void setChroma(boolean chroma) {
		this.chroma = chroma;
	}

	public float chromaSpeed() {
		return chromaSpeed;
	}

	public void setChromaSpeed(float speed) {
		this.chromaSpeed = Math.max(0.1f, Math.min(5f, speed));
	}

	/** The colour to draw with right now. */
	public int color() {
		return color(0f);
	}

	/** @param offset phase offset in hue turns, so lists of elements can ripple. */
	public int color(float offset) {
		if (!chroma) {
			return value;
		}
		float[] hsv = ColorUtil.toHsv(value);
		float sat = Math.max(hsv[1], 0.55f);
		float bright = Math.max(hsv[2], 0.75f);
		return ColorUtil.rainbow(chromaSpeed, offset, sat, bright, ColorUtil.alpha(value));
	}

	@Override
	protected Integer sanitize(Integer v) {
		return allowAlpha ? v : (v | 0xFF000000);
	}

	@Override
	public void reset() {
		super.reset();
		chroma = false;
		chromaSpeed = 1f;
	}

	@Override
	public JsonElement toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("argb", ColorUtil.toHex(value, true));
		o.addProperty("chroma", chroma);
		o.addProperty("chromaSpeed", chromaSpeed);
		return o;
	}

	@Override
	public void fromJson(JsonElement json) {
		if (!json.isJsonObject()) {
			return;
		}
		JsonObject o = json.getAsJsonObject();
		if (o.has("argb")) {
			try {
				set((int) Long.parseLong(o.get("argb").getAsString().replace("#", ""), 16));
			} catch (NumberFormatException ignored) {
			}
		}
		if (o.has("chroma")) {
			chroma = o.get("chroma").getAsBoolean();
		}
		if (o.has("chromaSpeed")) {
			setChromaSpeed(o.get("chromaSpeed").getAsFloat());
		}
	}
}
