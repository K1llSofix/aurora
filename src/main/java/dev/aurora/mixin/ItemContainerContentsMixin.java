package dev.aurora.mixin;

import dev.aurora.module.utility.TooltipsModule;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemContainerContents.class)
public abstract class ItemContainerContentsMixin {
	/** With the slots shown in the tooltip, the vanilla list of the first five items would only repeat them. */
	@Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
	private void aurora$slotsInstead(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components, CallbackInfo ci) {
		if (TooltipsModule.showsContainers()) {
			ci.cancel();
		}
	}
}
