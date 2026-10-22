package dev.aurora.gui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.config.Profiles;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A profile on the Profiles page: name (editable for saved profiles), what it is, the servers it
 * switches on for, and buttons to load, overwrite, copy as a code or delete it.
 */
public final class ProfileRow extends SettingRow {
	public static final float ROW_HEIGHT = 40;
	private static final float BUTTON = 18;

	public interface Actions {
		void load(Profiles.Profile profile);

		void overwrite(Profiles.Profile profile);

		void copy(Profiles.Profile profile);

		void delete(Profiles.Profile profile);
	}

	private final Profiles.Profile profile;
	private final boolean active;
	private final Actions actions;
	private final TextField name;
	private final TextField server;
	private final Animation[] hovers = {new Animation(0, 16), new Animation(0, 16), new Animation(0, 16), new Animation(0, 16)};
	private float loadX, loadW, iconsX, by;

	public ProfileRow(Profiles.Profile profile, boolean active, Actions actions) {
		super(profile.name(), () -> {
		});
		this.profile = profile;
		this.active = active;
		this.actions = actions;
		this.name = new TextField(I18n.get("aurora.profiles.name"), 32).plain();
		name.setValue(profile.name());
		this.server = new TextField(I18n.get("aurora.profiles.server_placeholder"), 120).plain();
		String bound = Profiles.serverOf(profile);
		server.setValue(bound == null ? "" : bound);
	}

	public Profiles.Profile profile() {
		return profile;
	}

	public void focusName() {
		name.setFocused(true);
	}

	@Override
	public float height() {
		return ROW_HEIGHT;
	}

	private int buttonCount() {
		return profile.builtin() ? 1 : 3;
	}

	@Override
	public void render(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w) {
		this.x = x;
		this.y = y;
		this.w = w;
		boolean inside = Ui.inside(mx, my, x, y, w, ROW_HEIGHT);
		float hv = hover.target(inside && !blocked).get();
		if (active) {
			Render2D.roundRect(g, x + 2, y + 2, w - 4, ROW_HEIGHT - 4, 8, Ui.accentPaint(x, w, 0.1f));
			Render2D.roundOutline(g, x + 2, y + 2, w - 4, ROW_HEIGHT - 4, 8, 0.6f, Ui.c(ColorUtil.fade(Theme.accent(), 0.45f)));
		} else if (hv > 0.01f) {
			Render2D.roundRect(g, x + 2, y + 2, w - 4, ROW_HEIGHT - 4, 8, Ui.c(ColorUtil.withAlpha(0xFFFFFF, Math.round(7 * hv))));
		}
		float cy = y + ROW_HEIGHT / 2f;

		// Tile with the first letter.
		float tx = x + 10;
		String letter = profile.name().isEmpty() ? "?" : profile.name().substring(0, 1).toUpperCase(Locale.ROOT);
		if (profile.builtin()) {
			Render2D.roundRect(g, tx, cy - 11, 22, 22, 7, Render2D.Paint.diagonal(tx, cy - 11, 22, 22, Ui.c(Theme.accent()), Ui.c(Theme.accent2())));
			FontRenderer.BOLD.drawCentered(g, letter, tx + 11, cy + 0.3f, 9, Ui.c(Theme.onAccent()));
		} else {
			Render2D.roundRect(g, tx, cy - 11, 22, 22, 7, Ui.c(Theme.ELEVATED));
			Render2D.roundOutline(g, tx, cy - 11, 22, 22, 7, 0.6f, Ui.c(Theme.BORDER));
			FontRenderer.BOLD.drawCentered(g, letter, tx + 11, cy + 0.3f, 9, Ui.c(Theme.TEXT));
		}

		// Buttons from the right: icon buttons, then "Load".
		iconsX = x + w - 10 - buttonCount() * (BUTTON + 4) + 4;
		by = cy - BUTTON / 2f;
		String loadText = I18n.get(active ? "aurora.profiles.reload" : "aurora.profiles.load");
		loadW = FontRenderer.SEMIBOLD.width(loadText, 7.5f) + 18;
		loadX = iconsX - 8 - loadW;
		boolean loadHov = Ui.inside(mx, my, loadX, by, loadW, BUTTON);
		float lh = hovers[0].target(loadHov).get();
		Render2D.roundRect(g, loadX, by, loadW, BUTTON, 6, Ui.accentPaint(loadX, loadW, 0.22f + 0.2f * lh));
		Render2D.roundOutline(g, loadX, by, loadW, BUTTON, 6, 0.7f, Ui.c(ColorUtil.fade(Theme.accent(), 0.5f + 0.4f * lh)));
		FontRenderer.SEMIBOLD.drawCentered(g, loadText, loadX + loadW / 2f, cy, 7.5f, Ui.c(Theme.TEXT));
		iconButton(g, mx, my, 0, 1, "aurora.profiles.copy");
		if (!profile.builtin()) {
			iconButton(g, mx, my, 1, 2, "aurora.profiles.overwrite");
			iconButton(g, mx, my, 2, 3, "aurora.profiles.delete");
		}

		// Server binding field.
		float serverW = Math.min(118, Math.max(0, (loadX - 12 - (tx + 30)) * 0.42f));
		float sx = loadX - 10 - serverW;
		if (serverW >= 70) {
			server.x = sx;
			server.y = cy - 9;
			server.w = serverW;
			server.h = 18;
			server.render(g, mx, my);
			if (Ui.inside(mx, my, sx, cy - 9, serverW, 18) && !server.focused()) {
				g.setTooltipForNextFrame(Component.translatable("aurora.profiles.server_tooltip"), mx, my);
			}
		} else {
			sx = loadX;
		}

		// Name and description.
		float left = tx + 30;
		float right = sx - 10;
		if (profile.builtin()) {
			FontRenderer.SEMIBOLD.drawMiddle(g, FontRenderer.SEMIBOLD.ellipsize(profile.name(), 8.5f, right - left), left, cy - 5, 8.5f, Ui.c(Theme.TEXT));
		} else {
			name.x = left - 4;
			name.y = cy - 14;
			name.w = Math.max(40, right - left + 4);
			name.h = 16;
			name.render(g, mx, my);
		}
		String sub = profile.builtin() ? profile.description()
			: I18n.get("aurora.profiles.saved", new SimpleDateFormat("dd.MM HH:mm").format(new Date(profile.saved())));
		if (active) {
			sub = I18n.get("aurora.profiles.active") + " · " + sub;
		}
		FontRenderer.MEDIUM.drawMiddle(g, FontRenderer.MEDIUM.ellipsize(sub, 6.8f, right - left), left, cy + 7, 6.8f,
			Ui.c(active ? ColorUtil.fade(Theme.accent(), 0.95f) : Theme.TEXT_MUTED));
	}

