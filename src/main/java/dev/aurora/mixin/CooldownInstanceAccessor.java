package dev.aurora.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Start and end tick of one item cooldown (a private record of {@code ItemCooldowns}). */
@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface CooldownInstanceAccessor {
	@Accessor("startTime")
	int aurora$startTime();

	@Accessor("endTime")
	int aurora$endTime();
}
