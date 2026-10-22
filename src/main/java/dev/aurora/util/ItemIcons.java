package dev.aurora.util;

import dev.aurora.mixin.HolderReferenceAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Item stacks for icons that also work on the title screen. Since 26.x an item only gets its
 * components (model, stack size...) when a world's registries load, and creating a stack before
 * that throws. For an icon only the model matters, so until then the item gets its default
 * components with its own model; the first world load replaces them with the real data.
 */
public final class ItemIcons {
	private ItemIcons() {
	}

	private static @Nullable DataComponentMap components(Holder.Reference<Item> holder) {
		return ((HolderReferenceAccessor) (Object) holder).aurora$components();
	}

	public static ItemStack stack(Item item) {
		Holder.Reference<Item> holder = item.builtInRegistryHolder();
		if (components(holder) == null) {
			holder.bindComponents(DataComponentMap.builder()
				.addAll(DataComponents.COMMON_ITEM_COMPONENTS)
				.set(DataComponents.ITEM_MODEL, BuiltInRegistries.ITEM.getKey(item))
				.build());
		}
		return new ItemStack(item);
	}
}
