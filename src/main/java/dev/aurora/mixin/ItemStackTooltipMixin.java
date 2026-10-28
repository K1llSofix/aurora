package dev.aurora.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aurora.module.utility.TooltipsModule;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
	/** Smart tooltips: a container's slots or a map's picture, when the item has no picture of its own. */
	@ModifyReturnValue(method = "getTooltipImage", at = @At("RETURN"))
	private Optional<TooltipComponent> aurora$preview(Optional<TooltipComponent> image) {
		return image.isPresent() ? image : TooltipsModule.image((ItemStack) (Object) this);
	}
}
