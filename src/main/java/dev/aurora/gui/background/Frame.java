package dev.aurora.gui.background;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One frame of a menu background.
 *
 * @param w  width in GUI units
 * @param h  height in GUI units
 * @param t  scene clock in seconds (follows the vanilla panorama speed option)
 * @param px one physical pixel in GUI units, for hairlines and anti-aliasing
 * @param mx smoothed mouse position, -1 (left) to 1 (right)
 * @param my smoothed mouse position, -1 (top) to 1 (bottom)
 */
record Frame(GuiGraphicsExtractor g, float w, float h, double t, float px, float mx, float my) {
	/** Parallax offset of a layer; depth 0 is the sky, 1 the closest foreground. */
	float shiftX(float depth) {
		return -mx * depth * h * 0.035f;
	}

	float shiftY(float depth) {
		return -my * depth * h * 0.014f;
	}

	/** Extra width generated on both sides so layers never show an edge when shifted. */
	float margin() {
		return h * 0.06f;
	}

	float time() {
		return (float) t;
	}
}
