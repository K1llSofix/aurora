package dev.aurora.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a particle is, for the Performance module's spawn distance. */
@Mixin(Particle.class)
public interface ParticleAccessor {
	@Accessor("x")
	double aurora$getX();

	@Accessor("y")
	double aurora$getY();

	@Accessor("z")
	double aurora$getZ();
}
