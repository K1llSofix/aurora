package dev.aurora.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aurora.module.hud.ChatModule;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Decorates new chat messages and hooks the background pass and slide-in of the chat. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements ChatModule.Access {
	private static final String EXTRACT =
		"extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V";

	@Shadow
	@Final
	private List<GuiMessage> allMessages;

	@Shadow
	@Final
	private List<GuiMessage.Line> trimmedMessages;

	@Shadow
	private int chatScrollbarPos;

	@Override
	public List<GuiMessage> aurora$messages() {
		return allMessages;
	}

	@Override
	public List<GuiMessage.Line> aurora$lines() {
		return trimmedMessages;
	}

	@Override
	public int aurora$scroll() {
		return chatScrollbarPos;
	}

	@ModifyVariable(
		method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
		at = @At("HEAD"), argsOnly = true)
	private Component aurora$decorate(Component contents) {
		return ChatModule.decorate(contents, this);
	}

	@Inject(method = "addMessageToQueue", at = @At("HEAD"))
	private void aurora$added(GuiMessage message, CallbackInfo ci) {
		ChatModule.onAdded(message);
	}

	@Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"))
	private void aurora$displayed(GuiMessage message, CallbackInfo ci) {
		ChatModule.onDisplayed(message, chatScrollbarPos);
	}

	@Inject(method = EXTRACT, at = @At("HEAD"))
	private void aurora$beginBackground(ChatComponent.ChatGraphicsAccess graphics, int screenHeight, int ticks,
										ChatComponent.DisplayMode displayMode, CallbackInfo ci) {
		ChatModule.beginBackground(screenHeight);
	}

	@Inject(method = EXTRACT, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/ChatComponent;forEachLine(Lnet/minecraft/client/gui/components/ChatComponent$AlphaCalculator;Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;)I",
		ordinal = 0, shift = At.Shift.AFTER))
	private void aurora$flushBackground(ChatComponent.ChatGraphicsAccess graphics, int screenHeight, int ticks,
									   ChatComponent.DisplayMode displayMode, CallbackInfo ci) {
		ChatModule.flushBackground();
	}

	@Inject(method = EXTRACT, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;updatePose(Ljava/util/function/Consumer;)V",
		shift = At.Shift.AFTER))
	private void aurora$slide(ChatComponent.ChatGraphicsAccess graphics, int screenHeight, int ticks,
							  ChatComponent.DisplayMode displayMode, CallbackInfo ci) {
		float offset = ChatModule.slideOffset();
		if (offset != 0) {
			graphics.updatePose(pose -> pose.translate(0, offset));
		}
	}

	/** Chat search: drawing, clicks, the scrollbar and scrolling see only the matching lines. */
	@ModifyExpressionValue(method = {"forEachLine", EXTRACT, "scrollChat"},
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/ChatComponent;trimmedMessages:Ljava/util/List;", opcode = Opcodes.GETFIELD))
	private List<GuiMessage.Line> aurora$searchLines(List<GuiMessage.Line> lines) {
		return ChatModule.visibleLines(lines);
	}
}
