package dev.aurora.gui.screen;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.account.Account;
import dev.aurora.account.AccountManager;
import dev.aurora.account.MicrosoftAuth;
import dev.aurora.gui.Notifications;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.gui.widget.TextField;
import dev.aurora.gui.widget.Ui;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.Easing;
import dev.aurora.util.Motion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Switch between Minecraft accounts without restarting: the launcher's account, saved Microsoft
 * accounts (signed in through microsoft.com/link with a short code) and offline names.
 */
public final class AccountsScreen extends Screen {
	private static final int LEFT = 1;
	private static final int RIGHT = 3;
	private static final float ROW = 30;
	private static final String AZURE_GUIDE = "https://learn.microsoft.com/entra/identity-platform/quickstart-register-app";

	private enum Mode {LIST, OFFLINE, CLIENT_ID, MICROSOFT}

	private final Screen parent;
	private final long openedAt = System.currentTimeMillis();
	private Mode mode = Mode.LIST;
	private final TextField nameField = new TextField("Steve", 16).plain();
	private final TextField clientField = new TextField("00000000-0000-0000-0000-000000000000", 64).plain();
	private final Map<String, Animation> hovers = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();
	private final Map<Account, ResolvableProfile> faces = new IdentityHashMap<>();
	private String status = "";
	private int statusColor = Theme.TEXT_DIM;
	private @Nullable Account switching;
	private MicrosoftAuth.@Nullable DeviceCode code;
	private final AtomicBoolean cancelled = new AtomicBoolean();
	private float scroll;

	private record Hit(String id, float x, float y, float w, float h, @Nullable Account account) {
	}

