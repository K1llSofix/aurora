package dev.aurora.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aurora.Aurora;
import dev.aurora.module.visual.TimeChangerModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance")
public abstract class ClientClockInstanceMixin {
	@ModifyReturnValue(method = "totalTicks", at = @At("RETURN"))
	private long aurora$time(long real) {
		TimeChangerModule time = Aurora.modules().get(TimeChangerModule.class);
		return time != null && time.isEnabled() ? time.totalTicks(real) : real;
	}

	@ModifyReturnValue(method = "partialTick", at = @At("RETURN"))
	private float aurora$partial(float real) {
		TimeChangerModule time = Aurora.modules().get(TimeChangerModule.class);
		return time != null && time.isEnabled() ? time.partialTick() : real;
	}
}
