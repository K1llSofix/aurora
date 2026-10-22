package dev.aurora.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aurora.Aurora;
import dev.aurora.module.visual.FreelookModule;
import dev.aurora.module.visual.ZoomModule;
import dev.aurora.util.CpsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "onButton", at = @At("HEAD"))
	private void aurora$countClicks(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (action == 1 && minecraft.gui.screen() == null && handle == minecraft.getWindow().handle()) {
			CpsTracker.onClick(info.button());
			if (minecraft.player != null) {
				Aurora.onBindPress(InputConstants.Type.MOUSE.getOrCreate(info.button()));
			}
		}
	}

	/** While zooming, the wheel changes the zoom level instead of the hotbar slot. */
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void aurora$zoomScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		if (minecraft.gui.screen() != null || handle != minecraft.getWindow().handle()) {
			return;
		}
		ZoomModule zoom = Aurora.modules().get(ZoomModule.class);
		if (zoom != null && zoom.onScroll(yoffset)) {
			ci.cancel();
		}
	}

	/** Freelook steals mouse movement; zoom lowers the sensitivity. */
	@WrapOperation(method = "turnPlayer",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void aurora$turn(LocalPlayer player, double dx, double dy, Operation<Void> original) {
		ZoomModule zoom = Aurora.modules().get(ZoomModule.class);
		if (zoom != null) {
			double f = zoom.mouseFactor();
			dx *= f;
			dy *= f;
		}
		FreelookModule freelook = Aurora.modules().get(FreelookModule.class);
		if (freelook != null && freelook.active()) {
			freelook.turn(dx, dy);
			return;
		}
		original.call(player, dx, dy);
	}
}
