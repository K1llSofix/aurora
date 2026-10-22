package dev.aurora.module.visual;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import net.minecraft.world.item.Items;

/** Clear skies on your screen: no rain, snow or thunder darkening. */
public final class WeatherModule extends Module {
	public WeatherModule() {
		super("weather", Category.VISUAL, Items.SNOWBALL, false);
	}
}
