package dev.aurora.mixin;

import dev.aurora.module.performance.PerformanceModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The start of every frame, for the stable frame rate's measurements. */
@Mixin(Minecraft.class)
public abstract class MinecraftFrameMixin {
	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void aurora$frameStart(boolean advanceGameTime, CallbackInfo ci) {
		PerformanceModule.onFrameStart();
	}
}
