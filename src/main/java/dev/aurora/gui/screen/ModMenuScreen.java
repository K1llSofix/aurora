package dev.aurora.gui.screen;

import dev.aurora.Aurora;
import dev.aurora.config.Profiles;
import dev.aurora.gui.Notifications;
import dev.aurora.gui.Theme;
import dev.aurora.gui.background.MenuBackground;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.gui.widget.BindRow;
import dev.aurora.gui.widget.CommandBindRow;
import dev.aurora.gui.widget.CrosshairPreview;
import dev.aurora.gui.widget.ProfileRow;
import dev.aurora.gui.widget.SettingRow;
import dev.aurora.gui.widget.Switch;
import dev.aurora.gui.widget.TextField;
import dev.aurora.gui.widget.Ui;
import dev.aurora.module.Category;
import dev.aurora.module.ClientSettings;
import dev.aurora.module.HudModule;
import dev.aurora.module.Module;
import dev.aurora.module.performance.PerformanceModule;
import dev.aurora.module.utility.CommandBindsModule;
import dev.aurora.module.utility.ScreenshotsModule;
import dev.aurora.module.visual.CrosshairModule;
import dev.aurora.setting.Setting;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.Easing;
import dev.aurora.util.Motion;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Aurora mod menu: a sidebar with categories, a searchable grid of module cards and a
 * settings page per module. Everything is drawn with vector shapes and animated.
 */
public final class ModMenuScreen extends Screen {
	private static @Nullable Category rememberedCategory;
	private static String rememberedSearch = "";

	private static final float SIDEBAR_W = 118;
	private static final float HEADER_H = 46;
	private static final float CARD_H = 78;
	private static final float GAP = 8;
	private float navH = 24;
	private static final int LEFT = 1;
	private static final int RIGHT = 3;

	private final @Nullable Screen parent;
	private final long openedAt = System.currentTimeMillis();
	private long closingAt = -1;
	private @Nullable Screen closeTarget;

	// Layout (recomputed in init)
	private float px, py, pw, ph;
	private float cx, cy, cw, ch;

	// Navigation
	private @Nullable Category category = rememberedCategory;
	private boolean settingsPageOfClient;
	private final Animation navIndicator = new Animation(-1, 16);
	private final Map<Object, Animation> hovers = new HashMap<>();

	// Grid
	private final TextField search = new TextField("", 32);
	private List<Module> shown = List.of();
	private long listChangedAt = System.currentTimeMillis();
	private final Map<Module, Switch> switches = new HashMap<>();
	private final Animation gridScroll = new Animation(0, 16);
	private float gridScrollTarget;
	private float gridContentHeight;
	private final List<Card> cards = new ArrayList<>();

	private record Card(Module module, float x, float y, float w, float h) {
	}

	// Settings page
	private @Nullable Module selected;
	private @Nullable Module pageModule;
	private final Animation page = new Animation(0, 13);
	private List<SettingRow> rows = List.of();
	private final Animation settingsScroll = new Animation(0, 16);
	private float settingsScrollTarget;
	private float settingsContentHeight;
	/** The settings list, which shares the page with a module's live preview. */
	private float pageListX, pageListY, pageListW, pageListH;
	private @Nullable CrosshairPreview preview;
	private @Nullable Switch pageSwitch;

	// Binds page
	private static boolean rememberedBinds;
	private boolean bindsPage = rememberedBinds;
	private List<SettingRow> bindRows = List.of();
	private final Animation bindsScroll = new Animation(0, 16);
	private float bindsScrollTarget;
	private float bindsContentHeight;
	private long bindsChangedAt = System.currentTimeMillis();
	private int bindCount;

	// The Profiles page shares the list machinery (rows, scroll, typing) with the Binds page.
	private enum ListKind { BINDS, PROFILES }

	private static ListKind rememberedList = ListKind.BINDS;
	private ListKind listKind = rememberedList;
	private float saveProfileX, saveProfileW, pasteProfileX, pasteProfileW, undoProfileX, profileButtonsY;

	public ModMenuScreen(@Nullable Screen parent) {
		this(parent, null);
	}

	public ModMenuScreen(@Nullable Screen parent, @Nullable Module openSettingsOf) {
		super(Component.translatable("aurora.menu.title"));
		this.parent = parent;
		search.setValue(rememberedSearch);
		search.onChange(this::refreshList);
		if (openSettingsOf != null) {
			openSettings(openSettingsOf);
			page.snap(1);
		}
	}

	@Override
	protected void init() {
		pw = Math.min(width - 32, 600);
		ph = Math.min(height - 32, 360);
		pw = Math.max(pw, Math.min(width - 8, 380));
		ph = Math.max(ph, Math.min(height - 8, 240));
		px = (width - pw) / 2f;
		py = (height - ph) / 2f;
		cx = px + SIDEBAR_W + 16;
		cy = py + HEADER_H + 4;
		cw = px + pw - 16 - cx;
		ch = py + ph - 12 - cy;
		// Five categories above and three pages below (plus the HUD button) must fit on short windows.
		navH = Math.max(13, Math.min(24, (ph - 114) / 8f - 2));
		refreshList();
		updateTextInput();
	}

	/**
	 * SDL only delivers typed characters while text input is started. The grid page keeps it on so
	 * typing anywhere searches; the settings page has no text fields and turns it off.
	 */
	private void updateTextInput() {
		if (selected == null) {
			minecraft.textInputManager().startTextInput(search);
		} else {
			minecraft.textInputManager().stopTextInput(search);
		}
	}

	// ======================================================================= state

	private void refreshList() {
		String q = search.value().trim().toLowerCase(Locale.ROOT);
		List<Module> list = new ArrayList<>();
		for (Module m : Aurora.modules().listed()) {
			if (category != null && m.category() != category) {
				continue;
			}
			if (!q.isEmpty() && !m.name().toLowerCase(Locale.ROOT).contains(q)
				&& !m.description().toLowerCase(Locale.ROOT).contains(q) && !m.id().contains(q)) {
				continue;
			}
			list.add(m);
		}
		shown = list;
		listChangedAt = System.currentTimeMillis();
		gridScrollTarget = 0;
		gridScroll.snap(0);
		if (bindsPage) {
			rebuildListPage();
		}
	}

	private void rebuildListPage() {
		if (listKind == ListKind.PROFILES) {
			buildProfileRows();
		} else {
			buildBindRows();
		}
	}

	private boolean profilesPage() {
		return bindsPage && listKind == ListKind.PROFILES;
	}

	private void selectCategory(@Nullable Category c) {
		boolean changed = c != category || settingsPageOfClient || selected != null || bindsPage;
		category = c;
		rememberedCategory = c;
		settingsPageOfClient = false;
		bindsPage = false;
		rememberedBinds = false;
		closeSettings();
		if (changed) {
			refreshList();
		}
	}

	private void openBinds() {
		closeSettings();
		settingsPageOfClient = false;
		bindsPage = true;
		rememberedBinds = true;
		listKind = ListKind.BINDS;
		rememberedList = listKind;
		buildBindRows();
	}

	private void openProfiles() {
		closeSettings();
		settingsPageOfClient = false;
		bindsPage = true;
		rememberedBinds = true;
		listKind = ListKind.PROFILES;
		rememberedList = listKind;
		search.setFocused(false);
		buildProfileRows();
		bindsScrollTarget = 0;
		bindsScroll.snap(0);
		bindsChangedAt = System.currentTimeMillis();
	}

	// ======================================================================= profiles

