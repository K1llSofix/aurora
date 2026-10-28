package dev.aurora.module.visual;

import dev.aurora.gui.Theme;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.ColorUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;

/**
 * Name tags in the client's style: a dark glass background tinted with the theme colour, its own
 * opacity and size, and your own name over your head in third person. They show nothing vanilla
 * name tags do not.
 */
public final class NametagsModule extends Module {
	private final ModeSetting background = add(new ModeSetting("background", "theme", "theme", "vanilla", "none"));
	private final NumberSetting opacity = add(new NumberSetting("opacity", 45, 10, 90, 5).suffix("%"))
		.visibleWhen(() -> !background.is("none"));
	private final NumberSetting size = add(new NumberSetting("size", 100, 60, 150, 5).suffix("%"));
	private final BooleanSetting own = add(new BooleanSetting("own", true));

	private static @Nullable NametagsModule instance;

	public NametagsModule() {
		super("nametags", Category.VISUAL, Items.NAME_TAG, false);
		instance = this;
	}

	public static @Nullable NametagsModule active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	/** The background behind a name, replacing vanilla's translucent black. */
	public int background(int vanilla) {
		return switch (background.get()) {
			case "none" -> 0;
			case "vanilla" -> ColorUtil.withAlpha(vanilla, Math.round(255 * opacity.floatValue() / 100f));
			// Deep navy with a hint of the accent, like the client's panels.
			default -> ColorUtil.withAlpha(ColorUtil.lerp(0x0E1118, Theme.accent() & 0xFFFFFF, 0.14f), Math.round(255 * opacity.floatValue() / 100f));
		};
	}

	public float scale() {
		return size.floatValue() / 100f;
	}

	/** Your own name tag in third person, which vanilla never shows; a team that hides names still wins. */
	public boolean showsOwn(Entity entity) {
		if (!own.on() || entity != mc.player || mc.options.getCameraType().isFirstPerson() || mc.gui.hud.isHidden() || entity.isInvisible()) {
			return false;
		}
		Team team = entity.getTeam();
		Team.Visibility visibility = team == null ? Team.Visibility.ALWAYS : team.getNameTagVisibility();
		return visibility == Team.Visibility.ALWAYS || visibility == Team.Visibility.HIDE_FOR_OTHER_TEAMS;
	}
}
