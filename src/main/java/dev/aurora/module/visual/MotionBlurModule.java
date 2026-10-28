package dev.aurora.module.visual;

import dev.aurora.Aurora;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.NumberSetting;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Motion blur: every frame is blended with the one before, so fast turns leave a soft trail. A
 * post effect of the game (assets/aurora/post_effect), applied to the world only, never to the HUD
 * or menus. Each strength is its own small effect file.
 */
public final class MotionBlurModule extends Module {
	private final NumberSetting strength = add(new NumberSetting("strength", 50, 10, 90, 10).suffix("%"));

	private static @Nullable MotionBlurModule instance;

	public MotionBlurModule() {
		super("motion_blur", Category.VISUAL, Items.FEATHER, false);
		instance = this;
	}

	/** The post effect to apply this frame, or null. */
	public static @Nullable Identifier effect() {
		MotionBlurModule blur = instance;
		if (blur == null || !blur.isEnabled()) {
			return null;
		}
		return Aurora.id("motion_blur_" + blur.strength.intValue());
	}
}
