package dev.aurora.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aurora.Aurora;
import dev.aurora.module.visual.MotionBlurModule;
import dev.aurora.module.visual.OverlaysModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow
	@Final
	private List<Identifier> requestedPostEffects;

	/** No hurt cam: skip the screen tilt when taking damage. */
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void aurora$noHurtCam(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		OverlaysModule overlays = Aurora.modules().get(OverlaysModule.class);
		if (overlays != null && overlays.hideHurtCam() && !cameraState.entityRenderState.isDeadOrDying) {
			ci.cancel();
		}
	}

	/** Motion blur: asked for every frame like the game's own effects, so it goes away with them too. */
	@Inject(method = "update", at = @At("TAIL"))
	private void aurora$motionBlur(DeltaTracker deltaTracker, CallbackInfo ci) {
		Identifier blur = MotionBlurModule.effect();
		if (blur != null) {
			requestedPostEffects.add(blur);
		}
	}
}
