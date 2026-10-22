package dev.aurora.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.aurora.gui.screen.LoadingScreens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World creation progress ("Preparing...") as an Aurora loading card. */
@Mixin(ProgressScreen.class)
public abstract class ProgressScreenMixin extends Screen {
	@Shadow
	private @Nullable Component header;

	@Shadow
	private @Nullable Component stage;

	@Shadow
	private int progress;

	@Shadow
	private boolean stop;

	@Unique
	private final long aurora$openedAt = System.currentTimeMillis();

	protected ProgressScreenMixin(Component title) {
		super(title);
	}

	@WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
	private boolean aurora$texts(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
		return !LoadingScreens.enabled();
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void aurora$card(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (!LoadingScreens.enabled() || stop) {
			return;
		}
		String title = header != null ? header.getString() : getTitle().getString();
		String detail = stage != null ? stage.getString() + (progress > 0 ? "  " + progress + "%" : "") : "";
		LoadingScreens.card(graphics, width / 2f, height / 2f - 6, title, detail, progress > 0 ? progress / 100f : -1f);
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
