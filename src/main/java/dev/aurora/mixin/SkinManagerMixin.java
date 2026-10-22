package dev.aurora.mixin;

import com.mojang.authlib.GameProfile;
import dev.aurora.skin.LocalSkins;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Offline accounts with a skin from Aurora Launcher show it instead of a default skin. */
@Mixin(SkinManager.class)
public abstract class SkinManagerMixin {
	@Inject(method = "createLookup", at = @At("HEAD"), cancellable = true)
	private void aurora$localLookup(GameProfile profile, boolean requireSecure, CallbackInfoReturnable<Supplier<PlayerSkin>> cir) {
		PlayerSkin skin = LocalSkins.forProfile(profile.id());
		if (skin != null) {
			cir.setReturnValue(() -> skin);
		}
	}

	@Inject(method = "get", at = @At("HEAD"), cancellable = true)
	private void aurora$localSkin(GameProfile profile, CallbackInfoReturnable<CompletableFuture<Optional<PlayerSkin>>> cir) {
		PlayerSkin skin = LocalSkins.forProfile(profile.id());
		if (skin != null) {
			cir.setReturnValue(CompletableFuture.completedFuture(Optional.of(skin)));
		}
	}
}
