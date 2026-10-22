package dev.aurora.module;

import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.Motion;
import net.minecraft.world.item.Items;

/** Appearance of Aurora itself: theme, animations and the shared HUD style. */
public final class ClientSettings extends Module {
	public final ModeSetting theme = add(new ModeSetting("theme", "aurora",
		"aurora", "ocean", "sunset", "rose", "emerald", "crimson", "mono", "custom"));
	public final ColorSetting customAccent = add(new ColorSetting("accent", 0xFF7C5CFF, false))
		.visibleWhen(() -> theme.is("custom"));
	public final ModeSetting animations = add(new ModeSetting("animations", "smooth", "smooth", "fast", "off"))
		.onChange(v -> applyAnimationSpeed());
	public final BooleanSetting menuBlur = add(new BooleanSetting("blur", true));

	public final ModeSetting hudStyle = add(new ModeSetting("hud_style", "modern", "modern", "classic", "minimal"));
	public final ModeSetting hudFont = add(new ModeSetting("hud_font", "inter", "inter", "minecraft"));
	public final NumberSetting hudOpacity = add(new NumberSetting("hud_opacity", 55, 0, 100, 5).suffix("%"))
		.visibleWhen(() -> hudStyle.is("modern"));
	public final BooleanSetting hudAccent = add(new BooleanSetting("hud_accent", true))
		.visibleWhen(() -> hudStyle.is("modern"));
	public final ColorSetting hudText = add(new ColorSetting("hud_text", 0xFFF1F3F8, false));
	public final BooleanSetting hudShadow = add(new BooleanSetting("hud_shadow", true));
	public final BooleanSetting hideInDebug = add(new BooleanSetting("hide_debug", true));
	public final BooleanSetting notifications = add(new BooleanSetting("notifications", true));
	public final BooleanSetting customMenu = add(new BooleanSetting("main_menu", true));
	public final ModeSetting menuBackground = add(new ModeSetting("menu_background", "aurora",
		"aurora", "sunset", "synthwave", "image", "panorama"));
	public final BooleanSetting menuParallax = add(new BooleanSetting("menu_parallax", true))
		.visibleWhen(() -> !menuBackground.is("panorama"));
	public final BooleanSetting vanillaStyle = add(new BooleanSetting("vanilla_style", true));
	public final BooleanSetting pauseMenu = add(new BooleanSetting("pause_menu", true));
	public final BooleanSetting loadingScreens = add(new BooleanSetting("loading_screens", true));

	public ClientSettings() {
		super("client", Category.CLIENT, Items.NETHER_STAR, true);
	}

	@Override
	public boolean canToggle() {
		return false;
	}

	public void applyAnimationSpeed() {
		Motion.setSpeedMultiplier(switch (animations.get()) {
			case "fast" -> 1.8f;
			case "off" -> 0f;
			default -> 1f;
		});
	}
}
