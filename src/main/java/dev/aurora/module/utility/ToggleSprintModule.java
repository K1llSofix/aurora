package dev.aurora.module.utility;

import dev.aurora.gui.HudDraw;
import dev.aurora.module.Category;
import dev.aurora.module.HudModule;
import dev.aurora.setting.BooleanSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.Items;

/**
 * Toggle sprint (and optionally sneak) through the game's own toggle options, so all of
 * vanilla's sprint rules still apply; adds a small status line on the HUD.
 */
public final class ToggleSprintModule extends HudModule {
	private final BooleanSetting sneak = add(new BooleanSetting("sneak", false)).onChange(v -> apply());
	private final BooleanSetting hud = add(new BooleanSetting("hud", true));

	private Boolean savedSprint;
	private Boolean savedCrouch;
	private String text = "";

	public ToggleSprintModule() {
		super("toggle_sprint", Category.UTILITY, Items.FEATHER, false, 0, 0, 4, 234);
	}

	@Override
	protected void onEnable() {
		// Applied on the next tick: this also runs while the config loads, before options exist.
		savedSprint = null;
	}

	@Override
	protected void onDisable() {
		if (savedSprint != null) {
			mc.options.toggleSprint().set(savedSprint);
			savedSprint = null;
		}
		if (savedCrouch != null) {
			mc.options.toggleCrouch().set(savedCrouch);
			savedCrouch = null;
		}
	}

	private void apply() {
		if (!isEnabled()) {
			return;
		}
		if (savedSprint == null) {
			savedSprint = mc.options.toggleSprint().get();
		}
		mc.options.toggleSprint().set(true);
		if (sneak.on()) {
			if (savedCrouch == null) {
				savedCrouch = mc.options.toggleCrouch().get();
			}
			mc.options.toggleCrouch().set(true);
		} else if (savedCrouch != null) {
			mc.options.toggleCrouch().set(savedCrouch);
			savedCrouch = null;
		}
	}

	@Override
	public void tick() {
		if (savedSprint == null) {
			apply();
		}
	}

	private String status(boolean editing) {
		if (mc.player == null) {
			return editing ? I18n.get("aurora.hud.sprint.toggled") : "";
		}
		if (mc.player.isShiftKeyDown() || mc.options.keyShift.isDown()) {
			return I18n.get(sneak.on() ? "aurora.hud.sneak.toggled" : "aurora.hud.sneak.held");
		}
		if (mc.options.keySprint.isDown()) {
			return I18n.get("aurora.hud.sprint.toggled");
		}
		if (mc.player.isSprinting()) {
			return I18n.get("aurora.hud.sprint.vanilla");
		}
		return editing ? I18n.get("aurora.hud.sprint.toggled") : "";
	}

	@Override
	public boolean hasContent() {
		return hud.on() && !status(false).isEmpty();
	}

	@Override
	public void layout(boolean editing) {
		text = status(editing);
		width = HudDraw.width(text) + HudDraw.contentInset() + HudDraw.padX();
		height = HudDraw.rowHeight();
	}

	@Override
	public void render(GuiGraphicsExtractor g, float partialTick, boolean editing) {
		HudDraw.panel(g, 0, 0, width, height);
		HudDraw.text(g, text, HudDraw.contentInset(), height / 2f, HudDraw.textColor());
	}
}
