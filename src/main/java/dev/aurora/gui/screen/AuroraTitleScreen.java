package dev.aurora.gui.screen;

import com.mojang.realmsclient.RealmsMainScreen;
import dev.aurora.Aurora;
import dev.aurora.account.Account;
import dev.aurora.account.AccountManager;
import dev.aurora.gui.Theme;
import dev.aurora.gui.background.MenuBackground;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.gui.widget.Ui;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.Easing;
import dev.aurora.util.Motion;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Aurora's main menu: an animated background under a glass column, and a profile card. */
public final class AuroraTitleScreen extends Screen {
	private static final float BUTTON_W = 176;
	private static final float BUTTON_H = 24;
	private static final float GAP = 6;
	private static final int LEFT = 1;

	private final long openedAt = System.currentTimeMillis();
	private final Map<String, Animation> hovers = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();
	private ResolvableProfile profile;

	private record Hit(String id, float x, float y, float w, float h) {
	}

	public AuroraTitleScreen() {
		super(Component.translatable("narrator.screen.title"));
	}

	@Override
	protected void init() {
		profile = ResolvableProfile.createResolved(minecraft.getGameProfile());
	}

	private Animation hover(String id, boolean on) {
		return hovers.computeIfAbsent(id, k -> new Animation(0, 14)).target(on);
	}

	private float appear(int index) {
		long delay = 120 + index * 55L;
		return Easing.OUT_EXPO.clamped((System.currentTimeMillis() - openedAt - delay) * Motion.speedMultiplier() / 650f);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		extractPanorama(g, a);
		float intro = Easing.OUT_CUBIC.clamped(Motion.progress(openedAt, 900));
		if (MenuBackground.active()) {
			// Aurora's own backgrounds are composed around the menu, so a light veil behind the
			// column is enough to keep it readable and the picture stays in full colour.
			float veil = Math.min(width, Math.max(28, width * 0.08f) + BUTTON_W + width * 0.2f);
			Render2D.horizontalGradient(g, 0, 0, veil, height, ColorUtil.fade(0x9907090F, intro), 0x0007090F);
			return;
		}
		// Readability gradient: dark behind the menu column, open towards the right.
		Render2D.horizontalGradient(g, 0, 0, width, height, ColorUtil.fade(0xE6070910, intro), ColorUtil.fade(0x33070910, intro));
		Render2D.verticalGradient(g, 0, height * 0.6f, width, height * 0.4f, 0x00070910, ColorUtil.fade(0x99070910, intro));
		drawAurora(g, intro);
	}

