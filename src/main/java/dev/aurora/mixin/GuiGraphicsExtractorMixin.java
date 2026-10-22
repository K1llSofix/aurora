package dev.aurora.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.aurora.gui.VanillaStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
	/** Every widget sprite (buttons, sliders, fields, checkboxes, tabs, scrollbars, tooltips) passes here. */
	@Inject(method = "blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V",
		at = @At("HEAD"), cancellable = true)
	private void aurora$styleSprite(RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, int color, CallbackInfo ci) {
		if (VanillaStyle.sprite((GuiGraphicsExtractor) (Object) this, sprite, x, y, width, height, color)) {
			ci.cancel();
		}
	}

	/** Menu backgrounds and header / footer separators are plain textures drawn through here. */
	@Inject(method = "innerBlit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIFFFFI)V",
		at = @At("HEAD"), cancellable = true)
	private void aurora$styleTexture(RenderPipeline pipeline, Identifier location, int x0, int x1, int y0, int y1,
									 float u0, float u1, float v0, float v1, int color, CallbackInfo ci) {
		if (VanillaStyle.texture((GuiGraphicsExtractor) (Object) this, location, x0, y0, x1, y1)) {
			ci.cancel();
		}
	}
}
