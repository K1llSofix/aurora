package dev.aurora.mixin;

import dev.aurora.gui.screen.LoadingScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Aurora's splash instead of the Mojang Studios screen. The timing (fade in, fade out, closing
 * the overlay) is vanilla's, so loading itself is untouched.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	@Final
	private ReloadInstance reload;

	@Shadow
	@Final
	private boolean fadeIn;

	@Shadow
	private float currentProgress;

	@Shadow
	private long fadeOutStart;

	@Shadow
	private long fadeInStart;

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void aurora$splash(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (!LoadingScreens.enabled()) {
			return;
		}
		ci.cancel();
		long now = Util.getMillis();
		if (fadeIn && fadeInStart == -1L) {
			fadeInStart = now;
		}
		float fadeOutAnim = fadeOutStart > -1L ? (now - fadeOutStart) / 1000.0F : -1.0F;
		float fadeInAnim = fadeInStart > -1L ? (now - fadeInStart) / 500.0F : -1.0F;
		float alpha;
		if (fadeOutAnim >= 1.0F) {
			if (minecraft.gui.screen() != null) {
				minecraft.gui.screen().extractRenderStateWithTooltipAndSubtitles(graphics, 0, 0, a);
			} else {
				minecraft.gui.hud.extractDeferredSubtitles();
			}
			graphics.nextStratum();
			alpha = 1.0F - Mth.clamp(fadeOutAnim - 1.0F, 0.0F, 1.0F);
		} else if (fadeIn) {
			if (minecraft.gui.screen() != null && fadeInAnim < 1.0F) {
				minecraft.gui.screen().extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, a);
			} else {
				minecraft.gui.hud.extractDeferredSubtitles();
			}
			graphics.nextStratum();
			alpha = Mth.clamp(fadeInAnim, 0.15F, 1.0F);
		} else {
			alpha = 1.0F;
		}
		currentProgress = Mth.clamp(currentProgress * 0.95F + reload.getActualProgress() * 0.050000012F, 0.0F, 1.0F);
		LoadingScreens.splash(graphics, graphics.guiWidth(), graphics.guiHeight(), alpha, fadeOutAnim, currentProgress);
		if (fadeOutAnim >= 2.0F) {
			minecraft.gui.setOverlay(null);
		}
	}
}
