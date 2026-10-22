package dev.aurora.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aurora.Aurora;
import dev.aurora.module.visual.BlockOutlineModule;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Shadow
	@Final
	private GameRenderer gameRenderer;

	/** Replaces the vanilla selection box unless the accessibility high-contrast outline is on. */
	@Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
	private void aurora$blockOutline(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState levelState, CallbackInfo ci) {
		BlockOutlineModule module = Aurora.modules().get(BlockOutlineModule.class);
		BlockOutlineRenderState state = levelState.blockOutlineRenderState;
		if (module == null || !module.isEnabled() || state == null || state.highContrast()) {
			return;
		}
		Vec3 camera = levelState.cameraRenderState.pos;
		BlockPos pos = state.pos();
		poseStack.pushPose();
		poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
		module.submit(poseStack, collector, state, gameRenderer.useImprovedTransparency());
		poseStack.popPose();
		ci.cancel();
	}
}
