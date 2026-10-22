package dev.aurora.mixin;

import dev.aurora.gui.screen.PauseMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-lays out the pause menu in Aurora's style while keeping every vanilla button and action. */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
	@Shadow
	@Final
	private boolean showPauseMenu;

	@Shadow
	private @Nullable Button disconnectButton;

	@Unique
	private PauseMenu.@Nullable Layout aurora$layout;

	protected PauseScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void aurora$layout(CallbackInfo ci) {
		aurora$layout = null;
		if (showPauseMenu && PauseMenu.enabled()) {
			aurora$layout = PauseMenu.layout(this, children(), disconnectButton, this::addRenderableWidget);
		}
	}

	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void aurora$decorate(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (aurora$layout != null && showPauseMenu && PauseMenu.enabled()) {
			PauseMenu.decorate(graphics, this, aurora$layout, mouseX, mouseY);
		}
	}
}
