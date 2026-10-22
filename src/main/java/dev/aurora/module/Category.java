package dev.aurora.module;

public enum Category {
	HUD("hud"),
	VISUAL("visual"),
	UTILITY("utility"),
	PERFORMANCE("performance"),
	/** The client's own settings page; never listed as a card. */
	CLIENT("client");

	private final String id;

	Category(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public String translationKey() {
		return "aurora.category." + id;
	}
}
