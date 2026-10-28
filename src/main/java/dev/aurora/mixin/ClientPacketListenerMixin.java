package dev.aurora.mixin;

import dev.aurora.module.hud.PickupsHud;
import dev.aurora.util.ServerTps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	private static final String ENSURE =
		"Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

	@Shadow
	private ClientLevel level;

	/** Picked up items, read before the game shrinks the stack on the ground (client thread only). */
	@Inject(method = "handleTakeItemEntity", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER))
	private void aurora$pickup(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || packet.getPlayerId() != mc.player.getId()) {
			return;
		}
		Entity from = level.getEntity(packet.getItemId());
		if (from instanceof ItemEntity item) {
			PickupsHud.onPickup(item.getItem(), packet.getAmount());
		} else if (from instanceof AbstractArrow arrow) {
			PickupsHud.onPickup(arrow.getPickupItemStackOrigin(), 1);
		}
	}

	/** Every time update the server sends feeds the tick rate estimate. */
	@Inject(method = "handleSetTime", at = @At(value = "INVOKE", target = ENSURE, shift = At.Shift.AFTER))
	private void aurora$serverTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
		ServerTps.onTimeUpdate(packet.gameTime());
	}
}
