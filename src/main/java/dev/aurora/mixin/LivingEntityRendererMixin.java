package dev.aurora.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aurora.module.visual.NametagsModule;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	/** Your own name tag in third person (vanilla hides the camera entity's name). */
	@ModifyReturnValue(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"))
	private boolean aurora$ownName(boolean shown, LivingEntity entity, double distanceToCameraSq) {
		if (shown) {
			return true;
		}
		NametagsModule tags = NametagsModule.active();
		return tags != null && tags.showsOwn(entity);
	}
}
