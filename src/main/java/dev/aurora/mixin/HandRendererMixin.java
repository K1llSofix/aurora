package dev.aurora.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
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
	/** Hand view: extra offset and scale after the vanilla item-in-hand placement. */
	@Inject(method = "applyItemArmTransform", at = @At("TAIL"))
	private void aurora$handView(PoseStack poseStack, HumanoidArm arm, float inverseArmHeight, CallbackInfo ci) {
		HandViewModule view = Aurora.modules().get(HandViewModule.class);
		if (view == null || !view.isEnabled()) {
			return;
		}
		int side = arm == HumanoidArm.RIGHT ? 1 : -1;
		poseStack.translate(side * view.x.floatValue(), view.y.floatValue(), view.z.floatValue());
		float s = view.scale.floatValue() / 100f;
		poseStack.scale(s, s, s);
	}
}
