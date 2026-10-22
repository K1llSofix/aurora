package dev.aurora.module.visual;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

/** Extra hit particles: more of them, and crit or sharpness sparks on every hit if wanted. */
public final class ParticlesModule extends Module {
	private final NumberSetting multiplier = add(new NumberSetting("multiplier", 2, 1, 10, 1).suffix("x"));
	private final BooleanSetting alwaysCrit = add(new BooleanSetting("always_crit", false));
	private final BooleanSetting alwaysSharp = add(new BooleanSetting("always_sharp", true));

	public ParticlesModule() {
		super("particles", Category.VISUAL, Items.BLAZE_POWDER, false);
	}

	public void onAttack(Entity target) {
		if (!isEnabled() || !(target instanceof LivingEntity) || mc.player == null) {
			return;
		}
		boolean crit = mc.player.fallDistance > 0 && !mc.player.onGround() && !mc.player.isInWater();
		int extra = multiplier.intValue() - 1;
		if (crit || alwaysCrit.on()) {
			int count = extra + (crit ? 0 : 1);
			for (int i = 0; i < count; i++) {
				mc.particleEngine.createTrackingEmitter(target, ParticleTypes.CRIT);
			}
		}
		if (alwaysSharp.on()) {
			for (int i = 0; i < Math.max(1, extra); i++) {
				mc.particleEngine.createTrackingEmitter(target, ParticleTypes.ENCHANTED_HIT);
			}
		}
	}
}
