package dev.aurora.module.utility;

import dev.aurora.gui.Theme;
import dev.aurora.gui.render.Render2D;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Locale;

/**
 * A search box above every chest and inventory: type, and the matching items light up while the
 * rest dim. It only highlights; nothing is moved for you. Ctrl+F jumps into the box.
 */
public final class InventorySearchModule extends Module {
	private final BooleanSetting inside = add(new BooleanSetting("inside", true));

	private static @Nullable InventorySearchModule instance;

	public InventorySearchModule() {
		super("inventory_search", Category.UTILITY, Items.SPYGLASS, true);
		instance = this;
	}

	public static @Nullable InventorySearchModule active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	/** Drawn over the slots (the pose stands at the container's corner): matches glow, the rest dim. */
	public void markSlots(GuiGraphicsExtractor g, AbstractContainerMenu menu, String query) {
		String q = query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return;
		}
		int accent = Theme.accent();
		for (Slot slot : menu.slots) {
			if (!slot.isActive() || !slot.hasItem()) {
				continue;
			}
			if (matches(slot.getItem(), q, inside.on())) {
				Render2D.roundRect(g, slot.x - 1, slot.y - 1, 18, 18, 3, (accent & 0xFFFFFF) | 0x30000000);
				Render2D.roundOutline(g, slot.x - 1, slot.y - 1, 18, 18, 3, 1f, accent);
			} else {
				Render2D.rect(g, slot.x, slot.y, 16, 16, 0xC00D0F14);
			}
		}
	}

	/** By the shown name, by the item's id ("diamond_sword" also as "diamond sword"), or by what it holds. */
	public static boolean matches(ItemStack stack, String query, boolean inside) {
		if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
			return true;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (id.contains(query) || id.replace('_', ' ').contains(query)) {
			return true;
		}
		if (!inside) {
			return false;
		}
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents != null) {
			Iterator<ItemStack> it = contents.nonEmptyItemCopyStream().iterator();
			while (it.hasNext()) {
				if (matches(it.next(), query, false)) {
					return true;
				}
			}
		}
		BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
		if (bundle != null) {
			for (ItemStack item : bundle.itemCopies().toList()) {
				if (matches(item, query, false)) {
					return true;
				}
			}
		}
		return false;
	}
}
