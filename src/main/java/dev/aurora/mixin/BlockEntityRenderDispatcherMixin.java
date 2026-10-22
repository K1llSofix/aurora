package dev.aurora.mixin;

import dev.aurora.module.performance.PerformanceModule;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skips chests, signs, banners, heads and other block entities beyond the Performance module's
 * distance. Globally rendered ones (beacon beams) are left alone.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
	@Shadow
	private Vec3 cameraPos;

	@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
	private <E extends BlockEntity, S extends BlockEntityRenderState> void aurora$cullFar(E blockEntity, float partialTicks,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress, boolean isGloballyRendered, CallbackInfoReturnable<S> cir) {
		PerformanceModule performance = PerformanceModule.active();
		if (performance != null && !isGloballyRendered && cameraPos != null && performance.skipBlockEntity(blockEntity.getBlockPos(), cameraPos)) {
			cir.setReturnValue(null);
		}
	}
}
