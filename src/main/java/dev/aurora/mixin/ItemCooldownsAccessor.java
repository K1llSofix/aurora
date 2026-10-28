package dev.aurora.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** The running cooldowns of your items, for the cooldowns HUD. */
@Mixin(ItemCooldowns.class)
public interface ItemCooldownsAccessor {
	@Accessor("cooldowns")
	Map<Identifier, Object> aurora$cooldowns();

	@Accessor("tickCount")
	int aurora$tickCount();
}
