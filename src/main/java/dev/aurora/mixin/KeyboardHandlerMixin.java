package dev.aurora.mixin;

import dev.aurora.Aurora;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	/**
	 * Module toggle keys react to the press event itself, so even a tap shorter than one game tick
	 * is never lost (polling the key state each tick would miss it).
	 */
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void aurora$moduleBinds(long handle, int action, KeyEvent event, CallbackInfo ci) {
		if (action == 1 && handle == minecraft.getWindow().handle() && minecraft.gui.screen() == null
			&& minecraft.player != null && !minecraft.options.keyDebugModifier.isDown()) {
			Aurora.onKeyPress(event.key());
		}
	}
}
