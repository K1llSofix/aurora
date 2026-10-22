package dev.aurora.mixin;

import dev.aurora.module.utility.ScreenshotsModule;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

/** Lets the Screenshots module act once a screenshot is saved. */
@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
	@ModifyVariable(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
		at = @At("HEAD"), argsOnly = true)
	private static Consumer<Component> aurora$saved(Consumer<Component> callback) {
		return ScreenshotsModule.wrap(callback);
	}
}
