package dev.aurora.mixin;

import dev.aurora.module.performance.PerformanceModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Queue;

/** Caps how many particles exist and drops ones spawned far from the camera (Performance module). */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Shadow
	@Final
	private Map<ParticleRenderType, ParticleGroup<?>> particles;

	@Shadow
	@Final
	private Queue<Particle> particlesToAdd;

	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void aurora$limit(Particle particle, CallbackInfo ci) {
		PerformanceModule performance = PerformanceModule.active();
		if (performance == null) {
			return;
		}
		int alive = particlesToAdd.size();
		for (ParticleGroup<?> group : particles.values()) {
			alive += group.size();
		}
		ParticleAccessor p = (ParticleAccessor) particle;
		if (performance.skipParticle(p.aurora$getX(), p.aurora$getY(), p.aurora$getZ(), alive)) {
			ci.cancel();
		}
	}
}
