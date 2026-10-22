package dev.aurora.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.aurora.gui.screen.LoadingScreens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World loading in Aurora's style: the menu scene, a card with a spinner and the progress. */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {
	@Shadow
	@Final
	private static Component DOWNLOADING_TERRAIN_TEXT;

	@Shadow
	private LevelLoadTracker loadTracker;

	@Shadow
	private float smoothedProgress;

	@Shadow
	private LevelLoadingScreen.Reason reason;

	@Unique
	private final long aurora$openedAt = System.currentTimeMillis();

	protected LevelLoadingScreenMixin(Component title) {
		super(title);
	}

	@WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;extractChunksForRendering(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIILnet/minecraft/server/level/progress/ChunkLoadStatusView;)V"))
	private boolean aurora$chunkMap(GuiGraphicsExtractor graphics, int xCenter, int yCenter, int size, int margin, ChunkLoadStatusView view) {
		return !LoadingScreens.enabled();
	}

	@WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;centeredText(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
	private boolean aurora$title(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
		return !LoadingScreens.enabled();
	}

	@Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true)
	private void aurora$vanillaBar(GuiGraphicsExtractor graphics, int left, int top, int width, int height, float progress, CallbackInfo ci) {
		if (LoadingScreens.enabled()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void aurora$card(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (!LoadingScreens.enabled()) {
			return;
		}
		boolean known = loadTracker.hasProgress();
		String detail = known ? I18n.get("aurora.loading.percent", Math.round(smoothedProgress * 100)) : I18n.get("aurora.loading.wait");
		LoadingScreens.card(graphics, width / 2f, height / 2f - 6, DOWNLOADING_TERRAIN_TEXT.getString(), detail, known ? smoothedProgress : -1f);
		LoadingScreens.tip(graphics, width, height, aurora$openedAt);
	}

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void aurora$background(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (LoadingScreens.enabled() && reason == LevelLoadingScreen.Reason.OTHER) {
			LoadingScreens.background(graphics, width, height, 1f);
			ci.cancel();
		}
	}
}
