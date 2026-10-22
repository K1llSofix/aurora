package dev.aurora.mixin;

import dev.aurora.Aurora;
import dev.aurora.module.visual.FullbrightModule;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapExtractorMixin {
	/** Fullbright: behave as if the player had Night Vision. */
	@Inject(method = "extract", at = @At("RETURN"))
	private void aurora$fullbright(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		FullbrightModule fullbright = Aurora.modules().get(FullbrightModule.class);
		if (fullbright != null && fullbright.isEnabled()) {
			state.nightVisionEffectIntensity = Math.max(state.nightVisionEffectIntensity, fullbright.strength());
		}
	}
}