	/** Icon button number {@code index} from the left of the icon group. */
	private void iconButton(GuiGraphicsExtractor g, int mx, int my, int index, int hoverSlot, String tooltip) {
		float bx = iconsX + index * (BUTTON + 4);
		boolean hov = Ui.inside(mx, my, bx, by, BUTTON, BUTTON);
		float h = hovers[hoverSlot].target(hov).get();
		boolean danger = tooltip.endsWith("delete");
		int bg = danger ? ColorUtil.fade(Theme.DANGER, 0.18f * h) : ColorUtil.withAlpha(0xFFFFFF, Math.round(10 + 14 * h));
		Render2D.roundRect(g, bx, by, BUTTON, BUTTON, 6, Ui.c(bg));
		int color = danger ? ColorUtil.lerp(Theme.TEXT_MUTED, Theme.DANGER, h) : ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, h);
		float cx = bx + BUTTON / 2f;
		float cy = by + BUTTON / 2f;
		switch (index) {
			case 0 -> Ui.iconCopy(g, cx, cy, 10, color);
			case 1 -> Ui.iconSave(g, cx, cy, 10, color);
			default -> Ui.iconClose(g, cx, cy, 9, color);
		}
		if (hov) {
			g.setTooltipForNextFrame(Component.translatable(tooltip), mx, my);
		}
	}

	@Override
	protected void renderControl(GuiGraphicsExtractor g, int mx, int my) {
	}

	private void commitFields() {
		if (name.focused()) {
			name.setFocused(false);
			if (!name.value().isBlank() && !name.value().equals(profile.name())) {
				Profiles.rename(profile, name.value());
			}
		}
		if (server.focused()) {
			server.setFocused(false);
			Profiles.setServer(profile, server.value());
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		boolean inside = Ui.inside(mx, my, x, y, w, ROW_HEIGHT);
		if (button == 1 && Ui.inside(mx, my, server.x, server.y, server.w, server.h) && server.w >= 70) {
			if (name.focused()) {
				commitFields();
			}
			server.setFocused(true);
			return true;
		}
		if (button == 1 && !profile.builtin() && Ui.inside(mx, my, name.x, name.y, name.w, name.h)) {
			if (server.focused()) {
				commitFields();
			}
			name.setFocused(true);
			return true;
		}
		if (name.focused() || server.focused()) {
			commitFields();
			if (!inside) {
				return false;
			}
		}
		if (!inside || button != 1) {
			return inside;
		}
		if (Ui.inside(mx, my, loadX, by, loadW, BUTTON)) {
			actions.load(profile);
			return true;
		}
		for (int i = 0; i < buttonCount(); i++) {
			float bx = iconsX + i * (BUTTON + 4);
			if (Ui.inside(mx, my, bx, by, BUTTON, BUTTON)) {
				switch (i) {
					case 0 -> actions.copy(profile);
					case 1 -> actions.overwrite(profile);
					default -> actions.delete(profile);
				}
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		TextField field = name.focused() ? name : server.focused() ? server : null;
		if (field == null) {
			return false;
		}
		if (event.isEscape() || event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
			commitFields();
			return true;
		}
		return field.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (name.focused()) {
			return name.charTyped(event);
		}
		return server.focused() && server.charTyped(event);
	}

	@Override
	public boolean capturesKeys() {
		return name.focused() || server.focused();
	}

	@Override
	public boolean typing() {
		return capturesKeys();
	}

	/** Used by the page to pass a click that landed elsewhere: closes an open field. */
	public void blur() {
		commitFields();
	}
}
