package dev.aurora.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Aurora see whether an item's components are bound yet without triggering the null check. */
@Mixin(Holder.Reference.class)
public interface HolderReferenceAccessor {
	@Accessor("components")
	@Nullable DataComponentMap aurora$components();
}
