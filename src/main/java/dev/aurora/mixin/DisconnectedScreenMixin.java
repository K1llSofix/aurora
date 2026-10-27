package dev.aurora.mixin;

import dev.aurora.module.utility.ReconnectModule;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.DisconnectedScreen;
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

/** The disconnect screen gets a "Reconnect" button above "Back to server list", with a countdown when automatic. */
@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
	@Shadow
	@Final
	private LinearLayout layout;

	@Unique
	private @Nullable Button aurora$reconnect;
	/** Ticks left until an automatic reconnect; -1 when there is none. */
	@Unique
	private int aurora$countdown = -1;

	protected DisconnectedScreenMixin(Component title) {
		super(title);
	}

	/** Right before the vanilla back button joins the layout, so ours stands above it. */
	@Inject(method = "init", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/layouts/LinearLayout;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;",
		ordinal = 2))
	private void aurora$reconnectButton(CallbackInfo ci) {
		ReconnectModule module = ReconnectModule.active();
		if (module == null || !module.canReconnect()) {
			return;
		}
		aurora$countdown = module.autoDelayTicks();
		aurora$reconnect = Button.builder(aurora$label(), b -> module.reconnect()).width(200).build();
		layout.addChild(aurora$reconnect);
	}

	@Override
	public void tick() {
		super.tick();
		if (aurora$reconnect == null || aurora$countdown < 0) {
			return;
		}
		if (--aurora$countdown <= 0) {
			aurora$countdown = -1;
			ReconnectModule module = ReconnectModule.active();
			if (module != null) {
				module.reconnectAutomatically();
			}
			return;
		}
		aurora$reconnect.setMessage(aurora$label());
	}

	@Unique
	private Component aurora$label() {
		return aurora$countdown > 0
			? Component.translatable("aurora.reconnect.button_auto", (aurora$countdown + 19) / 20)
			: Component.translatable("aurora.reconnect.button");
	}
}
