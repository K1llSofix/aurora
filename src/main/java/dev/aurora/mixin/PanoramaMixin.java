package dev.aurora.mixin;

import dev.aurora.gui.background.MenuBackground;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Panorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every menu that shows the panorama gets Aurora's chosen background instead. */
@Mixin(Panorama.class)
public abstract class PanoramaMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void aurora$background(GuiGraphicsExtractor graphics, int width, int height, CallbackInfo ci) {
		if (MenuBackground.render(graphics, width, height)) {
			ci.cancel();
		}
	}
}
