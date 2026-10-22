package dev.aurora.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.aurora.gui.screen.LoadingScreens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Connecting to a server: the status in a card with a spinner over the menu scene. */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {
	@Shadow
	private Component status;

	@Unique
	private final long aurora$openedAt = System.currentTimeMillis();

	protected ConnectScreenMixin(Component title) {
		super(title);
	}

	@WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
	private boolean aurora$status(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
		return !LoadingScreens.enabled();
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void aurora$card(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (!LoadingScreens.enabled()) {
			return;
		}
		ServerData server = minecraft.getCurrentServer();
		String detail = server != null ? server.name : I18n.get("aurora.loading.wait");
		LoadingScreens.card(graphics, width / 2f, height / 2f - 24, status.getString(), detail, -1f);
		LoadingScreens.tip(graphics, width, height, aurora$openedAt);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (LoadingScreens.enabled()) {
			LoadingScreens.background(graphics, width, height, 1f);
		} else {
			super.extractBackground(graphics, mouseX, mouseY, a);
		}
	}
}