	private final ProfileRow.Actions profileActions = new ProfileRow.Actions() {
		@Override
		public void load(Profiles.Profile profile) {
			Profiles.apply(profile);
			Notifications.push(I18n.get("aurora.profiles.loaded"), profile.name(), Notifications.Kind.ENABLED);
			refreshProfiles();
		}

		@Override
		public void overwrite(Profiles.Profile profile) {
			Profiles.overwrite(profile);
			Notifications.push(I18n.get("aurora.profiles.overwritten"), profile.name(), Notifications.Kind.INFO);
			refreshProfiles();
		}

		@Override
		public void copy(Profiles.Profile profile) {
			minecraft.keyboardHandler.setClipboard(Profiles.exportCode(profile));
			Notifications.push(I18n.get("aurora.profiles.copied"), I18n.get("aurora.profiles.copied.hint"), Notifications.Kind.INFO);
		}

		@Override
		public void delete(Profiles.Profile profile) {
			Profiles.delete(profile);
			Notifications.push(I18n.get("aurora.profiles.deleted"), profile.name(), Notifications.Kind.DISABLED);
			refreshProfiles();
		}
	};

	/** Built-in presets, then the player's own profiles. */
	private void buildProfileRows() {
		String active = Profiles.active();
		List<SettingRow> list = new ArrayList<>();
		List<Profiles.Profile> builtins = Profiles.builtins();
		List<Profiles.Profile> saved = Profiles.saved();
		list.add(new SettingRow.Header(I18n.get("aurora.profiles.yours")));
		for (Profiles.Profile p : saved) {
			list.add(new ProfileRow(p, p.id().equals(active), profileActions));
		}
		if (saved.isEmpty()) {
			list.add(new SettingRow.Action(I18n.get("aurora.profiles.empty"), I18n.get("aurora.profiles.save"), this::saveProfile));
		}
		list.add(new SettingRow.Header(I18n.get("aurora.profiles.presets")));
		for (Profiles.Profile p : builtins) {
			list.add(new ProfileRow(p, p.id().equals(active), profileActions));
		}
		bindRows = list;
		bindCount = builtins.size() + saved.size();
	}

	private void refreshProfiles() {
		float keep = bindsScrollTarget;
		buildProfileRows();
		bindsScrollTarget = keep;
		bindsScroll.snap(keep);
	}

	private void saveProfile() {
		Profiles.Profile profile = Profiles.saveCurrent(I18n.get("aurora.profiles.new_name", Profiles.saved().size() + 1));
		refreshProfiles();
		for (SettingRow row : bindRows) {
			if (row instanceof ProfileRow profileRow && profileRow.profile().id().equals(profile.id())) {
				profileRow.focusName();
			}
		}
		Notifications.push(I18n.get("aurora.profiles.created"), profile.name(), Notifications.Kind.ENABLED);
	}

	private void pasteProfile() {
		Profiles.Profile profile = Profiles.importCode(minecraft.keyboardHandler.getClipboard());
		if (profile == null) {
			Notifications.push(I18n.get("aurora.profiles.bad_code"), I18n.get("aurora.profiles.bad_code.hint"), Notifications.Kind.DISABLED);
			return;
		}
		refreshProfiles();
		Notifications.push(I18n.get("aurora.profiles.imported_title"), profile.name(), Notifications.Kind.ENABLED);
	}

	private void undoProfile() {
		if (Profiles.undo()) {
			Notifications.push(I18n.get("aurora.profiles.undone"), I18n.get("aurora.profiles.undone.hint"), Notifications.Kind.INFO);
			refreshProfiles();
		}
	}

	/** Aurora's own key mappings first, then every module that can be toggled, by category. */
	private void buildBindRows() {
		String q = search.value().trim().toLowerCase(Locale.ROOT);
		List<BindRow.Bind> all = new ArrayList<>();
		List<SettingRow> list = new ArrayList<>();
		List<BindRow.Bind> controls = List.of(BindRow.of(Aurora.menuKey), BindRow.of(Aurora.zoomKey), BindRow.of(Aurora.freelookKey));
		all.addAll(controls);
		Map<Category, List<BindRow.Bind>> byCategory = new java.util.LinkedHashMap<>();
		for (Category c : new Category[]{Category.HUD, Category.VISUAL, Category.UTILITY, Category.PERFORMANCE}) {
			byCategory.put(c, new ArrayList<>());
		}
		for (Module m : Aurora.modules().listed()) {
			if (m.canToggle() && byCategory.containsKey(m.category())) {
				BindRow.Bind b = BindRow.of(m);
				byCategory.get(m.category()).add(b);
				all.add(b);
			}
		}
		int count = 0;
		count += addBindSection(list, I18n.get("aurora.ui.binds.controls"), controls, all, q);
		count += addCommandSection(list, all, q);
		for (Map.Entry<Category, List<BindRow.Bind>> e : byCategory.entrySet()) {
			count += addBindSection(list, I18n.get(e.getKey().translationKey()), e.getValue(), all, q);
		}
		bindRows = list;
		bindCount = count;
		bindsScrollTarget = 0;
		bindsScroll.snap(0);
		bindsChangedAt = System.currentTimeMillis();
	}

	/** Keys that send a command; always listed (with the "add" row) unless a search hides them. */
	private int addCommandSection(List<SettingRow> list, List<BindRow.Bind> all, String query) {
		CommandBindsModule commands = Aurora.modules().get(CommandBindsModule.class);
		List<SettingRow> section = new ArrayList<>();
		Map<CommandBindsModule.CommandBind, BindRow.Bind> asBinds = new java.util.IdentityHashMap<>();
		for (CommandBindsModule.CommandBind bind : commands.binds()) {
			BindRow.Bind generic = CommandBindRow.bindOf(bind);
			asBinds.put(bind, generic);
			all.add(generic);
		}
		for (CommandBindsModule.CommandBind bind : commands.binds()) {
			if (query.isEmpty() || bind.command().toLowerCase(Locale.ROOT).contains(query)) {
				section.add(new CommandBindRow(bind, asBinds.get(bind), all, () -> {
					commands.remove(bind);
					Aurora.config().markDirty();
					rebuildBindRowsKeepingScroll();
				}));
			}
		}
		if (!query.isEmpty() && section.isEmpty()) {
			return 0;
		}
		list.add(new SettingRow.Header(I18n.get("aurora.ui.binds.commands")));
		list.addAll(section);
		list.add(new SettingRow.Action(I18n.get(commands.binds().isEmpty() ? "aurora.ui.binds.command_hint" : "aurora.ui.binds.command_more"),
			I18n.get("aurora.ui.binds.command_add"), this::addCommandBind));
		return section.size();
	}

	private void addCommandBind() {
		CommandBindsModule commands = Aurora.modules().get(CommandBindsModule.class);
		CommandBindsModule.CommandBind bind = commands.add();
		Aurora.config().markDirty();
		rebuildBindRowsKeepingScroll();
		for (SettingRow row : bindRows) {
			if (row instanceof CommandBindRow commandRow && commandRow.command() == bind) {
				commandRow.focusField();
			}
		}
	}

	private void rebuildBindRowsKeepingScroll() {
		float keep = bindsScrollTarget;
		buildBindRows();
		bindsScrollTarget = keep;
		bindsScroll.snap(keep);
	}

	/** True while a text field inside a bind row has focus. */
	private boolean typingInRow() {
		if (bindsPage && selected == null) {
			for (SettingRow row : bindRows) {
				if (row.typing()) {
					return true;
				}
			}
		}
		return false;
	}

	private int addBindSection(List<SettingRow> list, String title, List<BindRow.Bind> binds, List<BindRow.Bind> all, String query) {
		List<SettingRow> rowsOfSection = new ArrayList<>();
		for (BindRow.Bind b : binds) {
			if (query.isEmpty() || b.name().toLowerCase(Locale.ROOT).contains(query)) {
				rowsOfSection.add(new BindRow(b, all));
			}
		}
		if (!rowsOfSection.isEmpty()) {
			list.add(new SettingRow.Header(title));
			list.addAll(rowsOfSection);
		}
		return rowsOfSection.size();
	}

