package dev.aurora.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aurora.Aurora;
import dev.aurora.module.visual.OverlaysModule;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	/** Low fire: shift the first-person fire overlay down so it covers less of the view. */
	@Inject(method = "submitFire", at = @At("HEAD"))
	private static void aurora$lowerFire(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		poseStack.pushPose();
		OverlaysModule overlays = Aurora.modules().get(OverlaysModule.class);
		float drop = overlays == null ? 0 : overlays.fireDrop();
		if (drop > 0) {
			poseStack.translate(0, -drop, 0);
		}
	}

	@Inject(method = "submitFire", at = @At("RETURN"))
	private static void aurora$restoreFire(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		poseStack.popPose();
	}
}
