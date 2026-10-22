package dev.aurora.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aurora.Aurora;
import dev.aurora.module.visual.OverlaysModule;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	/** No hurt cam: skip the screen tilt when taking damage. */
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void aurora$noHurtCam(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		OverlaysModule overlays = Aurora.modules().get(OverlaysModule.class);
		if (overlays != null && overlays.hideHurtCam() && !cameraState.entityRenderState.isDeadOrDying) {
			ci.cancel();
		}
	}
}
