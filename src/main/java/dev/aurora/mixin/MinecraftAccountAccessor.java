package dev.aurora.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.services.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

/** Everything in {@link Minecraft} that belongs to the signed-in account, so it can be switched. */
@Mixin(Minecraft.class)
public interface MinecraftAccountAccessor {
	@Mutable
	@Accessor("user")
	void aurora$setUser(User user);

	@Mutable
	@Accessor("profileFuture")
	void aurora$setProfileFuture(CompletableFuture<ProfileResult> future);

	@Mutable
	@Accessor("userApiService")
	void aurora$setUserApiService(UserApiService service);

	@Mutable
	@Accessor("userPropertiesFuture")
	void aurora$setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> future);

	@Mutable
	@Accessor("playerSocialManager")
	void aurora$setPlayerSocialManager(PlayerSocialManager manager);

	@Accessor("remoteFriendListUpdateHandler")
	RemoteFriendListUpdateHandler aurora$friendHandler();

	@Mutable
	@Accessor("remoteFriendListUpdateHandler")
	void aurora$setFriendHandler(RemoteFriendListUpdateHandler handler);

	@Accessor("telemetryManager")
	ClientTelemetryManager aurora$telemetry();

	@Mutable
	@Accessor("telemetryManager")
	void aurora$setTelemetry(ClientTelemetryManager manager);

	@Mutable
	@Accessor("profileKeyPairManager")
	void aurora$setProfileKeyPairManager(ProfileKeyPairManager manager);

	@Accessor("reportingContext")
	void aurora$setReportingContext(ReportingContext context);
}
