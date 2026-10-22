package dev.aurora.module.visual;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.world.item.Items;

/** Small quality-of-life tweaks to what covers your screen. */
public final class OverlaysModule extends Module {
	public final BooleanSetting noHurtCam = add(new BooleanSetting("no_hurt_cam", true));
	public final BooleanSetting lowFire = add(new BooleanSetting("low_fire", true));
	public final NumberSetting fireOffset = add(new NumberSetting("fire_offset", 30, 5, 60, 5).suffix("%"))
		.visibleWhen(lowFire::on);
	public final BooleanSetting noPumpkin = add(new BooleanSetting("no_pumpkin", true));

	public OverlaysModule() {
		super("overlays", Category.VISUAL, Items.FIRE_CHARGE, true);
	}

	public boolean hideHurtCam() {
		return isEnabled() && noHurtCam.on();
	}

	public float fireDrop() {
		return isEnabled() && lowFire.on() ? fireOffset.floatValue() / 100f : 0f;
	}

	public boolean hidePumpkin() {
		return isEnabled() && noPumpkin.on();
	}
}
