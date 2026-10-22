package dev.aurora.mixin;

import com.mojang.realmsclient.client.RealmsClient;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Realms keeps one client with the session it was created with; it is dropped on account switch. */
@Mixin(RealmsClient.class)
public interface RealmsClientAccessor {
	@Accessor("realmsClientInstance")
	static void aurora$setInstance(@Nullable RealmsClient client) {
		throw new AssertionError();
	}
}
