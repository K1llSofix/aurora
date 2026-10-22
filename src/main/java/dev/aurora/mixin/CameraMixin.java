package dev.aurora.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aurora.Aurora;
import dev.aurora.module.visual.FreelookModule;
import dev.aurora.module.visual.ZoomModule;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public abstract class CameraMixin {
	/** Zoom: divide the final field of view. */
	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float aurora$zoom(float fov) {
		ZoomModule zoom = Aurora.modules().get(ZoomModule.class);
		return zoom == null ? fov : fov / zoom.fovDivisor();
	}

	/** Freelook: the camera uses its own yaw instead of the entity's. */
	@ModifyExpressionValue(method = "alignWithEntity",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float aurora$freelookYaw(float yaw) {
		FreelookModule freelook = Aurora.modules().get(FreelookModule.class);
		return freelook != null && freelook.active() ? freelook.yaw() : yaw;
	}

	@ModifyExpressionValue(method = "alignWithEntity",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float aurora$freelookPitch(float pitch) {
		FreelookModule freelook = Aurora.modules().get(FreelookModule.class);
		return freelook != null && freelook.active() ? freelook.pitch() : pitch;
	}
}
