package dev.aurora.mixin;

import dev.aurora.gui.screen.LoadingScreens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Short waits such as "Saving world": the scene and a spinner above the message. */
@Mixin(GenericMessageScreen.class)
public abstract class GenericMessageScreenMixin extends Screen {
	@Shadow
	private @Nullable FocusableTextWidget textWidget;

	@Unique
	private final long aurora$openedAt = System.currentTimeMillis();

	protected GenericMessageScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "repositionElements", at = @At("TAIL"))
	private void aurora$hideText(CallbackInfo ci) {
		if (textWidget != null) {
			textWidget.visible = !LoadingScreens.enabled();
		}
	}

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void aurora$background(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (LoadingScreens.enabled()) {
			LoadingScreens.background(graphics, width, height, 1f);
			LoadingScreens.card(graphics, width / 2f, height / 2f - 6, title.getString(), I18n.get("aurora.loading.wait"), -1f);
			LoadingScreens.tip(graphics, width, height, aurora$openedAt);
			ci.cancel();
		}
	}
}
