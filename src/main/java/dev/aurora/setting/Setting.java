package dev.aurora.setting;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A single configurable value of a module. Names and descriptions come from the language files. */
public abstract class Setting<T> {
	private final String id;
	private final T defaultValue;
	protected T value;
	private String translationPrefix = "aurora.setting";
	private BooleanSupplier visibility = () -> true;
	private final List<Consumer<T>> listeners = new ArrayList<>();

	protected Setting(String id, T defaultValue) {
		this.id = id;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public String id() {
		return id;
	}

	public T get() {
		return value;
	}

	public void set(T newValue) {
		T old = value;
		value = sanitize(newValue);
		if (!value.equals(old)) {
			for (Consumer<T> listener : listeners) {
				listener.accept(value);
			}
		}
	}

	public T defaultValue() {
		return defaultValue;
	}

	public void reset() {
		set(defaultValue);
	}

	protected T sanitize(T v) {
		return v;
	}

	/** Called by the owning module so keys read e.g. aurora.setting.crosshair.gap. */
	public void bindTranslationPrefix(String prefix) {
		this.translationPrefix = prefix;
	}

	public String translationKey() {
		return translationPrefix + "." + id;
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
		this.visibility = condition;
		return (S) this;
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S onChange(Consumer<T> listener) {
		listeners.add(listener);
		return (S) this;
	}

	public boolean isVisible() {
		return visibility.getAsBoolean();
	}

	public abstract JsonElement toJson();

	public abstract void fromJson(JsonElement json);
}