	private void resetAllBinds() {
		for (SettingRow row : bindRows) {
			if (row instanceof BindRow bindRow) {
				bindRow.bind().reset();
			}
		}
		Aurora.config().markDirty();
	}

	private void openSettings(Module module) {
		selected = module;
		pageModule = module;
		settingsPageOfClient = module.category() == Category.CLIENT;
		if (settingsPageOfClient) {
			bindsPage = false;
			rememberedBinds = false;
		}
		settingsScrollTarget = 0;
		settingsScroll.snap(0);
		pageSwitch = new Switch(module.isEnabled());
		rows = buildRows(module);
		preview = module instanceof CrosshairModule crosshair ? new CrosshairPreview(crosshair) : null;
		search.setFocused(false);
		if (width > 0) {
			updateTextInput();
		}
	}

	private void closeSettings() {
		boolean wasOpen = selected != null;
		selected = null;
		settingsPageOfClient = false;
		closeOverlays();
		if (wasOpen && width > 0) {
			updateTextInput();
		}
	}

	private List<SettingRow> buildRows(Module module) {
		List<SettingRow> list = new ArrayList<>();
		Runnable changed = () -> Aurora.config().markDirty();
		if (module instanceof HudModule hud) {
			list.add(new SettingRow.Header(I18n.get("aurora.ui.section.layout")));
			list.add(new SettingRow.Action(I18n.get("aurora.ui.position"), I18n.get("aurora.ui.edit_hud"),
				() -> openHudEditor(hud)));
			list.add(new HudScaleRow(hud, changed));
		}
		if (module instanceof PerformanceModule performance) {
			// One click sets every video option for the kind of computer.
			list.add(new SettingRow.Header(I18n.get("aurora.performance.presets")));
			for (String preset : new String[]{"potato", "balanced", "quality"}) {
				list.add(new SettingRow.Action(I18n.get("aurora.performance.preset." + preset + ".label"), I18n.get("aurora.performance.apply"), () -> {
					performance.applyPreset(preset);
					openSettings(performance);
				}));
			}
		}
		if (!module.settings().isEmpty()) {
			list.add(new SettingRow.Header(I18n.get(module instanceof ClientSettings ? "aurora.ui.section.appearance" : "aurora.ui.section.settings")));
			for (Setting<?> s : module.settings()) {
				list.add(SettingRow.of(s, changed));
				if (module instanceof ClientSettings client && s == client.menuBackground) {
					list.add(new SettingRow.Action(I18n.get("aurora.ui.backgrounds_folder"), I18n.get("aurora.ui.open_folder"),
						MenuBackground::openFolder).visibleWhen(() -> client.menuBackground.is("image")));
				}
			}
		}
		if (module instanceof ScreenshotsModule) {
			list.add(new SettingRow.Action(I18n.get("aurora.screenshot.folder_row"), I18n.get("aurora.ui.open_folder"), ScreenshotsModule::openFolder));
		}
		if (module instanceof CommandBindsModule) {
			list.add(new SettingRow.Action(I18n.get("aurora.ui.binds.commands"), I18n.get("aurora.ui.binds.open"), () -> {
				closeSettings();
				openBinds();
			}));
		}
		if (module.canToggle()) {
			list.add(new SettingRow.Header(I18n.get("aurora.ui.section.controls")));
			list.add(new SettingRow.Keybind(I18n.get("aurora.ui.toggle_key"), module.keybind()::get, module.keybind()::set, changed));
		}
		return list;
	}

	/** Slider row bound to a HUD element's scale rather than to a Setting. */
	private static final class HudScaleRow extends SettingRow {
		private final dev.aurora.setting.NumberSetting proxy;
		private final SettingRow inner;
		private final HudModule hud;

		HudScaleRow(HudModule hud, Runnable changed) {
			super(I18n.get("aurora.ui.scale"), changed);
			this.hud = hud;
			this.proxy = new dev.aurora.setting.NumberSetting("scale", hud.scale() * 100, 50, 300, 5).suffix("%");
			this.proxy.bindTranslationPrefix("aurora.ui");
			this.proxy.onChange(v -> hud.setScale((float) (v / 100.0)));
			this.inner = SettingRow.of(proxy, changed);
		}

		@Override
		public void render(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w) {
			if (Math.abs(proxy.get() - hud.scale() * 100) > 0.5) {
				proxy.set((double) Math.round(hud.scale() * 100));
			}
			inner.render(g, mx, my, x, y, w);
		}

		@Override
		protected void renderControl(GuiGraphicsExtractor g, int mx, int my) {
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			return inner.mouseClicked(mx, my, button);
		}

		@Override
		public boolean mouseDragged(double mx, double my) {
			return inner.mouseDragged(mx, my);
		}

		@Override
		public void mouseReleased() {
			inner.mouseReleased();
		}
	}

	private void openHudEditor(@Nullable HudModule focus) {
		Aurora.config().save();
		minecraft.gui.setScreen(new HudEditorScreen(this, focus));
	}

	private void closeOverlays() {
		for (SettingRow row : rows) {
			row.closeOverlay();
		}
	}

	private @Nullable SettingRow openOverlay() {
		if (selected == null) {
			return null;
		}
		for (SettingRow row : rows) {
			if (row.overlayOpen()) {
				return row;
			}
		}
		return null;
	}

	private Animation hover(Object key, boolean on) {
		return hovers.computeIfAbsent(key, k -> new Animation(0, 16)).target(on);
	}

	private float visibility() {
		float open = Easing.OUT_EXPO.clamped(Motion.progress(openedAt, 420));
		if (closingAt < 0) {
			return open;
		}
		float close = Easing.IN_OUT_CUBIC.clamped(Motion.progress(closingAt, 170));
		return open * (1 - close);
	}

	private boolean interactive() {
		return closingAt < 0 && Motion.progress(openedAt, 420) > 0.5f;
	}

	// ======================================================================= rendering

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		float vis = visibility();
		if (minecraft.level == null) {
			extractPanorama(g, a);
		}
		if (Aurora.client().menuBlur.on()) {
			try {
				g.blurBeforeThisStratum();
			} catch (IllegalStateException ignored) {
				// Something else already blurred this frame.
			}
		}
		Render2D.verticalGradient(g, 0, 0, width, height, ColorUtil.fade(Theme.OVERLAY_TOP, vis), ColorUtil.fade(Theme.OVERLAY_BOTTOM, vis));
		// Ambient aurora glow behind the panel.
		float t = (float) (System.nanoTime() / 1e9);
		float gx = px + pw * (0.2f + 0.08f * (float) Math.sin(t * 0.4));
		float gy = py + ph * 0.15f;
		Render2D.blob(g, gx - 60, gy - 30, 120, 60, 30, 110, ColorUtil.fade(Theme.accent(), 0.09f * vis));
		float gx2 = px + pw * (0.8f + 0.06f * (float) Math.cos(t * 0.35));
		float gy2 = py + ph * 0.85f;
		Render2D.blob(g, gx2 - 60, gy2 - 30, 120, 60, 30, 110, ColorUtil.fade(Theme.accent2(), 0.09f * vis));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		float vis = visibility();
		Ui.alpha = vis;
		int mx = interactive() ? mouseX : -10000;
		int my = interactive() ? mouseY : -10000;
		SettingRow overlay = openOverlay();
		SettingRow.blocked = overlay != null;
		int hx = overlay != null ? -10000 : mx;
		int hy = overlay != null ? -10000 : my;

		g.pose().pushMatrix();
		float scale = 0.94f + 0.06f * vis;
		g.pose().translate(width / 2f, height / 2f + (1 - vis) * 10);
		g.pose().scale(scale, scale);
		g.pose().translate(-width / 2f, -height / 2f);

