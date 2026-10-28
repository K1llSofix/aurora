package dev.aurora.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.aurora.module.visual.ItemPhysicsModule;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item physics: instead of hovering and spinning, a dropped item lies flat where it fell, and
 * tumbles while it flies. Only the vanilla bob and spin are replaced, so shadows, outlines and
 * stacks of several items stay as they are.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
		at = @At("TAIL"))
	private void aurora$physicsState(ItemEntity entity, ItemEntityRenderState state, float partialTicks, CallbackInfo ci) {
		((ItemPhysicsModule.State) state).aurora$setPhysics(entity.onGround(), entity.isInWater() || entity.isInLava());
	}

	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
	private void aurora$place(PoseStack poseStack, float x, float y, float z, Operation<Void> original,
							  @Local(argsOnly = true) ItemEntityRenderState state, @Local AABB boundingBox) {
		ItemPhysicsModule physics = ItemPhysicsModule.active();
		if (physics == null) {
			original.call(poseStack, x, y, z);
			return;
		}
		physics.place(poseStack, state, boundingBox);
	}

	@WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotate(Lcom/mojang/math/Axis;F)V"))
	private void aurora$noSpin(PoseStack poseStack, Axis axis, float angle, Operation<Void> original) {
		if (ItemPhysicsModule.active() == null) {
			original.call(poseStack, axis, angle);
		}
	}
}
