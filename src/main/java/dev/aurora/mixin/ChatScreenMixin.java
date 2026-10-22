package dev.aurora.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aurora.module.hud.ChatModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Glass input bar and right-click to copy a message. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
	protected ChatScreenMixin(Component title) {
		super(title);
	}

	@WrapOperation(method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
	private void aurora$inputBar(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
		if (!ChatModule.drawInputBar(graphics, x0, y0, x1, y1)) {
			original.call(graphics, x0, y0, x1, y1, color);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void aurora$copy(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (ChatModule.copyAt(event.x(), event.y(), event.button(), this.height)) {
			cir.setReturnValue(true);
		}
	}
}
