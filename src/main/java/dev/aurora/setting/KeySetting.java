package dev.aurora.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A GLFW key code, or -1 when unbound. Mouse buttons are stored as -100 - button. */
public final class KeySetting extends Setting<Integer> {
	public static final int NONE = -1;

	public KeySetting(String id, int defaultKey) {
		super(id, defaultKey);
	}

	public boolean bound() {
		return value != NONE;
	}

	public boolean isMouse() {
		return value <= -100;
	}

	public int mouseButton() {
		return -100 - value;
	}

	public static int mouseCode(int button) {
		return -100 - button;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsInt());
		}
	}
}
