package dev.aurora.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.module.hud.ChatModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Glass input bar, right-click to copy a message and Ctrl+F to search the chat. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
	@Shadow
	protected EditBox input;

	@Unique
	private String aurora$draft = "";

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

	/** Ctrl+F searches the chat: the input becomes the search box until Enter or Escape. */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void aurora$search(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (!ChatModule.searchAvailable()) {
			return;
		}
		boolean searching = ChatModule.searching();
		if (event.key() == InputConstants.KEY_F && event.hasControlDownWithQuirk()) {
			aurora$setSearching(!searching);
			cir.setReturnValue(true);
		} else if (searching && (event.isEscape() || event.isConfirmation())) {
			aurora$setSearching(false);
			cir.setReturnValue(true);
		}
	}

	@Unique
	private void aurora$setSearching(boolean on) {
		if (on) {
			aurora$draft = input.getValue();
			input.setValue("");
			input.setHint(Component.translatable("aurora.chat.search_hint"));
		} else {
			input.setValue(aurora$draft);
			input.setHint(Component.empty());
		}
		ChatModule.setSearching(on);
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void aurora$searchQuery(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (ChatModule.searching()) {
			ChatModule.setQuery(input.getValue());
		}
	}

	/** Closing while searching keeps the unsent message, not the search text. */
	@Inject(method = "removed", at = @At("HEAD"))
	private void aurora$endSearch(CallbackInfo ci) {
		if (ChatModule.searching()) {
			input.setValue(aurora$draft);
		}
		ChatModule.setSearching(false);
	}
}
