package dev.aurora.mixin;

import dev.aurora.module.hud.ChatModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The chat's line backgrounds are collected by Aurora and drawn as one rounded panel. */
@Mixin(targets = {
	"net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess",
	"net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess"
}, remap = false)
public abstract class ChatGraphicsAccessMixin {
	@Shadow(remap = false)
	@Final
	private GuiGraphicsExtractor graphics;

	@Inject(method = "fill", at = @At("HEAD"), cancellable = true, remap = false)
	private void aurora$fill(int x0, int y0, int x1, int y1, int color, CallbackInfo ci) {
		if (ChatModule.recordBackground(graphics, x0, y0, x1, y1, color)) {
			ci.cancel();
		}
	}
}
