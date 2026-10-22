package dev.aurora.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** What Mouse Tweaks needs from an inventory screen: the slot under the mouse, clicks, the drag state. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Invoker("getHoveredSlot")
	@Nullable Slot aurora$slotAt(double x, double y);

	@Invoker("slotClicked")
	void aurora$slotClicked(Slot slot, int slotId, int buttonNum, ContainerInput containerInput);

	@Accessor("isQuickCrafting")
	boolean aurora$isQuickCrafting();

	@Accessor("isQuickCrafting")
	void aurora$setQuickCrafting(boolean quickCrafting);

	@Accessor("quickCraftingButton")
	int aurora$quickCraftingButton();

	@Accessor("skipNextRelease")
	void aurora$setSkipNextRelease(boolean skip);
}