		drawPanel(g);
		drawSidebar(g, hx, hy);

		float p = page.target(selected != null ? 1 : 0).get();
		if (selected == null && p < 0.01f) {
			pageModule = null;
		}
		float gridAlpha = 1 - p;
		if (gridAlpha > 0.01f) {
			Ui.alpha = vis * gridAlpha;
			g.pose().pushMatrix();
			g.pose().translate(-24 * p, 0);
			if (bindsPage) {
				float in = Easing.OUT_CUBIC.clamped(Motion.progress(bindsChangedAt, 260));
				Ui.alpha = vis * gridAlpha * in;
				g.pose().translate(0, (1 - in) * 8);
				drawBindsHeader(g, hx, hy);
				drawBinds(g, p < 0.5f ? hx : -10000, p < 0.5f ? hy : -10000);
			} else {
				drawGridHeader(g, hx, hy);
				drawGrid(g, p < 0.5f ? hx : -10000, p < 0.5f ? hy : -10000);
			}
			g.pose().popMatrix();
		}
		if (p > 0.01f && pageModule != null) {
			Ui.alpha = vis * p;
			g.pose().pushMatrix();
			g.pose().translate(24 * (1 - p), 0);
			drawSettingsPage(g, pageModule, p > 0.5f ? hx : -10000, p > 0.5f ? hy : -10000);
			g.pose().popMatrix();
		}
		g.pose().popMatrix();

