package dev.aurora.mixin;

import com.mojang.renderpearl.api.device.GpuSurface;
import dev.aurora.module.performance.PerformanceModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * "Latest frames only": with mailbox presentation a finished frame replaces one still waiting for
 * the screen, so the monitor always shows the newest picture, without tearing and without the
 * queue of older frames that vertical sync builds up. Only surfaces that offer it (Vulkan) can.
 */
@Mixin(GpuSurface.PresentMode.class)
public abstract class PresentModeMixin {
	@Inject(method = "getSupportedVsyncMode", at = @At("HEAD"), cancellable = true)
	private static void aurora$latestFrames(Collection<GpuSurface.PresentMode> supported, boolean vsync,
		CallbackInfoReturnable<GpuSurface.PresentMode> cir) {
		if (PerformanceModule.wantsLatestFrames() && supported.contains(GpuSurface.PresentMode.MAILBOX)) {
			cir.setReturnValue(GpuSurface.PresentMode.MAILBOX);
		}
	}
}