	public AccountsScreen(Screen parent) {
		super(Component.translatable("aurora.accounts.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		updateTextInput();
	}

	private void updateTextInput() {
		if (mode == Mode.OFFLINE) {
			minecraft.textInputManager().startTextInput(nameField);
		} else if (mode == Mode.CLIENT_ID) {
			minecraft.textInputManager().startTextInput(clientField);
		} else {
			minecraft.textInputManager().stopTextInput(nameField);
			minecraft.textInputManager().stopTextInput(clientField);
		}
	}

	private void setMode(Mode next) {
		mode = next;
		nameField.setFocused(next == Mode.OFFLINE);
		clientField.setFocused(next == Mode.CLIENT_ID);
		updateTextInput();
	}

	private void status(String key, int color, Object... args) {
		status = I18n.get(key, args);
		statusColor = color;
	}

	private Animation hover(String id, boolean on) {
		return hovers.computeIfAbsent(id, k -> new Animation(0, 14)).target(on);
	}

	private ResolvableProfile face(Account account) {
		return faces.computeIfAbsent(account, a -> {
			if (a == AccountManager.current()) {
				return ResolvableProfile.createResolved(minecraft.getGameProfile());
			}
			return a.premium() ? ResolvableProfile.createUnresolved(a.uuid()) : ResolvableProfile.createResolved(new GameProfile(a.uuid(), a.name()));
		});
	}

	// ======================================================================= drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		extractPanorama(g, a);
		Render2D.rect(g, 0, 0, width, height, 0x8C05070D);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		hits.clear();
		float appear = Easing.OUT_CUBIC.clamped(Motion.progress(openedAt, 260));
		Ui.alpha = appear;
		float w = Math.min(360, width - 32);
		float h = Math.min(300, height - 24);
		float x = (width - w) / 2f;
		float y = (height - h) / 2f + (1 - appear) * 10;
		Render2D.shadow(g, x, y, w, h, 14, 20, Ui.c(0x8C000000));
		Render2D.roundRect(g, x, y, w, h, 14, Render2D.Paint.vertical(y, h, Ui.c(0xF5171A23), Ui.c(0xF50E1016)));
		Render2D.roundOutline(g, x, y, w, h, 14, 0.8f, Render2D.Paint.horizontal(x, w, Ui.c(ColorUtil.fade(Theme.accent(), 0.5f)), Ui.c(0x1FFFFFFF)));

		FontRenderer.SEMIBOLD.drawMiddle(g, I18n.get("aurora.accounts.title"), x + 18, y + 20, 13, Ui.c(Theme.TEXT));
		FontRenderer.MEDIUM.drawMiddle(g, FontRenderer.MEDIUM.ellipsize(I18n.get(subtitleKey()), 7.5f, w - 60), x + 18, y + 35, 7.5f,
			Ui.c(Theme.TEXT_DIM));
		closeButton(g, mouseX, mouseY, x + w - 24, y + 14);

		float top = y + 50;
		float bottom = y + h - 54;
		switch (mode) {
			case LIST -> list(g, mouseX, mouseY, x + 12, top, w - 24, bottom - top);
			case OFFLINE -> offline(g, mouseX, mouseY, x + 18, top + 4, w - 36);
			case CLIENT_ID -> clientId(g, mouseX, mouseY, x + 18, top + 2, w - 36);
			case MICROSOFT -> microsoft(g, mouseX, mouseY, x + 18, top + 4, w - 36);
		}
		if (!status.isEmpty()) {
			List<String> lines = FontRenderer.MEDIUM.wrap(status, 7.5f, w - 36);
			float sy = bottom + 6;
			for (String line : lines.subList(0, Math.min(2, lines.size()))) {
				FontRenderer.MEDIUM.drawMiddle(g, line, x + 18, sy, 7.5f, Ui.c(statusColor));
				sy += 10;
			}
		}
		if (mode == Mode.LIST) {
			float bw = (w - 36 - 8) / 2f;
			button(g, mouseX, mouseY, "add_microsoft", I18n.get("aurora.accounts.add_microsoft"), x + 18, y + h - 32, bw, 22, true);
			button(g, mouseX, mouseY, "add_offline", I18n.get("aurora.accounts.add_offline"), x + 26 + bw, y + h - 32, bw, 22, false);
		}
		Ui.alpha = 1f;
	}

	private String subtitleKey() {
		return switch (mode) {
			case LIST -> "aurora.accounts.subtitle";
			case OFFLINE -> "aurora.accounts.offline_subtitle";
			case CLIENT_ID -> "aurora.accounts.client_subtitle";
			case MICROSOFT -> "aurora.accounts.microsoft_subtitle";
		};
	}

	private void list(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w, float h) {
		List<Account> accounts = AccountManager.all();
		float content = accounts.size() * (ROW + 4);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, content - h)));
		g.enableScissor(Math.round(x - 2), Math.round(y), Math.round(x + w + 2), Math.round(y + h));
		float ry = y - scroll;
		for (Account account : accounts) {
			row(g, mx, my, account, x, ry, w);
			ry += ROW + 4;
		}
		g.disableScissor();
	}

	private void row(GuiGraphicsExtractor g, int mx, int my, Account account, float x, float y, float w) {
		boolean current = account == AccountManager.current();
		String id = "row" + System.identityHashCode(account);
		boolean hov = Ui.inside(mx, my, x, y, w, ROW);
		float hv = hover(id, hov).get();
		Render2D.roundRect(g, x, y, w, ROW, 9, Ui.c(ColorUtil.lerp(0x66161922, 0x991D212B, hv)));
		if (current) {
			Render2D.roundRect(g, x, y, w, ROW, 9, Ui.accentPaint(x, w, 0.16f));
			Render2D.roundOutline(g, x, y, w, ROW, 9, 0.9f, Render2D.Paint.horizontal(x, w, Ui.c(Theme.accent()), Ui.c(Theme.accent2())));
		} else {
			Render2D.roundOutline(g, x, y, w, ROW, 9, 0.7f, Ui.c(ColorUtil.lerp(0x14FFFFFF, 0x2EFFFFFF, hv)));
		}
		float fx = x + 7;
		float fy = y + 6;
		Render2D.roundRect(g, fx - 1, fy - 1, 20, 20, 5, Ui.c(0xFF0B0D12));
		if (Ui.alpha > 0.5f) {
			PlayerFaceExtractor.extractRenderState(g, face(account), Math.round(fx), Math.round(fy), 18);
		}
		FontRenderer.SEMIBOLD.drawMiddle(g, account.name(), x + 34, y + 11, 9, Ui.c(Theme.TEXT));
		int badgeColor = switch (account.type()) {
			case MICROSOFT -> Theme.SUCCESS;
			case OFFLINE -> Theme.TEXT_DIM;
			case LAUNCHER -> account.premium() ? Theme.SUCCESS : Theme.TEXT_DIM;
		};
		String badge = I18n.get(switch (account.type()) {
			case MICROSOFT -> "aurora.accounts.type.microsoft";
			case OFFLINE -> "aurora.accounts.type.offline";
			case LAUNCHER -> account.premium() ? "aurora.accounts.type.launcher_premium" : "aurora.accounts.type.launcher";
		});
		Render2D.circle(g, x + 36.5f, y + 21.5f, 1.8f, Ui.c(badgeColor));
		FontRenderer.MEDIUM.drawMiddle(g, badge, x + 41, y + 21.5f, 7, Ui.c(Theme.TEXT_DIM));

		float right = x + w - 10;
		if (switching == account) {
			float t = (float) (System.nanoTime() / 1e9);
			Render2D.arc(g, right - 8, y + ROW / 2f, 6, 1.6f, (t * 360) % 360, 110, Ui.c(Theme.accent()));
		} else if (current) {
			String active = I18n.get("aurora.accounts.active");
			float bw = FontRenderer.SEMIBOLD.width(active, 7) + 14;
			Render2D.roundRect(g, right - bw, y + ROW / 2f - 7, bw, 14, 7, Ui.accentPaint(right - bw, bw, 0.9f));
			FontRenderer.SEMIBOLD.drawCentered(g, active, right - bw / 2f, y + ROW / 2f, 7, Ui.c(Theme.onAccent()));
		} else if (account.type() != Account.Type.LAUNCHER) {
			float cx = right - 7;
			float cy = y + ROW / 2f;
			boolean overDelete = Ui.inside(mx, my, cx - 8, cy - 8, 16, 16);
			if (overDelete) {
				Render2D.circle(g, cx, cy, 8, Ui.c(ColorUtil.fade(Theme.DANGER, 0.18f)));
				g.setTooltipForNextFrame(Component.translatable("aurora.accounts.remove"), mx, my);
			}
			Render2D.cross(g, cx, cy, 6.5f, 1.3f, Ui.c(overDelete ? Theme.DANGER : ColorUtil.fade(Theme.TEXT_MUTED, hv)));
			hits.add(new Hit("remove", cx - 8, cy - 8, 16, 16, account));
		}
		hits.add(new Hit("account", x, y, w, ROW, account));
	}

	private void offline(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w) {
		FontRenderer.MEDIUM.drawMiddle(g, I18n.get("aurora.accounts.name"), x, y + 6, 7.5f, Ui.c(Theme.TEXT_MUTED));
		nameField.x = x;
		nameField.y = y + 14;
		nameField.w = w;
		nameField.h = 22;
		nameField.render(g, mx, my);
		List<String> lines = FontRenderer.MEDIUM.wrap(I18n.get("aurora.accounts.offline_note"), 7.5f, w);
		float ly = y + 50;
		for (String line : lines) {
			FontRenderer.MEDIUM.drawMiddle(g, line, x, ly, 7.5f, Ui.c(Theme.TEXT_DIM));
			ly += 10;
		}
		float bw = (w - 8) / 2f;
		button(g, mx, my, "offline_add", I18n.get("aurora.accounts.add"), x, ly + 8, bw, 22, true);
		button(g, mx, my, "back", I18n.get("gui.back"), x + bw + 8, ly + 8, bw, 22, false);
	}

	private void clientId(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w) {
		List<String> lines = FontRenderer.MEDIUM.wrap(I18n.get("aurora.accounts.client_note"), 7.5f, w);
		float ly = y + 6;
		for (String line : lines) {
			FontRenderer.MEDIUM.drawMiddle(g, line, x, ly, 7.5f, Ui.c(Theme.TEXT_DIM));
			ly += 10;
		}
		clientField.x = x;
		clientField.y = ly + 4;
		clientField.w = w;
		clientField.h = 22;
		clientField.render(g, mx, my);
		float by = ly + 34;
		float bw = (w - 16) / 3f;
		button(g, mx, my, "client_save", I18n.get("aurora.accounts.save"), x, by, bw, 22, true);
		button(g, mx, my, "client_guide", I18n.get("aurora.accounts.guide"), x + bw + 8, by, bw, 22, false);
		button(g, mx, my, "back", I18n.get("gui.back"), x + (bw + 8) * 2, by, bw, 22, false);
	}

	private void microsoft(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w) {
		float cx = x + w / 2f;
		if (code == null) {
			LoadingScreens.spinner(g, cx, y + 40, 22);
			FontRenderer.MEDIUM.drawCentered(g, I18n.get("aurora.accounts.requesting"), cx, y + 76, 8, Ui.c(Theme.TEXT_DIM));
		} else {
			FontRenderer.MEDIUM.drawCentered(g, I18n.get("aurora.accounts.enter_code"), cx, y + 8, 8, Ui.c(Theme.TEXT_DIM));
			String userCode = code.userCode();
			float cw = FontRenderer.BOLD.width(userCode, 20) + 28;
			Render2D.roundRect(g, cx - cw / 2f, y + 18, cw, 34, 10, Ui.c(0xFF0B0D12));
			Render2D.roundOutline(g, cx - cw / 2f, y + 18, cw, 34, 10, 1f, Render2D.Paint.horizontal(cx - cw / 2f, cw,
				Ui.c(Theme.accent()), Ui.c(Theme.accent2())));
			Ui.gradientText(g, FontRenderer.BOLD, userCode, cx - (cw - 28) / 2f, y + 35, 20, Theme.accent(), Theme.accent2());
			FontRenderer.SEMIBOLD.drawCentered(g, code.verificationUri().replaceFirst("^https?://(www\\.)?", ""), cx, y + 64, 8.5f,
				Ui.c(Theme.TEXT));
			float bw = (w - 16) / 3f;
			button(g, mx, my, "ms_open", I18n.get("aurora.accounts.open_page"), x, y + 78, bw, 22, true);
			button(g, mx, my, "ms_copy", I18n.get("aurora.accounts.copy_code"), x + bw + 8, y + 78, bw, 22, false);
			button(g, mx, my, "ms_cancel", I18n.get("gui.cancel"), x + (bw + 8) * 2, y + 78, bw, 22, false);
			float t = (float) (System.nanoTime() / 1e9);
			Render2D.arc(g, cx - 56, y + 118, 4.5f, 1.4f, (t * 360) % 360, 110, Ui.c(Theme.accent()));
			FontRenderer.MEDIUM.drawMiddle(g, I18n.get("aurora.accounts.waiting"), cx - 46, y + 118, 7.5f, Ui.c(Theme.TEXT_DIM));
		}
	}

	private void button(GuiGraphicsExtractor g, int mx, int my, String id, String label, float x, float y, float w, float h, boolean primary) {
		boolean hov = Ui.inside(mx, my, x, y, w, h);
		float hv = hover(id, hov).get();
		if (primary) {
			Render2D.roundRect(g, x, y, w, h, 8, Ui.accentPaint(x, w, 0.75f + 0.25f * hv));
		} else {
			Render2D.roundRect(g, x, y, w, h, 8, Ui.c(ColorUtil.lerp(0xCC161922, 0xE61F2430, hv)));
			Render2D.roundOutline(g, x, y, w, h, 8, 0.7f, Ui.c(ColorUtil.lerp(0x24FFFFFF, 0x47FFFFFF, hv)));
		}
		String text = FontRenderer.SEMIBOLD.ellipsize(label, 8, w - 12);
		FontRenderer.SEMIBOLD.drawCentered(g, text, x + w / 2f, y + h / 2f, 8, Ui.c(primary ? Theme.onAccent() : Theme.TEXT));
		hits.add(new Hit(id, x, y, w, h, null));
	}

	private void closeButton(GuiGraphicsExtractor g, int mx, int my, float cx, float cy) {
		boolean hov = Ui.inside(mx, my, cx - 9, cy - 9, 18, 18);
		float hv = hover("close", hov).get();
		if (hv > 0.01f) {
			Render2D.circle(g, cx, cy, 9, Ui.c(ColorUtil.fade(0xFFFFFFFF, 0.08f * hv)));
		}
		Render2D.cross(g, cx, cy, 7, 1.3f, Ui.c(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv)));
		hits.add(new Hit("close", cx - 9, cy - 9, 18, 18, null));
	}

	// ======================================================================= actions

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (mode == Mode.OFFLINE && nameField.mouseClicked(mx, my)) {
			return true;
		}
		if (mode == Mode.CLIENT_ID && clientField.mouseClicked(mx, my)) {
			return true;
		}
		for (Hit hit : hits) {
			if (!Ui.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
				continue;
			}
			if (event.button() == RIGHT && hit.account() != null && "account".equals(hit.id())) {
				remove(hit.account());
				return true;
			}
			if (event.button() != LEFT) {
				continue;
			}
			activate(hit);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void activate(Hit hit) {
		switch (hit.id()) {
			case "close" -> onClose();
			case "back" -> {
				status = "";
				setMode(Mode.LIST);
			}
			case "account" -> select(hit.account());
			case "remove" -> remove(hit.account());
			case "add_offline" -> {
				status = "";
				nameField.setValue("");
				setMode(Mode.OFFLINE);
			}
			case "offline_add" -> addOffline();
			case "add_microsoft" -> {
				status = "";
				if (AccountManager.clientId().isEmpty()) {
					clientField.setValue("");
					setMode(Mode.CLIENT_ID);
				} else {
					startMicrosoft();
				}
			}
			case "client_save" -> {
				String id = clientField.value().trim();
				if (!id.matches("[0-9a-fA-F-]{20,40}")) {
					status("aurora.accounts.error.client_format", Theme.WARNING);
					return;
				}
				AccountManager.setClientId(id);
				startMicrosoft();
			}
			case "client_guide" -> Blaze3D.openUri(URI.create(AZURE_GUIDE));
			case "ms_open" -> {
				if (code != null) {
					Blaze3D.openUri(URI.create(code.verificationUri() + "?otc=" + code.userCode()));
				}
			}
			case "ms_copy" -> {
				if (code != null) {
					minecraft.keyboardHandler.setClipboard(code.userCode());
					status("aurora.accounts.code_copied", Theme.SUCCESS);
				}
			}
			case "ms_cancel" -> {
				cancelled.set(true);
				code = null;
				status = "";
				setMode(Mode.LIST);
			}
			default -> {
			}
		}
	}

	private void select(@Nullable Account account) {
		if (account == null || account == AccountManager.current() || AccountManager.busy()) {
			return;
		}
		if (minecraft.level != null) {
			status("aurora.accounts.error.in_world", Theme.WARNING);
			return;
		}
		switching = account;
		status("aurora.accounts.switching", Theme.TEXT_DIM, account.name());
		AccountManager.switchTo(account).whenComplete((result, error) -> minecraft.execute(() -> {
			switching = null;
			faces.clear();
			if (error == null) {
				status("aurora.accounts.switched", Theme.SUCCESS, account.name());
				Notifications.push(I18n.get("aurora.accounts.title"), account.name(), Notifications.Kind.INFO);
			} else {
				showError(error);
			}
		}));
	}

	private void remove(@Nullable Account account) {
		if (account == null || account.type() == Account.Type.LAUNCHER || account == AccountManager.current()) {
			return;
		}
		AccountManager.remove(account);
		faces.remove(account);
		status("aurora.accounts.removed", Theme.TEXT_DIM, account.name());
	}

	private void addOffline() {
		String name = nameField.value().trim();
		if (!AccountManager.validName(name)) {
			status("aurora.accounts.error.name", Theme.WARNING);
			return;
		}
		Account account = AccountManager.addOffline(name);
		setMode(Mode.LIST);
		select(account);
	}

	private void startMicrosoft() {
		setMode(Mode.MICROSOFT);
		code = null;
		cancelled.set(false);
		String clientId = AccountManager.clientId();
		CompletableFuture.supplyAsync(() -> {
			try {
				MicrosoftAuth.DeviceCode device = MicrosoftAuth.deviceCode(clientId);
				minecraft.execute(() -> {
					if (!cancelled.get()) {
						code = device;
					}
				});
				MicrosoftAuth.Tokens tokens = MicrosoftAuth.awaitSignIn(clientId, device, cancelled::get);
				MicrosoftAuth.Session session = MicrosoftAuth.minecraft(tokens.accessToken());
				return new Object[]{tokens, session};
			} catch (MicrosoftAuth.AuthException e) {
				throw new CompletionException(e);
			}
		}, Util.nonCriticalIoPool()).whenComplete((result, error) -> minecraft.execute(() -> {
			code = null;
			if (cancelled.get()) {
				return;
			}
			setMode(Mode.LIST);
			if (error != null) {
				showError(error);
				return;
			}
			Account account = AccountManager.addMicrosoft((MicrosoftAuth.Tokens) result[0], (MicrosoftAuth.Session) result[1]);
			select(account);
		}));
	}

	private void showError(Throwable error) {
		Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		if (cause instanceof MicrosoftAuth.AuthException auth) {
			status(auth.key(), Theme.DANGER);
		} else {
			status("aurora.accounts.error.unknown", Theme.DANGER, String.valueOf(cause.getMessage()));
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll -= (float) scrollY * 20;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		TextField field = mode == Mode.OFFLINE ? nameField : mode == Mode.CLIENT_ID ? clientField : null;
		if (field != null) {
			if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
				activate(new Hit(mode == Mode.OFFLINE ? "offline_add" : "client_save", 0, 0, 0, 0, null));
				return true;
			}
			if (event.isEscape()) {
				status = "";
				setMode(Mode.LIST);
				return true;
			}
			if (field.keyPressed(event)) {
				return true;
			}
		}
		if (event.isEscape() && mode != Mode.LIST) {
			activate(new Hit(mode == Mode.MICROSOFT ? "ms_cancel" : "back", 0, 0, 0, 0, null));
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (mode == Mode.OFFLINE) {
			return nameField.charTyped(event);
		}
		if (mode == Mode.CLIENT_ID) {
			return clientField.charTyped(event);
		}
		return false;
	}

	@Override
	public void onClose() {
		cancelled.set(true);
		minecraft.textInputManager().stopTextInput(nameField);
		minecraft.textInputManager().stopTextInput(clientField);
		minecraft.gui.setScreen(parent);
	}
}
