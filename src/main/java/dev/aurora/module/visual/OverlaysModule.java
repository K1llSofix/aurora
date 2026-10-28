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
	public final BooleanSetting lowShield = add(new BooleanSetting("low_shield", false));
	public final NumberSetting shieldOffset = add(new NumberSetting("shield_offset", 25, 10, 50, 5).suffix("%"))
		.visibleWhen(lowShield::on);
	public final NumberSetting totemSize = add(new NumberSetting("totem_size", 100, 0, 100, 10).suffix("%"));

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

	/** How far a shield in your hand sits lower, in first-person units. */
	public float shieldDrop() {
		return isEnabled() && lowShield.on() ? shieldOffset.floatValue() / 100f * 0.8f : 0f;
	}

	/** Size of the item that pops up when a totem saves you: 1 is vanilla, 0 hides it. */
	public float totemScale() {
		return isEnabled() ? totemSize.floatValue() / 100f : 1f;
	}
}
