package dev.aurora.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.aurora.module.visual.AuroraSkyModule;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Northern lights: uploaded before the sky pass, drawn in it right after the sun, moon and stars. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void aurora$prepareAurora(GpuBufferSlice skyFog, SkyRenderState state, CallbackInfo ci) {
		AuroraSkyModule sky = AuroraSkyModule.active();
		if (sky != null) {
			sky.prepare(state);
		}
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/SkyRenderer;renderSunMoonAndStars(Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/blaze3d/vertex/PoseStack;FFFLnet/minecraft/world/level/MoonPhase;FF)V"))
	private void aurora$drawAurora(SkyRenderer sky, RenderPass pass, PoseStack poseStack, float sunAngle, float moonAngle, float starAngle,
								   MoonPhase moonPhase, float rainBrightness, float starBrightness, Operation<Void> original) {
		original.call(sky, pass, poseStack, sunAngle, moonAngle, starAngle, moonPhase, rainBrightness, starBrightness);
		AuroraSkyModule aurora = AuroraSkyModule.active();
		if (aurora != null) {
			aurora.draw(pass);
		}
	}
}
