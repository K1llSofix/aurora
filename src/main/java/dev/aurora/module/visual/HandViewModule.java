package dev.aurora.module.visual;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.NumberSetting;
import net.minecraft.world.item.Items;

/** Moves and resizes the item held in first person. */
public final class HandViewModule extends Module {
	public final NumberSetting x = add(new NumberSetting("x", 0, -0.8, 0.8, 0.02));
	public final NumberSetting y = add(new NumberSetting("y", 0, -0.8, 0.8, 0.02));
	public final NumberSetting z = add(new NumberSetting("z", 0, -0.8, 0.8, 0.02));
	public final NumberSetting scale = add(new NumberSetting("scale", 100, 30, 150, 5).suffix("%"));

	public HandViewModule() {
		super("hand_view", Category.VISUAL, Items.NETHERITE_SWORD, false);
	}
}
