package dev.aurora.mixin;

import dev.aurora.module.performance.PerformanceModule;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skips drawing entities beyond the distances set in the Performance module. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void aurora$cullFar(E entity, Frustum culler, double camX, double camY, double camZ, float partialTicks,
												CallbackInfoReturnable<Boolean> cir) {
		PerformanceModule performance = PerformanceModule.active();
		if (performance != null && performance.skipEntity(entity, camX, camY, camZ)) {
			cir.setReturnValue(false);
		}
	}
}
