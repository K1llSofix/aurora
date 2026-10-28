package dev.aurora.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.aurora.module.visual.NametagsModule;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Name tags in the client's style: background colour and size. */
@Mixin(SubmitNodeCollection.class)
public abstract class NameTagMixin {
	@ModifyExpressionValue(method = "submitNameTag",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;color(FI)I", ordinal = 0))
	private int aurora$background(int vanilla) {
		NametagsModule tags = NametagsModule.active();
		return tags == null ? vanilla : tags.background(vanilla);
	}

	@WrapOperation(method = "submitNameTag",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void aurora$size(PoseStack poseStack, float x, float y, float z, Operation<Void> original) {
		NametagsModule tags = NametagsModule.active();
		float s = tags == null ? 1f : tags.scale();
		original.call(poseStack, x * s, y * s, z * s);
	}
}
