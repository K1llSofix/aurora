package dev.aurora.module.visual;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.NumberSetting;
import net.minecraft.world.item.Items;

/** Works like a client-side Night Vision effect, so colours stay natural instead of washed out. */
public final class FullbrightModule extends Module {
	private final NumberSetting strength = add(new NumberSetting("strength", 100, 10, 100, 5).suffix("%"));

	public FullbrightModule() {
		super("fullbright", Category.VISUAL, Items.SEA_LANTERN, false);
	}

	public float strength() {
		return strength.floatValue() / 100f;
	}
}
