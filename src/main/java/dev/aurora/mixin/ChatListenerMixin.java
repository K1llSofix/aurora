package dev.aurora.mixin;

import com.mojang.authlib.GameProfile;
import dev.aurora.module.hud.ChatModule;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;

/** Remembers who sent the player chat message being shown, for its head in the chat. */
@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
	@Inject(method = "showMessageToPlayer", at = @At("HEAD"))
	private void aurora$sender(ChatType.Bound boundChatType, PlayerChatMessage message, Component decoratedMessage, GameProfile sender,
							   boolean onlyShowSecure, Instant received, CallbackInfoReturnable<Boolean> cir) {
		ChatModule.sender = sender;
	}

	@Inject(method = "showMessageToPlayer", at = @At("RETURN"))
	private void aurora$senderDone(ChatType.Bound boundChatType, PlayerChatMessage message, Component decoratedMessage, GameProfile sender,
								   boolean onlyShowSecure, Instant received, CallbackInfoReturnable<Boolean> cir) {
		ChatModule.sender = null;
	}
}
