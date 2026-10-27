package dev.aurora.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.aurora.Aurora;
import dev.aurora.module.visual.HandViewModule;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HandRendererMixin {
	/**
	 * Hand view: extra offset, turn and scale after the vanilla item-in-hand placement. Turns
	 * around the vertical and forward axes are mirrored for the left hand, so both hands match.
	 */
	@Inject(method = "applyItemArmTransform", at = @At("TAIL"))
	private void aurora$handView(PoseStack poseStack, HumanoidArm arm, float inverseArmHeight, CallbackInfo ci) {
		HandViewModule view = Aurora.modules().get(HandViewModule.class);
		if (view == null || !view.isEnabled()) {
			return;
		}
		int side = arm == HumanoidArm.RIGHT ? 1 : -1;
		poseStack.translate(side * view.x.floatValue(), view.y.floatValue(), view.z.floatValue());
		poseStack.rotateDegrees(Axis.XP, view.rotX.floatValue());
		poseStack.rotateDegrees(Axis.YP, side * view.rotY.floatValue());
		poseStack.rotateDegrees(Axis.ZP, side * view.rotZ.floatValue());
		float s = view.scale.floatValue() / 100f;
		poseStack.scale(s, s, s);
	}
}