	/** Soft drifting curtains of light along the top of the screen. */
	private void drawAurora(GuiGraphicsExtractor g, float alpha) {
		double t = System.nanoTime() / 1e9;
		int strips = 90;
		float sw = width / (float) strips;
		Render2D.Batch batch = Render2D.batch();
		for (int layer = 0; layer < 3; layer++) {
			float base = height * (0.16f + layer * 0.07f);
			float amp = height * (0.05f + layer * 0.015f);
			for (int i = 0; i < strips; i++) {
				float x = i * sw;
				double u = i / (double) strips;
				float wave = (float) (Math.sin(u * 7.0 + t * (0.35 + layer * 0.12) + layer * 1.7)
					+ 0.5 * Math.sin(u * 13.0 - t * 0.5 + layer));
				float top = base + wave * amp;
				float glow = (float) (0.55 + 0.45 * Math.sin(u * 5.0 + t * 0.8 + layer * 2.1));
				int color = Theme.accent((float) ((u + layer * 0.3 + t * 0.03) % 1.0));
				int strong = ColorUtil.fade(color, alpha * glow * (0.30f - layer * 0.07f));
				float h = height * (0.22f - layer * 0.03f);
				// Bright lower edge fading upwards, like real aurora curtains.
				batch.vertical(x, top - h, sw + 0.5f, h, ColorUtil.withAlpha(color, 0), strong);
				batch.vertical(x, top, sw + 0.5f, h * 0.18f, strong, ColorUtil.withAlpha(color, 0));
			}
		}
		batch.submit(g);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		hits.clear();
		float left = Math.max(28, width * 0.08f);
		// On short windows the buttons and gaps shrink so the column never runs into the footer.
		float room = height - 34 - 16 - 58;
		float bh = Math.max(16, Math.min(BUTTON_H, room / 6.25f));
		float gap = Math.max(3, Math.min(GAP, bh * 0.25f));
		float totalH = 58 + 5 * (bh + gap) + bh;
		float top = Math.max(12, (height - 30 - totalH) / 2f + 6);

		// Logo block
		float la = appear(0);
		Ui.alpha = la;
		g.pose().pushMatrix();
		g.pose().translate((1 - la) * -24, 0);
		Ui.logo(g, left + 15, top + 17, 30);
		Ui.gradientText(g, FontRenderer.BOLD, "AURORA", left + 40, top + 13, 22, Theme.accent(), Theme.accent2());
		FontRenderer.SEMIBOLD.drawMiddle(g, I18n.get("aurora.title.tagline"), left + 41, top + 31, 8, Ui.c(0xCCDDE3EE));
		g.pose().popMatrix();

		float y = top + 58;
		String[][] buttons = {
			{"singleplayer", I18n.get("menu.singleplayer")},
			{"multiplayer", I18n.get("menu.multiplayer")},
			{"realms", I18n.get("menu.online")},
			{"mods", I18n.get("aurora.title.mods")},
			{"options", I18n.get("menu.options")},
		};
		for (int i = 0; i < buttons.length; i++) {
			button(g, mouseX, mouseY, buttons[i][0], buttons[i][1], left, y, BUTTON_W, bh, i + 1, buttons[i][0].equals("mods"));
			y += bh + gap;
		}
		// Quit plus two square icon buttons.
		float small = bh;
		float quitW = BUTTON_W - (small + gap) * 2;
		button(g, mouseX, mouseY, "quit", I18n.get("menu.quit"), left, y, quitW, bh, 6, false);
		iconButton(g, mouseX, mouseY, "language", left + quitW + gap, y, small, 7);
		iconButton(g, mouseX, mouseY, "accessibility", left + quitW + gap * 2 + small, y, small, 8);

		drawProfile(g, mouseX, mouseY);
		drawFooter(g, mouseX, mouseY);
		Ui.alpha = 1f;
	}

	private void button(GuiGraphicsExtractor g, int mx, int my, String id, String label, float x, float y, float w, float h,
						int index, boolean highlight) {
		float ap = appear(index);
		if (ap <= 0) {
			return;
		}
		Ui.alpha = ap;
		x += (1 - ap) * -30;
		boolean hov = Ui.inside(mx, my, x, y, w, h);
		float hv = hover(id, hov).get();
		Render2D.roundRect(g, x, y, w, h, 8, Ui.c(ColorUtil.lerp(0x8C0D0F15, 0xB3141720, hv)));
		if (highlight) {
			Render2D.roundRect(g, x, y, w, h, 8, Ui.accentPaint(x, w, 0.22f));
		}
		if (hv > 0.01f) {
			// Accent fill that sweeps in from the left.
			float fw = Math.max(h, w * Easing.OUT_CUBIC.apply(hv));
			Render2D.glow(g, x, y, fw, h, 8, 10, Ui.c(ColorUtil.fade(Theme.accent(0.5f), 0.25f * hv)));
			Render2D.roundRect(g, x, y, fw, h, 8, Render2D.Paint.horizontal(x, w,
				Ui.c(ColorUtil.fade(Theme.accent(), 0.9f * hv)), Ui.c(ColorUtil.fade(Theme.accent2(), 0.9f * hv))));
		}
		Render2D.roundOutline(g, x, y, w, h, 8, 0.7f, Ui.c(ColorUtil.lerp(0x24FFFFFF, 0x55FFFFFF, hv)));
		int text = ColorUtil.lerp(Theme.TEXT, Theme.onAccent(), hv);
		FontRenderer.SEMIBOLD.drawMiddle(g, label, x + 14 + hv * 4, y + h / 2f, 9, Ui.c(text));
		if (hv > 0.01f) {
			Render2D.chevron(g, x + w - 14, y + h / 2f, 6, 1.4f, 0, Ui.c(ColorUtil.fade(text, hv)));
		}
		hits.add(new Hit(id, x, y, w, h));
	}

