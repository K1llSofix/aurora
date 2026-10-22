package dev.aurora.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** One choice out of a fixed list. Option labels are translated as {@code <setting key>.<option>}. */
public final class ModeSetting extends Setting<String> {
	private final List<String> options;

	public ModeSetting(String id, String defaultValue, String... options) {
		super(id, defaultValue);
		this.options = List.of(options);
		if (!this.options.contains(defaultValue)) {
			throw new IllegalArgumentException("Default " + defaultValue + " is not an option of " + id);
		}
	}

	public List<String> options() {
		return options;
	}

	public boolean is(String option) {
		return value.equals(option);
	}

	public int index() {
		return options.indexOf(value);
	}

	public void cycle(int direction) {
		int i = Math.floorMod(index() + direction, options.size());
		set(options.get(i));
	}

	public String optionKey(String option) {
		return translationKey() + "." + option;
	}

	@Override
	protected String sanitize(String v) {
		return options.contains(v) ? v : defaultValue();
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json.isJsonPrimitive()) {
			set(json.getAsString());
		}
	}
}
