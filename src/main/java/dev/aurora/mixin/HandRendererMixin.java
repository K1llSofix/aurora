package dev.aurora.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.aurora.Aurora;
import dev.aurora.module.visual.HandViewModule;
import dev.aurora.module.visual.OverlaysModule;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
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

	/** Low shield: a shield in either hand sits lower, so it covers less of the view. */
	@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void aurora$lowShield(ItemStackRenderState item, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outline,
								  Operation<Void> original, @Local(argsOnly = true) ItemStack itemStack) {
		OverlaysModule overlays = Aurora.modules().get(OverlaysModule.class);
		float drop = overlays == null || !(itemStack.getItem() instanceof ShieldItem) ? 0 : overlays.shieldDrop();
		if (drop <= 0) {
			original.call(item, poseStack, collector, light, overlay, outline);
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0, -drop, 0);
		original.call(item, poseStack, collector, light, overlay, outline);
		poseStack.popPose();
	}
}