	private void iconButton(GuiGraphicsExtractor g, int mx, int my, String id, float x, float y, float s, int index) {
		float ap = appear(index);
		if (ap <= 0) {
			return;
		}
		Ui.alpha = ap;
		boolean hov = Ui.inside(mx, my, x, y, s, s);
		float hv = hover(id, hov).get();
		Render2D.roundRect(g, x, y, s, s, 8, Ui.c(ColorUtil.lerp(0x8C0D0F15, 0xCC1B1F29, hv)));
		Render2D.roundOutline(g, x, y, s, s, 8, 0.7f, Ui.c(ColorUtil.lerp(0x24FFFFFF, ColorUtil.fade(Theme.accent(), 0.8f), hv)));
		int color = ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv);
		float cx = x + s / 2f;
		float cy = y + s / 2f;
		if (id.equals("language")) {
			// Globe: circle, equator and a meridian ellipse.
			Render2D.ring(g, cx, cy, 5.5f, 1.1f, Ui.c(color));
			Render2D.rect(g, cx - 5.5f, cy - 0.5f, 11, 1, Ui.c(color));
			Render2D.roundOutline(g, cx - 2.6f, cy - 5.5f, 5.2f, 11, 2.6f, 1f, Ui.c(color));
		} else {
			// Accessibility: a figure with open arms.
			Render2D.circle(g, cx, cy - 4.2f, 1.6f, Ui.c(color));
			Render2D.line(g, cx - 4.5f, cy - 1.6f, cx + 4.5f, cy - 1.6f, 1.2f, Ui.c(color));
			Render2D.line(g, cx, cy - 1.6f, cx, cy + 1.8f, 1.2f, Ui.c(color));
			Render2D.polyline(g, 1.2f, Ui.c(color), cx - 2.6f, cy + 5f, cx, cy + 1.8f, cx + 2.6f, cy + 5f);
		}
		if (hov) {
			g.setTooltipForNextFrame(Component.translatable(id.equals("language") ? "options.language" : "options.accessibility"), mx, my);
		}
		hits.add(new Hit(id, x, y, s, s));
	}

	private void drawProfile(GuiGraphicsExtractor g, int mx, int my) {
		float ap = appear(3);
		Ui.alpha = ap;
		String name = minecraft.getUser().getName();
		Account account = AccountManager.current();
		boolean premium = account != null ? account.premium() : minecraft.getUser().getXuid().isPresent();
		String status = I18n.get(premium ? "aurora.title.account.microsoft" : "aurora.title.account.offline");
		float tw = Math.max(FontRenderer.SEMIBOLD.width(name, 9), FontRenderer.MEDIUM.width(status, 6.5f) + 8);
		float w = tw + 62;
		float h = 30;
		float x = width - w - 14 + (1 - ap) * 30;
		float y = 14;
		boolean hov = Ui.inside(mx, my, x, y, w, h);
		float hv = hover("profile", hov).get();
		Render2D.shadow(g, x, y, w, h, 10, 12, Ui.c(0x66000000));
		Render2D.roundRect(g, x, y, w, h, 10, Ui.c(ColorUtil.lerp(0xB30D0F15, 0xCC171A22, hv)));
		Render2D.roundOutline(g, x, y, w, h, 10, 0.7f, Ui.c(ColorUtil.lerp(0x24FFFFFF, ColorUtil.fade(Theme.accent(), 0.8f), hv)));
		Render2D.roundRect(g, x + 6, y + 6, 18, 18, 5, Render2D.Paint.diagonal(x + 6, y + 6, 18, 18, Ui.c(Theme.accent()), Ui.c(Theme.accent2())));
		if (ap > 0.5f && profile != null) {
			PlayerFaceExtractor.extractRenderState(g, profile, Math.round(x + 7), Math.round(y + 7), 16);
		}
		FontRenderer.SEMIBOLD.drawMiddle(g, name, x + 31, y + 11.5f, 9, Ui.c(Theme.TEXT));
		Render2D.circle(g, x + 34, y + 21, 2, Ui.c(premium ? Theme.SUCCESS : Theme.TEXT_MUTED));
		FontRenderer.MEDIUM.drawMiddle(g, status, x + 39, y + 21, 6.5f, Ui.c(Theme.TEXT_DIM));
		// Two arrows: click to switch account.
		float ax = x + w - 14;
		float ay = y + h / 2f;
		int arrows = Ui.c(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv));
		Render2D.chevron(g, ax, ay - 3, 5, 1.2f, 3, arrows);
		Render2D.chevron(g, ax, ay + 3, 5, 1.2f, 1, arrows);
		if (hov) {
			g.setTooltipForNextFrame(Component.translatable("aurora.accounts.tooltip"), mx, my);
		}
		hits.add(new Hit("accounts", x, y, w, h));
	}

	private void drawFooter(GuiGraphicsExtractor g, int mx, int my) {
		float ap = appear(9);
		Ui.alpha = ap;
		String version = "Minecraft " + SharedConstants.getCurrentVersion().name() + "  ·  Fabric  ·  Aurora "
			+ FabricLoader.getInstance().getModContainer(Aurora.MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("").split("\\+")[0];
		FontRenderer.MEDIUM.drawMiddle(g, version, 10, height - 10, 6.5f, Ui.c(0x99FFFFFF));
		String copyright = "Copyright Mojang AB. Do not distribute!";
		float cw = FontRenderer.MEDIUM.width(copyright, 6.5f);
		boolean hov = Ui.inside(mx, my, width - 10 - cw, height - 16, cw, 12);
		FontRenderer.MEDIUM.drawMiddle(g, copyright, width - 10 - cw, height - 10, 6.5f, Ui.c(hov ? 0xFFFFFFFF : 0x99FFFFFF));
		if (hov) {
			Render2D.rect(g, width - 10 - cw, height - 6, cw, 0.6f, Ui.c(0x99FFFFFF));
		}
		hits.add(new Hit("credits", width - 10 - cw, height - 16, cw, 12));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		for (Hit hit : hits) {
			if (Ui.inside(event.x(), event.y(), hit.x, hit.y, hit.w, hit.h)) {
				activate(hit.id);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void activate(String id) {
		switch (id) {
			case "singleplayer" -> minecraft.gui.setScreen(new SelectWorldScreen(this));
			case "multiplayer" -> minecraft.gui.setScreen(minecraft.options.skipMultiplayerWarning
				? new JoinMultiplayerScreen(this) : new SafetyScreen(this));
			case "realms" -> minecraft.gui.setScreen(new RealmsMainScreen(this));
			case "mods" -> minecraft.gui.setScreen(new ModMenuScreen(this));
			case "options" -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options));
			case "quit" -> minecraft.stop();
			case "language" -> minecraft.gui.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager()));
			case "accessibility" -> minecraft.gui.setScreen(new AccessibilityOptionsScreen(this, minecraft.options));
			case "credits" -> minecraft.gui.setScreen(new CreditsAndAttributionScreen(this));
			case "accounts" -> minecraft.gui.setScreen(new AccountsScreen(this));
			default -> {
			}
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (Aurora.menuKey.matches(event)) {
			minecraft.gui.setScreen(new ModMenuScreen(this));
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
