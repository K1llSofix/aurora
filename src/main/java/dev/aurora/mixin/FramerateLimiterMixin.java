package dev.aurora.mixin;

import dev.aurora.module.performance.PerformanceModule;
import net.minecraft.client.FramerateLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the stable frame rate how long the game's own limiter waited, which is not work. */
@Mixin(FramerateLimiter.class)
public abstract class FramerateLimiterMixin {
	@Unique
	private static long aurora$waitStart;

	@Inject(method = "limitDisplayFPS", at = @At("HEAD"))
	private static void aurora$waitStart(int limit, CallbackInfo ci) {
		aurora$waitStart = System.nanoTime();
	}

	@Inject(method = "limitDisplayFPS", at = @At("RETURN"))
	private static void aurora$waitEnd(int limit, CallbackInfo ci) {
		PerformanceModule.onLimiterWait(System.nanoTime() - aurora$waitStart);
	}
}