		if (overlay != null) {
			g.nextStratum();
			Ui.alpha = vis;
			overlay.renderOverlay(g, mx, my);
		}
		Ui.alpha = 1f;
		SettingRow.blocked = false;
	}

	private void drawPanel(GuiGraphicsExtractor g) {
		Render2D.shadow(g, px, py, pw, ph, 14, 34, Ui.c(0xA0000000));
		Render2D.roundRect(g, px, py, pw, ph, 14, Render2D.Paint.vertical(py, ph, Ui.c(Theme.PANEL_TOP), Ui.c(Theme.PANEL)));
		// Sidebar
		Render2D.roundRect(g, px, py, SIDEBAR_W, ph, 14, 0, 0, 14, Render2D.Paint.solid(Ui.c(Theme.SIDEBAR)));
		Render2D.rect(g, px + SIDEBAR_W - 0.5f, py + 12, 0.5f, ph - 24, Ui.c(Theme.DIVIDER));
		// Thin accent line along the top edge
		Render2D.roundRect(g, px + 30, py, pw - 60, 1.2f, 0.6f, Render2D.Paint.horizontal(px + 30, pw - 60,
			Ui.c(ColorUtil.fade(Theme.accent(), 0)), Ui.c(ColorUtil.fade(Theme.accent(0.5f), 0.9f))));
		Render2D.roundOutline(g, px, py, pw, ph, 14, 0.8f, Ui.c(Theme.BORDER));
	}

	private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
		float x = px + 12;
		float w = SIDEBAR_W - 24;
		// Logo
		Ui.logo(g, x + 9, py + 22, 18);
		Ui.gradientText(g, FontRenderer.BOLD, "Aurora", x + 24, py + 19, 12, Theme.accent(), Theme.accent2());
		FontRenderer.SEMIBOLD.drawMiddle(g, "VISUALS", x + 24.5f, py + 29.5f, 5.5f, Ui.c(Theme.TEXT_MUTED));

		float y = py + 52;
		FontRenderer.SEMIBOLD.drawMiddle(g, I18n.get("aurora.ui.browse").toUpperCase(Locale.ROOT), x + 4, y, 6, Ui.c(Theme.TEXT_MUTED));
		y += 8;

		Object[] items = {null, Category.HUD, Category.VISUAL, Category.UTILITY, Category.PERFORMANCE};
		float activeY = -1;
		for (Object item : items) {
			Category c = (Category) item;
			boolean active = !settingsPageOfClient && !bindsPage && category == c;
			if (active) {
				activeY = y;
			}
			navItem(g, mx, my, c == null ? "all" : c.id(), c == null ? I18n.get("aurora.category.all") : I18n.get(c.translationKey()),
				x, y, w, active, count(c));
			y += navH + 2;
		}
		// Bottom block: profiles, binds and settings sit together above the HUD editor button.
		float by = py + ph - 12 - 26;
		float sy = by - 8 - navH;
		float bindsY = sy - 2 - navH;
		float profilesY = bindsY - 2 - navH;
		boolean profilesActive = profilesPage() && !settingsPageOfClient;
		boolean bindsActive = bindsPage && listKind == ListKind.BINDS && !settingsPageOfClient;
		if (profilesActive) {
			activeY = profilesY;
		}
		if (bindsActive) {
			activeY = bindsY;
		}
		if (settingsPageOfClient) {
			activeY = sy;
		}
		navItem(g, mx, my, "profiles", I18n.get("aurora.category.profiles"), x, profilesY, w, profilesActive, -1);
		navItem(g, mx, my, "binds", I18n.get("aurora.category.binds"), x, bindsY, w, bindsActive, -1);
		navItem(g, mx, my, "settings", I18n.get("aurora.category.client"), x, sy, w, settingsPageOfClient, -1);

		// Active indicator: a pill that glides between items.
		if (activeY >= 0) {
			if (navIndicator.peek() < 0) {
				navIndicator.snap(activeY);
			}
			float iy = navIndicator.target(activeY).get();
			Render2D.roundRect(g, x - 12, iy + 6, 2.5f, navH - 12, 1.25f,
				Render2D.Paint.vertical(iy + 6, navH - 12, Ui.c(Theme.accent()), Ui.c(Theme.accent2())));
		}

		boolean hov = Ui.inside(mx, my, x, by, w, 26);
		float h = hover("edit", hov).get();
		Render2D.glow(g, x, by, w, 26, 8, 10, Ui.c(ColorUtil.fade(Theme.accent(0.5f), 0.18f + 0.18f * h)));
		Render2D.roundRect(g, x, by, w, 26, 8, Render2D.Paint.horizontal(x, w,
			Ui.c(ColorUtil.shade(Theme.accent(), h * 0.12f)), Ui.c(ColorUtil.shade(Theme.accent2(), h * 0.12f))));
		Ui.iconMove(g, x + 15, by + 13, 9, Theme.onAccent());
		FontRenderer.SEMIBOLD.drawMiddle(g, I18n.get("aurora.ui.edit_hud"), x + 26, by + 13, 8, Ui.c(Theme.onAccent()));

		String version = "v" + FabricLoader.getInstance().getModContainer(Aurora.MOD_ID)
			.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?").split("\\+")[0];
		FontRenderer.MEDIUM.drawMiddle(g, version, x + 2, py + ph - 6, 5.5f, Ui.c(0xFF4A5060));
	}

	private int count(@Nullable Category c) {
		int n = 0;
		for (Module m : Aurora.modules().listed()) {
			if (c == null || m.category() == c) {
				n++;
			}
		}
		return n;
	}

	private void navItem(GuiGraphicsExtractor g, int mx, int my, String key, String label, float x, float y, float w, boolean active, int count) {
		boolean hov = Ui.inside(mx, my, x, y, w, navH);
		float h = hover("nav:" + key, hov || active).get();
		if (active) {
			Render2D.roundRect(g, x, y, w, navH, 7, Ui.accentPaint(x, w, 0.16f));
		} else if (h > 0.01f) {
			Render2D.roundRect(g, x, y, w, navH, 7, Ui.c(ColorUtil.withAlpha(0xFFFFFF, Math.round(10 * h))));
		}
		int color = active ? Theme.TEXT : ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, h);
		int iconColor = active ? Theme.accent() : color;
		float icx = x + 13;
		float icy = y + navH / 2f;
		switch (key) {
			case "all" -> Ui.iconGrid(g, icx, icy, 9, iconColor);
			case "hud" -> Ui.iconHud(g, icx, icy, 10, iconColor);
			case "visual" -> Ui.iconEye(g, icx, icy, 10, iconColor);
			case "utility" -> Ui.iconBolt(g, icx, icy, 10, iconColor);
			case "binds" -> Ui.iconKeyboard(g, icx, icy, 11, iconColor);
			case "profiles" -> Ui.iconLayers(g, icx, icy, 10, iconColor);
			case "performance" -> Ui.iconGauge(g, icx, icy, 10, iconColor);
			default -> Ui.iconGear(g, icx, icy, 10, iconColor);
		}
		FontRenderer.MEDIUM.drawMiddle(g, label, x + 25, icy, 8, Ui.c(color));
		if (count >= 0) {
			String n = Integer.toString(count);
			float nw = FontRenderer.SEMIBOLD.width(n, 6.5f) + 8;
			Render2D.roundRect(g, x + w - 6 - nw, icy - 5, nw, 10, 5, Ui.c(active ? ColorUtil.fade(Theme.accent(), 0.25f) : 0x14FFFFFF));
			FontRenderer.SEMIBOLD.drawCentered(g, n, x + w - 6 - nw / 2f, icy, 6.5f, Ui.c(active ? Theme.TEXT : Theme.TEXT_MUTED));
		}
	}

	private void drawGridHeader(GuiGraphicsExtractor g, int mx, int my) {
		float hy = py + HEADER_H / 2f + 2;
		String title = category == null ? I18n.get("aurora.ui.all_mods") : I18n.get(category.translationKey());
		float tw = FontRenderer.BOLD.width(title, 14);
		FontRenderer.BOLD.drawMiddle(g, title, cx, hy, 14, Ui.c(Theme.TEXT));
		String n = Integer.toString(shown.size());
		float nw = FontRenderer.SEMIBOLD.width(n, 7) + 10;
		Render2D.roundRect(g, cx + tw + 7, hy - 6, nw, 12, 6, Ui.accentPaint(cx + tw + 7, nw, 0.2f));
		FontRenderer.SEMIBOLD.drawCentered(g, n, cx + tw + 7 + nw / 2f, hy, 7, Ui.c(Theme.TEXT));

		search.w = Math.min(170, cw * 0.42f);
		search.h = 22;
		search.x = cx + cw - search.w;
		search.y = hy - search.h / 2f;
		String placeholder = I18n.get("aurora.ui.search");
		renderSearch(g, mx, my, placeholder);
	}

	private float resetBindsX, resetBindsY, resetBindsW;

	private void drawBindsHeader(GuiGraphicsExtractor g, int mx, int my) {
		if (listKind == ListKind.PROFILES) {
			drawProfilesHeader(g, mx, my);
			return;
		}
		float hy = py + HEADER_H / 2f + 2;
		String title = I18n.get("aurora.category.binds");
		float tw = FontRenderer.BOLD.width(title, 14);
		FontRenderer.BOLD.drawMiddle(g, title, cx, hy, 14, Ui.c(Theme.TEXT));
		String n = Integer.toString(bindCount);
		float nw = FontRenderer.SEMIBOLD.width(n, 7) + 10;
		Render2D.roundRect(g, cx + tw + 7, hy - 6, nw, 12, 6, Ui.accentPaint(cx + tw + 7, nw, 0.2f));
		FontRenderer.SEMIBOLD.drawCentered(g, n, cx + tw + 7 + nw / 2f, hy, 7, Ui.c(Theme.TEXT));

		search.w = Math.min(150, cw * 0.36f);
		search.h = 22;
		search.x = cx + cw - search.w;
		search.y = hy - search.h / 2f;
		renderSearch(g, mx, my, I18n.get("aurora.ui.binds.search"));

		// "Reset all" button left of the search box.
		String label = I18n.get("aurora.ui.binds.reset_all");
		resetBindsW = FontRenderer.SEMIBOLD.width(label, 7.5f) + 26;
		resetBindsX = search.x - 8 - resetBindsW;
		resetBindsY = hy - 11;
		boolean hov = Ui.inside(mx, my, resetBindsX, resetBindsY, resetBindsW, 22);
		float h = hover("resetBinds", hov).get();
		Render2D.roundRect(g, resetBindsX, resetBindsY, resetBindsW, 22, 11, Ui.c(ColorUtil.lerp(Theme.ELEVATED, 0xFF2A2F3B, h)));
		Ui.iconReset(g, resetBindsX + 11, hy, 8, ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, h));
		FontRenderer.SEMIBOLD.drawMiddle(g, label, resetBindsX + 19, hy, 7.5f, Ui.c(ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, h)));
	}

	/** Title, count and the save / paste code / undo buttons of the Profiles page. */
	private void drawProfilesHeader(GuiGraphicsExtractor g, int mx, int my) {
		float hy = py + HEADER_H / 2f + 2;
		String title = I18n.get("aurora.category.profiles");
		float tw = FontRenderer.BOLD.width(title, 14);
		FontRenderer.BOLD.drawMiddle(g, title, cx, hy, 14, Ui.c(Theme.TEXT));
		String n = Integer.toString(bindCount);
		float nw = FontRenderer.SEMIBOLD.width(n, 7) + 10;
		Render2D.roundRect(g, cx + tw + 7, hy - 6, nw, 12, 6, Ui.accentPaint(cx + tw + 7, nw, 0.2f));
		FontRenderer.SEMIBOLD.drawCentered(g, n, cx + tw + 7 + nw / 2f, hy, 7, Ui.c(Theme.TEXT));

		profileButtonsY = hy - 11;
		float right = cx + cw;
		if (Profiles.canUndo()) {
			undoProfileX = right - 22;
			boolean hov = Ui.inside(mx, my, undoProfileX, profileButtonsY, 22, 22);
			float h = hover("undoProfile", hov).get();
			Render2D.roundRect(g, undoProfileX, profileButtonsY, 22, 22, 11, Ui.c(ColorUtil.lerp(Theme.ELEVATED, 0xFF2A2F3B, h)));
			Ui.iconReset(g, undoProfileX + 11, hy, 9, ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, h));
			if (hov) {
				g.setTooltipForNextFrame(Component.translatable("aurora.profiles.undo"), mx, my);
			}
			right = undoProfileX - 6;
		} else {
			undoProfileX = -10000;
		}
		String paste = I18n.get("aurora.profiles.paste");
		pasteProfileW = FontRenderer.SEMIBOLD.width(paste, 7.5f) + 28;
		pasteProfileX = right - pasteProfileW;
		boolean pasteHov = Ui.inside(mx, my, pasteProfileX, profileButtonsY, pasteProfileW, 22);
		float ph2 = hover("pasteProfile", pasteHov).get();
		Render2D.roundRect(g, pasteProfileX, profileButtonsY, pasteProfileW, 22, 11, Ui.c(ColorUtil.lerp(Theme.ELEVATED, 0xFF2A2F3B, ph2)));
		Ui.iconCopy(g, pasteProfileX + 12, hy, 9, ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, ph2));
		FontRenderer.SEMIBOLD.drawMiddle(g, paste, pasteProfileX + 21, hy, 7.5f, Ui.c(ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, ph2)));
		if (pasteHov) {
			g.setTooltipForNextFrame(Component.translatable("aurora.profiles.paste.tooltip"), mx, my);
		}
		String save = I18n.get("aurora.profiles.save");
		saveProfileW = FontRenderer.SEMIBOLD.width(save, 7.5f) + 28;
		saveProfileX = pasteProfileX - 6 - saveProfileW;
		boolean saveHov = Ui.inside(mx, my, saveProfileX, profileButtonsY, saveProfileW, 22);
		float sh = hover("saveProfile", saveHov).get();
		Render2D.roundRect(g, saveProfileX, profileButtonsY, saveProfileW, 22, 11, Ui.accentPaint(saveProfileX, saveProfileW, 0.3f + 0.3f * sh));
		Ui.iconSave(g, saveProfileX + 12, hy, 9, Theme.TEXT);
		FontRenderer.SEMIBOLD.drawMiddle(g, save, saveProfileX + 21, hy, 7.5f, Ui.c(Theme.TEXT));
	}

	private void drawBinds(GuiGraphicsExtractor g, int mx, int my) {
		float hint = 16;
		String hintText = I18n.get(listKind == ListKind.PROFILES ? "aurora.profiles.hint" : "aurora.ui.binds.hint");
		FontRenderer.MEDIUM.drawMiddle(g, FontRenderer.MEDIUM.ellipsize(hintText, 7, cw), cx, cy + 4, 7,
			Ui.c(Theme.TEXT_MUTED));
		float top = cy + hint;
		float viewH = ch - hint;
		if (bindRows.isEmpty()) {
			FontRenderer.SEMIBOLD.drawCentered(g, I18n.get("aurora.ui.nothing_found"), cx + cw / 2f, top + viewH / 2f, 10, Ui.c(Theme.TEXT_DIM));
			return;
		}
		float total = 0;
		for (SettingRow row : bindRows) {
			total += row.height();
		}
		bindsContentHeight = total + 8;
		float maxScroll = Math.max(0, bindsContentHeight - viewH);
		bindsScrollTarget = Math.max(0, Math.min(maxScroll, bindsScrollTarget));
		float scroll = bindsScroll.target(bindsScrollTarget).get();

		g.enableScissor(Math.round(cx - 4), Math.round(top), Math.round(cx + cw + 4), Math.round(top + viewH));
		Render2D.roundRect(g, cx, top - scroll, cw, bindsContentHeight, 10, Ui.c(0x66151820));
		Render2D.roundOutline(g, cx, top - scroll, cw, bindsContentHeight, 10, 0.7f, Ui.c(Theme.BORDER));
		boolean inView = Ui.inside(mx, my, cx, top, cw, viewH);
		float y = top + 4 - scroll;
		for (SettingRow row : bindRows) {
			float rh = row.height();
			if (y + rh >= top - 40 && y <= top + viewH + 40) {
				row.render(g, inView ? mx : -10000, inView ? my : -10000, cx, y, cw);
			}
			y += rh;
		}
		g.disableScissor();
		drawScrollbar(g, cx + cw + 6, top, viewH, scroll, bindsContentHeight, mx, my);
	}

	private void renderSearch(GuiGraphicsExtractor g, int mx, int my, String placeholder) {
		search.render(g, mx, my);
		if (search.value().isEmpty() && !search.focused()) {
			// TextField draws its own placeholder; ours is localized, so draw it over the empty field.
			FontRenderer.MEDIUM.drawMiddle(g, placeholder, search.x + search.h / 2f + 9, search.y + search.h / 2f, 8, Ui.c(Theme.TEXT_MUTED));
		}
	}

	private int columns() {
		return Math.max(2, (int) ((cw + GAP) / (132 + GAP)));
	}

	private void drawGrid(GuiGraphicsExtractor g, int mx, int my) {
		cards.clear();
		int cols = columns();
		float cardW = (cw - GAP * (cols - 1)) / cols;
		int rowsCount = (shown.size() + cols - 1) / cols;
		gridContentHeight = rowsCount * (CARD_H + GAP) - GAP;
		float maxScroll = Math.max(0, gridContentHeight - ch);
		gridScrollTarget = Math.max(0, Math.min(maxScroll, gridScrollTarget));
		float scroll = gridScroll.target(gridScrollTarget).get();

		if (shown.isEmpty()) {
			FontRenderer.SEMIBOLD.drawCentered(g, I18n.get("aurora.ui.nothing_found"), cx + cw / 2f, cy + ch / 2f - 6, 10, Ui.c(Theme.TEXT_DIM));
			FontRenderer.MEDIUM.drawCentered(g, I18n.get("aurora.ui.nothing_found.hint"), cx + cw / 2f, cy + ch / 2f + 8, 7.5f, Ui.c(Theme.TEXT_MUTED));
			return;
		}

		g.enableScissor(Math.round(cx - 6), Math.round(cy), Math.round(cx + cw + 6), Math.round(cy + ch));
		boolean mouseInView = Ui.inside(mx, my, cx - 6, cy, cw + 12, ch);
		for (int i = 0; i < shown.size(); i++) {
			Module m = shown.get(i);
			int col = i % cols;
			int row = i / cols;
			float x = cx + col * (cardW + GAP);
			float y = cy + row * (CARD_H + GAP) - scroll;
			if (y + CARD_H < cy - 10 || y > cy + ch + 10) {
				continue;
			}
			long delay = i * 22L;
			float appear = Easing.OUT_CUBIC.clamped((System.currentTimeMillis() - listChangedAt - delay) * Motion.speedMultiplier() / 320f);
			if (appear <= 0) {
				continue;
			}
			float saved = Ui.alpha;
			Ui.alpha = saved * appear;
			float oy = (1 - appear) * 10;
			boolean hov = mouseInView && Ui.inside(mx, my, x, y, cardW, CARD_H);
			drawCard(g, m, x, y + oy, cardW, CARD_H, hov, mx, my);
			Ui.alpha = saved;
			cards.add(new Card(m, x, y, cardW, CARD_H));
		}
		g.disableScissor();

		// Edge fades so cards dissolve under the header instead of being cut.
		if (scroll > 1) {
			Render2D.verticalGradient(g, cx - 6, cy, cw + 12, 10, Ui.c(Theme.PANEL_TOP), Ui.c(ColorUtil.withAlpha(Theme.PANEL_TOP, 0)));
		}
		if (scroll < maxScroll - 1) {
			Render2D.verticalGradient(g, cx - 6, cy + ch - 12, cw + 12, 12, Ui.c(ColorUtil.withAlpha(Theme.PANEL, 0)), Ui.c(Theme.PANEL));
		}
		drawScrollbar(g, cx + cw + 6, cy, ch, scroll, gridContentHeight, mx, my);
	}

	private void drawScrollbar(GuiGraphicsExtractor g, float x, float y, float h, float scroll, float content, int mx, int my) {
		if (content <= h) {
			return;
		}
		float thumbH = Math.max(18, h * h / content);
		float thumbY = y + (h - thumbH) * (scroll / (content - h));
		Render2D.roundRect(g, x, thumbY, 2.5f, thumbH, 1.25f, Ui.c(0x30FFFFFF));
	}

	private void drawCard(GuiGraphicsExtractor g, Module m, float x, float y, float w, float h, boolean hov, int mx, int my) {
		float hv = hover(m, hov).get();
		boolean on = m.isEnabled();
		y -= hv * 1.5f;
		if (hv > 0.01f) {
			Render2D.shadow(g, x, y + 2, w, h, 10, 12, Ui.c(ColorUtil.withAlpha(0x000000, Math.round(110 * hv))));
		}
		Render2D.roundRect(g, x, y, w, h, 10, Render2D.Paint.vertical(y, h,
			Ui.c(ColorUtil.lerp(Theme.CARD, Theme.CARD_HOVER, hv)), Ui.c(ColorUtil.lerp(0xFF13161C, 0xFF181B23, hv))));
		if (on) {
			// Soft accent wash from the top-left when enabled.
			Render2D.roundRect(g, x, y, w, h, 10, Render2D.Paint.diagonal(x, y, w, h,
				Ui.c(ColorUtil.fade(Theme.accent(), 0.10f)), Ui.c(ColorUtil.fade(Theme.accent2(), 0.0f))));
		}
		int border = ColorUtil.lerp(Theme.BORDER, ColorUtil.fade(Theme.accent(0.5f), 0.55f), hv);
		Render2D.roundOutline(g, x, y, w, h, 10, 0.8f, Ui.c(border));

		// Icon well
		float iw = 28;
		float ix = x + 10;
		float iy = y + 10;
		if (on) {
			Render2D.roundRect(g, ix, iy, iw, iw, 8, Render2D.Paint.diagonal(ix, iy, iw, iw,
				Ui.c(ColorUtil.fade(Theme.accent(), 0.30f)), Ui.c(ColorUtil.fade(Theme.accent2(), 0.30f))));
			Render2D.roundOutline(g, ix, iy, iw, iw, 8, 0.7f, Ui.c(ColorUtil.fade(Theme.accent(), 0.5f)));
		} else {
			Render2D.roundRect(g, ix, iy, iw, iw, 8, Ui.c(Theme.ELEVATED));
		}
		if (Ui.alpha > 0.55f) {
			g.item(m.icon(), Math.round(ix + 6), Math.round(iy + 6));
		}

		// Name + category
		float tx = ix + iw + 8;
		float nameMax = x + w - 10 - Switch.W - 6 - tx;
		FontRenderer.SEMIBOLD.drawMiddle(g, FontRenderer.SEMIBOLD.ellipsize(m.name(), 9, nameMax), tx, iy + 9, 9, Ui.c(Theme.TEXT));
		FontRenderer.MEDIUM.drawMiddle(g, I18n.get(m.category().translationKey()), tx, iy + 20, 6.5f,
			Ui.c(on ? ColorUtil.fade(Theme.accent(), 0.95f) : Theme.TEXT_MUTED));

		// Switch
		if (m.canToggle()) {
			Switch sw = switches.computeIfAbsent(m, k -> new Switch(k.isEnabled()));
			float sx = x + w - 10 - Switch.W;
			float sy = y + 12;
			sw.render(g, sx, sy, on, Ui.inside(mx, my, sx - 3, sy - 3, Switch.W + 6, Switch.H + 6));
		}

		// Description (two lines max)
		List<String> lines = FontRenderer.MEDIUM.wrap(m.description(), 7, w - 20);
		float dy = y + 50;
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			String line = lines.get(i);
			if (i == 1 && lines.size() > 2) {
				line = FontRenderer.MEDIUM.ellipsize(line + "…", 7, w - 20);
			}
			FontRenderer.MEDIUM.drawMiddle(g, line, x + 10, dy + i * 10, 7, Ui.c(Theme.TEXT_DIM));
		}

		// Settings affordance
		if (hv > 0.01f && !m.settings().isEmpty()) {
			Render2D.chevron(g, x + w - 12, y + h - 12, 5, 1.2f, 0, Ui.c(ColorUtil.fade(Theme.TEXT_DIM, hv)));
		}
	}

	private void drawSettingsPage(GuiGraphicsExtractor g, Module m, int mx, int my) {
		// Header: back button, icon, name, description, master switch.
		float hy = py + HEADER_H / 2f + 2;
		float bx = cx;
		float bs = 22;
		boolean backHov = Ui.inside(mx, my, bx, hy - bs / 2f, bs, bs);
		float bh = hover("back", backHov).get();
		Render2D.roundRect(g, bx, hy - bs / 2f, bs, bs, 7, Ui.c(ColorUtil.lerp(Theme.ELEVATED, 0xFF2A2F3B, bh)));
		Render2D.chevron(g, bx + bs / 2f - 0.5f, hy, 7, 1.5f, 2, Ui.c(Theme.TEXT));

		float tx = bx + bs + 10;
		boolean client = m instanceof ClientSettings;
		if (!client && Ui.alpha > 0.55f) {
			g.item(m.icon(), Math.round(tx), Math.round(hy - 8));
			tx += 22;
		} else if (client) {
			Ui.iconGear(g, tx + 7, hy, 12, Theme.accent());
			tx += 20;
		}
		String name = client ? I18n.get("aurora.category.client") : m.name();
		FontRenderer.BOLD.drawMiddle(g, name, tx, hy - 5, 12, Ui.c(Theme.TEXT));
		String desc = m.description();
		float descMax = cx + cw - tx - (m.canToggle() ? 70 : 40);
		FontRenderer.MEDIUM.drawMiddle(g, FontRenderer.MEDIUM.ellipsize(desc, 7.5f, descMax), tx, hy + 8, 7.5f, Ui.c(Theme.TEXT_DIM));

		// Reset button
		float rx = cx + cw - 22 - (m.canToggle() ? Switch.W + 12 : 0);
		boolean resetHov = Ui.inside(mx, my, rx, hy - 11, 22, 22);
		float rh = hover("reset", resetHov).get();
		Render2D.roundRect(g, rx, hy - 11, 22, 22, 7, Ui.c(ColorUtil.lerp(Theme.ELEVATED, 0xFF2A2F3B, rh)));
		Ui.iconReset(g, rx + 11, hy, 10, ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, rh));
		if (resetHov) {
			g.setTooltipForNextFrame(Component.translatable("aurora.ui.reset"), mx, my);
		}
		if (m.canToggle() && pageSwitch != null) {
			float sx = cx + cw - Switch.W;
			pageSwitch.render(g, sx, hy - Switch.H / 2f, m.isEnabled(), Ui.inside(mx, my, sx - 3, hy - 9, Switch.W + 6, 18));
		}

		// Rows inside a card, with the live preview beside them (above them on a narrow screen).
		pageListX = cx;
		pageListY = cy;
		pageListW = cw;
		pageListH = ch;
		float scrollbarX = cx + cw + 6;
		if (preview != null) {
			if (cw >= 330) {
				float side = Math.min(180, Math.max(132, cw * 0.36f));
				pageListW = cw - side - 12;
				scrollbarX = cx + pageListW + 4.5f;
				preview.render(g, mx, my, cx + pageListW + 12, cy, side, Math.min(ch, CrosshairPreview.fullHeight(side)));
			} else {
				float strip = Math.min(96, ch * 0.42f);
				preview.render(g, mx, my, cx, cy, cw, strip);
				pageListY = cy + strip + 8;
				pageListH = ch - strip - 8;
			}
		}
		float listX = pageListX;
		float listY = pageListY;
		float listW = pageListW;
		float listH = pageListH;
		float total = 0;
		for (SettingRow row : rows) {
			float ap = row.appearance();
			if (ap > 0.001f) {
				total += row.height() * ap;
			}
		}
		settingsContentHeight = total + 8;
		float maxScroll = Math.max(0, settingsContentHeight - listH);
		settingsScrollTarget = Math.max(0, Math.min(maxScroll, settingsScrollTarget));
		float scroll = settingsScroll.target(settingsScrollTarget).get();

		g.enableScissor(Math.round(listX - 4), Math.round(listY), Math.round(listX + listW + 4), Math.round(listY + listH));
		float cardH = Math.min(settingsContentHeight, Math.max(settingsContentHeight, 0));
		Render2D.roundRect(g, listX, listY - scroll, listW, cardH, 10, Ui.c(0x66151820));
		Render2D.roundOutline(g, listX, listY - scroll, listW, cardH, 10, 0.7f, Ui.c(Theme.BORDER));
		float y = listY + 4 - scroll;
		boolean inView = Ui.inside(mx, my, listX, listY, listW, listH);
		for (SettingRow row : rows) {
			float ap = row.appearance();
			if (ap <= 0.001f) {
				continue;
			}
			float rowH = row.height() * ap;
			if (y + rowH >= listY - 40 && y <= listY + listH + 40) {
				float saved = Ui.alpha;
				Ui.alpha = saved * ap;
				row.render(g, inView ? mx : -10000, inView ? my : -10000, listX, y, listW);
				Ui.alpha = saved;
			}
			y += rowH;
		}
		g.disableScissor();
		drawScrollbar(g, scrollbarX, listY, listH, scroll, settingsContentHeight, mx, my);
	}

	// ======================================================================= input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!interactive()) {
			return true;
		}
		double mx = event.x();
		double my = event.y();
		int button = event.button();

		SettingRow overlay = openOverlay();
		if (overlay != null) {
			overlay.overlayClicked(mx, my);
			return true;
		}

		// A bind row waiting for a key takes the click (mouse button or cancel); a row with a
		// focused text field gives up focus and lets the click through when it lands elsewhere.
		if (bindsPage && selected == null) {
			for (SettingRow row : bindRows) {
				if (row.capturesKeys() && row.mouseClicked(mx, my, button)) {
					return true;
				}
			}
		}

		// Sidebar
		float x = px + 12;
		float w = SIDEBAR_W - 24;
		float y = py + 60;
		Category[] cats = {null, Category.HUD, Category.VISUAL, Category.UTILITY, Category.PERFORMANCE};
		for (Category c : cats) {
			if (Ui.inside(mx, my, x, y, w, navH)) {
				selectCategory(c);
				search.setFocused(false);
				return true;
			}
			y += navH + 2;
		}
		float by = py + ph - 12 - 26;
		float sy = by - 8 - navH;
		float bindsY = sy - 2 - navH;
		float profilesY = bindsY - 2 - navH;
		if (Ui.inside(mx, my, x, profilesY, w, navH)) {
			openProfiles();
			return true;
		}
		if (Ui.inside(mx, my, x, bindsY, w, navH)) {
			search.setFocused(false);
			openBinds();
			return true;
		}
		if (Ui.inside(mx, my, x, sy, w, navH)) {
			search.setFocused(false);
			openSettings(Aurora.client());
			return true;
		}
		if (Ui.inside(mx, my, x, by, w, 26)) {
			openHudEditor(null);
			return true;
		}

		if (selected != null) {
			return settingsClicked(mx, my, button);
		}

		// The Profiles page has no search box; its last position must not take clicks.
		if (!profilesPage() && search.mouseClicked(mx, my)) {
			return true;
		}
		if (bindsPage) {
			if (profilesPage() && button == LEFT) {
				if (Ui.inside(mx, my, saveProfileX, profileButtonsY, saveProfileW, 22)) {
					saveProfile();
					return true;
				}
				if (Ui.inside(mx, my, pasteProfileX, profileButtonsY, pasteProfileW, 22)) {
					pasteProfile();
					return true;
				}
				if (Ui.inside(mx, my, undoProfileX, profileButtonsY, 22, 22)) {
					undoProfile();
					return true;
				}
			} else if (!profilesPage() && button == LEFT && Ui.inside(mx, my, resetBindsX, resetBindsY, resetBindsW, 22)) {
				resetAllBinds();
				return true;
			}
			if (Ui.inside(mx, my, cx, cy + 16, cw, ch - 16)) {
				for (SettingRow row : bindRows) {
					if (row.mouseClicked(mx, my, button)) {
						return true;
					}
				}
			}
			return super.mouseClicked(event, doubleClick);
		}
		if (!Ui.inside(mx, my, cx - 6, cy, cw + 12, ch)) {
			return super.mouseClicked(event, doubleClick);
		}
		for (Card card : cards) {
			if (!Ui.inside(mx, my, card.x, card.y, card.w, card.h)) {
				continue;
			}
			Module m = card.module;
			float sx = card.x + card.w - 10 - Switch.W;
			float sy2 = card.y + 12;
			boolean onSwitch = Ui.inside(mx, my, sx - 4, sy2 - 4, Switch.W + 8, Switch.H + 8);
			if (m.canToggle() && (onSwitch || button == RIGHT)) {
				m.toggle();
				Aurora.config().markDirty();
			} else if (button == LEFT) {
				openSettings(m);
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private boolean settingsClicked(double mx, double my, int button) {
		float hy = py + HEADER_H / 2f + 2;
		if (Ui.inside(mx, my, cx, hy - 11, 22, 22) || button == 4) {
			closeSettings();
			return true;
		}
		Module m = selected;
		if (m == null) {
			return false;
		}
		float rx = cx + cw - 22 - (m.canToggle() ? Switch.W + 12 : 0);
		if (Ui.inside(mx, my, rx, hy - 11, 22, 22)) {
			m.resetToDefaults();
			Aurora.config().markDirty();
			openSettings(m);
			return true;
		}
		if (m.canToggle() && Ui.inside(mx, my, cx + cw - Switch.W - 3, hy - 9, Switch.W + 6, 18)) {
			m.toggle();
			Aurora.config().markDirty();
			return true;
		}
		if (preview != null && preview.mouseClicked(mx, my, button)) {
			return true;
		}
		if (!Ui.inside(mx, my, pageListX - 4, pageListY, pageListW + 8, pageListH)) {
			return false;
		}
		for (SettingRow row : rows) {
			if (row.isVisible() && row.mouseClicked(mx, my, button)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		for (SettingRow row : rows) {
			row.mouseReleased();
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (selected != null) {
			for (SettingRow row : rows) {
				if (row.mouseDragged(event.x(), event.y())) {
					return true;
				}
			}
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (openOverlay() != null) {
			return true;
		}
		if (selected != null) {
			settingsScrollTarget -= (float) scrollY * 28;
		} else if (bindsPage) {
			bindsScrollTarget -= (float) scrollY * 28;
		} else {
			gridScrollTarget -= (float) scrollY * 32;
		}
		return true;
	}

	/** Set when a bind row took a key, so the character event that follows is not typed into search. */
	private boolean swallowNextChar;

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!interactive()) {
			return true;
		}
		swallowNextChar = false;
		if (selected != null) {
			for (SettingRow row : rows) {
				if (row.capturesKeys() && row.keyPressed(event)) {
					swallowNextChar = true;
					return true;
				}
			}
		} else if (bindsPage) {
			for (SettingRow row : bindRows) {
				if (row.capturesKeys() && row.keyPressed(event)) {
					swallowNextChar = true;
					return true;
				}
			}
		}
		if (event.isEscape()) {
			SettingRow overlay = openOverlay();
			if (overlay != null) {
				overlay.closeOverlay();
				return true;
			}
			if (selected != null) {
				closeSettings();
				return true;
			}
			if (search.focused() || !search.value().isEmpty()) {
				if (!search.value().isEmpty()) {
					search.setValue("");
				}
				search.setFocused(false);
				return true;
			}
			onClose();
			return true;
		}
		if (selected == null && search.keyPressed(event)) {
			return true;
		}
		if (Aurora.menuKey.matches(event) && !search.focused() && !typingInRow()) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (swallowNextChar) {
			swallowNextChar = false;
			return true;
		}
		if (selected != null) {
			return false;
		}
		if (bindsPage) {
			for (SettingRow row : bindRows) {
				if (row.charTyped(event)) {
					return true;
				}
			}
			if (typingInRow() || profilesPage()) {
				return true;
			}
		}
		if (!search.focused() && event.isAllowedChatCharacter() && !Character.isWhitespace(event.codepoint())) {
			// Typing anywhere starts a search, like a launcher.
			search.setFocused(true);
		}
		return search.charTyped(event);
	}

	@Override
	public void onClose() {
		if (closingAt < 0) {
			closingAt = System.currentTimeMillis();
			closeTarget = parent;
			rememberedSearch = search.value();
			Aurora.config().save();
		}
	}

	@Override
	public void tick() {
		if (closingAt >= 0 && Motion.progress(closingAt, 170) >= 1f) {
			minecraft.gui.setScreen(closeTarget);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
