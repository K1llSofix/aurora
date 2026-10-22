package dev.aurora.mixin;

import dev.aurora.Aurora;
import dev.aurora.module.visual.ParticlesModule;
import dev.aurora.util.CombatTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void aurora$trackAttack(Player player, Entity target, CallbackInfo ci) {
		CombatTracker.onAttack(target);
		ParticlesModule particles = Aurora.modules().get(ParticlesModule.class);
		if (particles != null) {
			particles.onAttack(target);
		}
	}
}
