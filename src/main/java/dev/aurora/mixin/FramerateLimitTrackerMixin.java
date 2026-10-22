package dev.aurora.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.aurora.module.performance.PerformanceModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The game slows down only when minimised; this also rests a window that is merely in the background. */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {
	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void aurora$backgroundLimit(CallbackInfoReturnable<Integer> cir) {
		PerformanceModule performance = PerformanceModule.active();
		if (performance != null) {
			cir.setReturnValue(performance.frameLimit(cir.getReturnValueI()));
		}
	}
}
