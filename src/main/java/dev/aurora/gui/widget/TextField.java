package dev.aurora.gui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/** A single-line text box (search by default) with a placeholder, focus glow and blinking caret. */
public final class TextField {
	private final StringBuilder text = new StringBuilder();
	private final String placeholder;
	private final int maxLength;
	private boolean focused;
	private boolean allSelected;
	private long lastEdit = System.currentTimeMillis();
	private final Animation focus = new Animation(0, 14);
	private final Animation hover = new Animation(0, 18);
	private Runnable onChange = () -> {
	};
	private boolean searchIcon = true;
	private boolean escapeClears = true;
	private float radius = -1;

	public float x, y, w, h;

	public TextField(String placeholder, int maxLength) {
		this.placeholder = placeholder;
		this.maxLength = maxLength;
	}

	public TextField onChange(Runnable r) {
		this.onChange = r;
		return this;
	}

	/** A plain input: no magnifier, Escape only leaves the field and slightly squarer corners. */
	public TextField plain() {
		searchIcon = false;
		escapeClears = false;
		radius = 6;
		return this;
	}

	public String value() {
		return text.toString();
	}

	public void setValue(String v) {
		text.setLength(0);
		text.append(v);
		onChange.run();
	}

	public boolean focused() {
		return focused;
	}

	public void setFocused(boolean f) {
		focused = f;
		allSelected = false;
		lastEdit = System.currentTimeMillis();
	}

	public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		float f = focus.target(focused).get();
		float hv = hover.target(Ui.inside(mouseX, mouseY, x, y, w, h)).get();
		float r = radius < 0 ? h / 2f : Math.min(radius, h / 2f);
		if (f > 0.01f) {
			Render2D.glow(g, x, y, w, h, r, 6, Ui.c(ColorUtil.fade(Theme.accent(), 0.22f * f)));
		}
		Render2D.roundRect(g, x, y, w, h, r, Ui.c(ColorUtil.lerp(Theme.INPUT, 0xFF12151B, hv)));
		int border = ColorUtil.lerp(ColorUtil.lerp(Theme.BORDER, Theme.BORDER_STRONG, hv), Theme.accent(), f);
		Render2D.roundOutline(g, x, y, w, h, r, 0.8f, Ui.c(border));
		float tx;
		if (searchIcon) {
			Ui.iconSearch(g, x + h / 2f + 2, y + h / 2f, 8, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.accent(), f));
			tx = x + h / 2f + 9;
		} else {
			tx = x + 7;
		}
		float maxW = w - (tx - x) - 8;
		float size = 8f;
		FontRenderer font = FontRenderer.MEDIUM;
		g.enableScissor(Math.round(tx - 1), Math.round(y), Math.round(x + w - 6), Math.round(y + h));
		if (text.isEmpty()) {
			font.drawMiddle(g, placeholder, tx, y + h / 2f, size, Ui.c(Theme.TEXT_MUTED));
		} else {
			String s = text.toString();
			float tw = font.width(s, size);
			float shift = Math.max(0, tw - maxW);
			if (allSelected) {
				Render2D.roundRect(g, tx - shift - 1, y + h / 2f - 5.5f, tw + 2, 11, 2, Ui.c(ColorUtil.fade(Theme.accent(), 0.35f)));
			}
			font.drawMiddle(g, s, tx - shift, y + h / 2f, size, Ui.c(Theme.TEXT));
			tx += tw - shift;
		}
		if (focused && ((System.currentTimeMillis() - lastEdit) / 530) % 2 == 0) {
			Render2D.rect(g, tx + 0.5f, y + h / 2f - 4.5f, 0.8f, 9, Ui.c(Theme.accent()));
		}
		g.disableScissor();
	}

	public boolean mouseClicked(double mx, double my) {
		boolean inside = Ui.inside(mx, my, x, y, w, h);
		setFocused(inside);
		return inside;
	}

	public boolean keyPressed(KeyEvent event) {
		if (!focused) {
			return false;
		}
		if (event.isSelectAll()) {
			allSelected = !text.isEmpty();
			return true;
		}
		if (event.isPaste()) {
			String clip = net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard();
			insert(clip.replaceAll("[\\r\\n\\t]", " "));
			return true;
		}
		int key = event.key();
		if (key == InputConstants.KEY_BACKSPACE) {
			if (allSelected || event.hasControlDownWithQuirk()) {
				text.setLength(0);
			} else if (!text.isEmpty()) {
				text.setLength(text.length() - 1);
			}
			allSelected = false;
			edited();
			return true;
		}
		if (key == InputConstants.KEY_DELETE && allSelected) {
			text.setLength(0);
			allSelected = false;
			edited();
			return true;
		}
		if (event.isEscape()) {
			if (escapeClears && !text.isEmpty()) {
				text.setLength(0);
				edited();
				return true;
			}
			setFocused(false);
			return false;
		}
		return false;
	}

	public boolean charTyped(CharacterEvent event) {
		if (!focused || !event.isAllowedChatCharacter()) {
			return false;
		}
		insert(event.codepointAsString());
		return true;
	}

	private void insert(String s) {
		if (allSelected) {
			text.setLength(0);
			allSelected = false;
		}
		for (int i = 0; i < s.length() && text.length() < maxLength; i++) {
			text.append(s.charAt(i));
		}
		edited();
	}

	private void edited() {
		lastEdit = System.currentTimeMillis();
		onChange.run();
	}
}
