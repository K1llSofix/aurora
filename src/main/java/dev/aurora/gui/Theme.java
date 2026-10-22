package dev.aurora.gui;

import dev.aurora.Aurora;
import dev.aurora.module.ClientSettings;
import dev.aurora.util.ColorUtil;

/** The colour system. Surfaces are fixed dark tones; the accent pair comes from the theme setting. */
public final class Theme {
	public static final int TEXT = 0xFFF1F3F8;
	public static final int TEXT_DIM = 0xFFA3ABBB;
	public static final int TEXT_MUTED = 0xFF687083;

	public static final int OVERLAY_TOP = 0x8C05070D;
	public static final int OVERLAY_BOTTOM = 0xC005070D;
	public static final int PANEL = 0xF0101218;
	public static final int PANEL_TOP = 0xF2141720;
	public static final int SIDEBAR = 0xF50B0D12;
	public static final int CARD = 0xFF161920;
	public static final int CARD_HOVER = 0xFF1C2029;
	public static final int ELEVATED = 0xFF20242E;
	public static final int INPUT = 0xFF0D0F14;
	public static final int BORDER = 0x16FFFFFF;
	public static final int BORDER_STRONG = 0x26FFFFFF;
	public static final int DIVIDER = 0x10FFFFFF;
	public static final int SHADOW = 0x99000000;

	public static final int SUCCESS = 0xFF34D399;
	public static final int WARNING = 0xFFFBBF24;
	public static final int DANGER = 0xFFF87171;

	private Theme() {
	}

	private static ClientSettings settings() {
		return Aurora.client();
	}

	/** First accent colour (start of the gradient). */
	public static int accent() {
		return pair()[0];
	}

	/** Second accent colour (end of the gradient). */
	public static int accent2() {
		return pair()[1];
	}

	/** A point along the accent gradient. */
	public static int accent(float t) {
		int[] p = pair();
		return ColorUtil.lerp(p[0], p[1], t);
	}

	/** Accent colour that slowly flows along the gradient, for logos and highlights. */
	public static int accentFlow(float offset) {
		double s = System.nanoTime() / 1_000_000_000.0;
		float t = (float) (Math.sin(s * 1.6 + offset * Math.PI * 2) * 0.5 + 0.5);
		return accent(t);
	}

	private static int[] pair() {
		ClientSettings c = settings();
		String theme = c == null ? "aurora" : c.theme.get();
		return switch (theme) {
			case "ocean" -> new int[]{0xFF38BDF8, 0xFF6366F1};
			case "sunset" -> new int[]{0xFFFB923C, 0xFFEC4899};
			case "rose" -> new int[]{0xFFF472B6, 0xFFA855F7};
			case "emerald" -> new int[]{0xFF34D399, 0xFF22D3EE};
			case "crimson" -> new int[]{0xFFF43F5E, 0xFFF97316};
			case "mono" -> new int[]{0xFFE5E7EB, 0xFF8B93A3};
			case "custom" -> {
				int a = c.customAccent.color();
				float[] hsv = ColorUtil.toHsv(a);
				yield new int[]{a, ColorUtil.hsv(hsv[0] + 0.1f, hsv[1], Math.min(1f, hsv[2] * 0.95f + 0.05f), 255)};
			}
			default -> new int[]{0xFF2DD4BF, 0xFF8B5CF6};
		};
	}

	/** Readable text colour on top of an accent fill. */
	public static int onAccent() {
		return ColorUtil.luminance(accent(0.5f)) > 0.62f ? 0xFF0B0D12 : 0xFFFFFFFF;
	}
}
