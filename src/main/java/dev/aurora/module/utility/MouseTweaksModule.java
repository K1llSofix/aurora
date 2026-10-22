/*
 * The inventory tweaks in this file are ported from Mouse Tweaks by Ivan Molodetskikh (YaLTeR),
 * https://github.com/YaLTeR/MouseTweaks, under the BSD 3-Clause License:
 *
 * Copyright (c) 2023, Ivan Molodetskikh
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package dev.aurora.module.utility;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.mixin.AbstractContainerScreenAccessor;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.ModeSetting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mouse Tweaks, as in the mod of the same name, in every inventory screen (the creative inventory
 * too):
 * <ul>
 * <li>RMB tweak: dragging with the right button and a stack on the cursor puts one item into every
 * slot it passes, again on every pass;</li>
 * <li>LMB tweak with an item: press on a stack and drag over slots with the same item to gather them
 * onto the cursor; with Shift, they are moved to the other inventory instead;</li>
 * <li>LMB tweak without an item: Shift + left button dragged across slots moves every stack;</li>
 * <li>wheel tweak: scrolling over a stack moves items one by one to the other inventory or back.</li>
 * </ul>
 * Everything is done with ordinary clicks, so it works on any server.
 */
public final class MouseTweaksModule extends Module {
	private final BooleanSetting rmbTweak = add(new BooleanSetting("rmb_tweak", true));
	private final BooleanSetting lmbWithItem = add(new BooleanSetting("lmb_with_item", true));
	private final BooleanSetting lmbWithoutItem = add(new BooleanSetting("lmb_without_item", true));
	private final BooleanSetting wheel = add(new BooleanSetting("wheel", true));
	private final ModeSetting direction = add(new ModeSetting("wheel_direction", "normal",
		"normal", "inverted", "position", "position_inverted")).visibleWhen(wheel::on);
	private final ModeSetting order = add(new ModeSetting("wheel_order", "last_to_first",
		"last_to_first", "first_to_last")).visibleWhen(wheel::on);
	private final ModeSetting scaling = add(new ModeSetting("wheel_scaling", "proportional",
		"proportional", "always_one")).visibleWhen(wheel::on);

	/** The two buttons the tweaks use, with their container click numbers. */
	private enum Button {
		LEFT(0), RIGHT(1);

		final int click;

		Button(int click) {
			this.click = click;
		}

		static @Nullable Button of(MouseButtonEvent event) {
			return switch (event.button()) {
				case InputConstants.MOUSE_BUTTON_LEFT -> LEFT;
				case InputConstants.MOUSE_BUTTON_RIGHT -> RIGHT;
				default -> null;
			};
		}
	}

	// State for the screen that is open, reset when another one opens.
	private @Nullable Screen openScreen;
	private @Nullable AbstractContainerScreen<?> screen;
	private @Nullable Slot oldSelectedSlot;
	private double accumulatedScrollDelta;
	private boolean canDoLMBDrag;
	private boolean canDoRMBDrag;
	private boolean rmbTweakLeftOriginalSlot;

	public MouseTweaksModule() {
		super("mouse_tweaks", Category.UTILITY, Items.CHEST, true);
		// Same hooks as Mouse Tweaks itself: they reach every screen, the creative inventory included.
		ScreenEvents.BEFORE_INIT.register((client, opened, width, height) -> {
			ScreenMouseEvents.allowMouseClick(opened).register((s, event) -> {
				Button button = Button.of(event);
				return button == null || !onMouseClicked(s, event.x(), event.y(), button);
			});
			ScreenMouseEvents.allowMouseDrag(opened).register((s, event, dx, dy) -> {
				Button button = Button.of(event);
				return button == null || !onMouseDrag(s, event.x(), event.y(), button);
			});
			ScreenMouseEvents.allowMouseRelease(opened).register((s, event) -> {
				Button button = Button.of(event);
				return button == null || !onMouseReleased(s, button);
			});
			ScreenMouseEvents.afterMouseScroll(opened).register((s, x, y, horizontal, vertical, consumed) ->
				!consumed && onMouseScrolled(s, x, y, vertical));
		});
	}

	private void updateScreen(Screen newScreen) {
		if (newScreen == openScreen) {
			return;
		}
		openScreen = newScreen;
		screen = newScreen instanceof AbstractContainerScreen<?> container ? container : null;
		oldSelectedSlot = null;
		accumulatedScrollDelta = 0;
		canDoLMBDrag = false;
		canDoRMBDrag = false;
		rmbTweakLeftOriginalSlot = false;
	}

	/** The screen the tweaks work in right now, or null (module off, not an inventory). */
	private @Nullable AbstractContainerScreen<?> active(Screen current) {
		updateScreen(current);
		return isEnabled() && mc.player != null ? screen : null;
	}

	// ======================================================================= buttons

	/** Returns true if the event was handled and should be cancelled (never, as in Mouse Tweaks). */
	private boolean onMouseClicked(Screen current, double x, double y, Button button) {
		AbstractContainerScreen<?> container = active(current);
		if (container == null) {
			return false;
		}
		// Store the currently selected slot.
		oldSelectedSlot = slotAt(container, x, y);
		// Stack that the player is currently "holding" on the mouse cursor.
		ItemStack stackOnMouse = mc.player.containerMenu.getCarried();
		if (button == Button.LEFT) {
			// If the stack on mouse isn't empty, the vanilla LMB dragging mechanic is going to start. We don't
			// want to interfere with that.
			if (stackOnMouse.isEmpty()) {
				canDoLMBDrag = true;
			}
		} else {
			// We're only interested in the RMB tweak when there's something on the mouse, and only when it is
			// enabled right away, as otherwise the vanilla RMB dragging mechanic should occur.
			if (stackOnMouse.isEmpty() || !rmbTweak.on()) {
				return false;
			}
			canDoRMBDrag = true;
			rmbTweakLeftOriginalSlot = false;
		}
		return false;
	}

	/** Clicks a slot as RMB tweak. */
	private void rmbTweakMaybeClickSlot(AbstractContainerScreen<?> container, @Nullable Slot slot, ItemStack stackOnMouse) {
		// Can't click if there's no slot or nothing on the mouse, or on ignored slots.
		if (slot == null || stackOnMouse.isEmpty() || isIgnored(container, slot)) {
			return;
		}
		// Can't put items into crafting output slots.
		if (isCraftingOutput(slot)) {
			return;
		}
		// Skip two conditions below for bundles to enable RMB-dragging with a bundle to put items into it.
		if (!(stackOnMouse.getItem() instanceof BundleItem)) {
			// If the stacks are incompatible, we can't right click.
			ItemStack selectedSlotStack = slot.getItem();
			if (!areStacksCompatible(selectedSlotStack, stackOnMouse)) {
				return;
			}
			// Return if we cannot put any more items into the slot.
			if (selectedSlotStack.getCount() == slot.getMaxStackSize(selectedSlotStack)) {
				return;
			}
		}
		clickSlot(container, slot, Button.RIGHT, false);
	}

	private boolean onMouseReleased(Screen current, Button button) {
		if (active(current) == null) {
			return false;
		}
		// Reset the flags.
		if (button == Button.LEFT) {
			canDoLMBDrag = false;
		} else {
			canDoRMBDrag = false;
		}
		return false;
	}

	private boolean onMouseDrag(Screen current, double x, double y, Button button) {
		AbstractContainerScreen<?> container = active(current);
		if (container == null) {
			return false;
		}
		Slot selectedSlot = slotAt(container, x, y);
		// If the mouse hasn't moved to a new slot, we don't need to do anything.
		if (selectedSlot == oldSelectedSlot) {
			return false;
		}
		// Stack that the player is currently "holding" on the mouse cursor.
		ItemStack stackOnMouse = mc.player.containerMenu.getCarried();

		// When leaving the original slot for the first time, set the flag and click it to put an item there.
		if (canDoRMBDrag && button == Button.RIGHT && !rmbTweakLeftOriginalSlot) {
			rmbTweakLeftOriginalSlot = true;
			disableRMBDraggingFunctionality(container);
			rmbTweakMaybeClickSlot(container, oldSelectedSlot, stackOnMouse);
		}
		oldSelectedSlot = selectedSlot;

		// If no slot was selected, or it is ignored, we don't need to do anything.
		if (selectedSlot == null || isIgnored(container, selectedSlot)) {
			return false;
		}
		// At this point the mouse has just entered a new, non-ignored slot.
		if (button == Button.LEFT) {
			if (!canDoLMBDrag) {
				return false;
			}
			// LMB tweaks don't do anything for empty slots.
			ItemStack selectedSlotStack = selectedSlot.getItem();
			if (selectedSlotStack.isEmpty()) {
				return false;
			}
			boolean shiftIsDown = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
			if (stackOnMouse.isEmpty()) {
				// Shift-LMB drag without item: shift-left click the newly selected slot.
				if (!lmbWithoutItem.on() || !shiftIsDown) {
					return false;
				}
				clickSlot(container, selectedSlot, Button.LEFT, true);
			} else {
				// (Shift-)LMB drag with item; here we only click on slots with the same item.
				if (!lmbWithItem.on() || !areStacksCompatible(selectedSlotStack, stackOnMouse)) {
					return false;
				}
				if (shiftIsDown) {
					// If shift is down, just shift-click on the slot, and the items get moved to the other inventory.
					clickSlot(container, selectedSlot, Button.LEFT, true);
				} else {
					// If shift is not down, we need to merge the item stack on the mouse with the one in the slot.
					// However, if the slot stack contains more items than can still fit on mouse, clicking on it
					// would fill the slot to the maximum and leave only the rest on the mouse, without any way to
					// get the items back onto the mouse, which is not what we want for the LMB tweak.
					if (stackOnMouse.getCount() + selectedSlotStack.getCount() > stackOnMouse.getMaxStackSize()) {
						return false;
					}
					// Click on the slot so that our stack gets merged with it, and then again to return the stack
					// to the mouse. A crafting output slot adds the item to the mouse stack on the first click, so
					// it needs no second one.
					clickSlot(container, selectedSlot, Button.LEFT, false);
					if (!isCraftingOutput(selectedSlot)) {
						clickSlot(container, selectedSlot, Button.LEFT, false);
					}
				}
			}
		} else if (canDoRMBDrag) {
			rmbTweakMaybeClickSlot(container, selectedSlot, stackOnMouse);
		}
		return false;
	}

	// ======================================================================= wheel

	/** Returns true if Mouse Tweaks handled the scroll. */
	private boolean onMouseScrolled(Screen current, double x, double y, double scrollDelta) {
		AbstractContainerScreen<?> container = active(current);
		if (container == null || !wheel.on()) {
			return false;
		}
		// Check if the scroll is above a non-ignored slot.
		Slot selectedSlot = slotAt(container, x, y);
		if (selectedSlot == null || isIgnored(container, selectedSlot)) {
			return false;
		}
		ItemStack selectedSlotStack = selectedSlot.getItem();

		// If we're above a valid slot, always handle the scroll, even if no items are moved. This prevents
		// surprises when e.g. the items run out and suddenly scrolling starts doing something else.

		// Perform scroll delta scaling and accumulation the same way the game does it.
		double scaledDelta = scaling.is("always_one") ? Math.signum(scrollDelta) : scrollDelta;
		if (accumulatedScrollDelta != 0 && Math.signum(scaledDelta) != Math.signum(accumulatedScrollDelta)) {
			accumulatedScrollDelta = 0;
		}
		accumulatedScrollDelta += scaledDelta;
		// delta is the number of items to move, sign controls the direction.
		int delta = (int) accumulatedScrollDelta;
		accumulatedScrollDelta -= delta;
		if (delta == 0) {
			return true;
		}

		List<Slot> slots = container.getMenu().slots;
		int numItemsToMove = Math.abs(delta);
		boolean pushItems = delta < 0;
		if (direction.is("position") || direction.is("position_inverted")) {
			if (otherInventoryIsAbove(selectedSlot, slots)) {
				pushItems = !pushItems;
			}
		}
		if (direction.is("inverted") || direction.is("position_inverted")) {
			pushItems = !pushItems;
		}
		if (selectedSlotStack.isEmpty()) {
			return true;
		}
		ItemStack stackOnMouse = mc.player.containerMenu.getCarried();

		// Scrolling over a crafting output slot requires special handling as those slots behave differently.
		if (isCraftingOutput(selectedSlot)) {
			// Pulling from a crafting output slot only works if the stack is compatible to that on the mouse.
			if (!areStacksCompatible(selectedSlotStack, stackOnMouse)) {
				return true;
			}
			if (stackOnMouse.isEmpty()) {
				// Can't pull into the crafting output slot.
				if (!pushItems) {
					return true;
				}
				while (numItemsToMove-- > 0) {
					// Crafting outputs batches of items, we need to be able to distribute the whole batch at once.
					List<Slot> targetSlots = findPushSlots(container, slots, selectedSlot, selectedSlotStack.getCount(), true);
					if (targetSlots == null) {
						break;
					}
					// Grab the item batch from the crafting output slot, then distribute it.
					clickSlot(container, selectedSlot, Button.LEFT, false);
					for (int i = 0; i < targetSlots.size(); i++) {
						Slot slot = targetSlots.get(i);
						if (i == targetSlots.size() - 1) {
							// The last slot takes the remaining items with a left click.
							clickSlot(container, slot, Button.LEFT, false);
						} else {
							// Otherwise right click the needed number of times.
							int clickTimes = slot.getMaxStackSize(slot.getItem()) - slot.getItem().getCount();
							while (clickTimes-- > 0) {
								clickSlot(container, slot, Button.RIGHT, false);
							}
						}
					}
				}
			} else {
				// Retrieve items from the slot by left-clicking it.
				while (numItemsToMove-- > 0) {
					clickSlot(container, selectedSlot, Button.LEFT, false);
				}
			}
			return true;
		}

		// It's not possible to interact with the slot cleanly if the player is holding a compatible stack.
		if (!stackOnMouse.isEmpty() && areStacksCompatible(selectedSlotStack, stackOnMouse)) {
			return true;
		}

		if (pushItems) {
			// If the stack on mouse isn't empty, it should be possible to put it into the selected slot.
			if (!stackOnMouse.isEmpty() && !selectedSlot.mayPlace(stackOnMouse)) {
				return true;
			}
			// Clip the number of items to move by the available item count.
			numItemsToMove = Math.min(numItemsToMove, selectedSlotStack.getCount());
			List<Slot> targetSlots = findPushSlots(container, slots, selectedSlot, numItemsToMove, false);
			// If no target slots were found, don't pick up the items in the first place.
			if (targetSlots == null || targetSlots.isEmpty()) {
				return true;
			}
			// Remember if we had an item on mouse to pick it back up.
			boolean hadItemOnMouse = !stackOnMouse.isEmpty();

			// Right click picks up the items in most cases: it keeps a furnace's progress and swaps bundles as
			// wanted. Left click is better when the cursor is empty and we need the whole (small) stack.
			Button pickUpButton = Button.RIGHT;
			if (stackOnMouse.isEmpty() && selectedSlotStack.getCount() <= numItemsToMove) {
				pickUpButton = Button.LEFT;
			}
			clickSlot(container, selectedSlot, pickUpButton, false);

			// Fetch how many items we picked up rather than computing it ourselves, just in case.
			ItemStack pickedUpStack = mc.player.containerMenu.getCarried();
			numItemsToMove = Math.min(numItemsToMove, pickedUpStack.getCount());

			// Click the target slots: right click the needed number of times.
			for (Slot slot : targetSlots) {
				int clickTimes = slot.getMaxStackSize(pickedUpStack) - slot.getItem().getCount();
				clickTimes = Math.min(clickTimes, numItemsToMove);
				numItemsToMove -= clickTimes;
				while (clickTimes-- > 0) {
					clickSlot(container, slot, Button.RIGHT, false);
				}
			}

			// Click the selected slot again to put down left-over items and pick up whatever was on mouse.
			boolean hasLeftoverItems = !mc.player.containerMenu.getCarried().isEmpty();
			if (hadItemOnMouse || hasLeftoverItems) {
				// Swapping with a different item (rather than putting back part of the stack) wants a right click,
				// even with bundles, but only while still carrying something, or it would pick up half.
				Button putDownButton = hadItemOnMouse && hasLeftoverItems ? Button.RIGHT : Button.LEFT;
				clickSlot(container, selectedSlot, putDownButton, false);
			}
			return true;
		}

		// Handle pulling items: clip the number of items to move by what still fits in the slot.
		int maxItemsToMove = selectedSlot.getMaxStackSize(selectedSlotStack) - selectedSlotStack.getCount();
		numItemsToMove = Math.min(numItemsToMove, maxItemsToMove);
		while (numItemsToMove > 0) {
			Slot targetSlot = findPullSlot(container, slots, selectedSlot);
			if (targetSlot == null) {
				break;
			}
			ItemStack targetSlotStack = targetSlot.getItem();
			int numItemsInTargetSlot = targetSlotStack.getCount();

			if (isCraftingOutput(targetSlot)) {
				// One wheel tick pulls one batch of crafted items, and only one batch at a time, so nothing odd
				// happens when the ingredients run out and the output changes.
				if (maxItemsToMove < numItemsInTargetSlot) {
					break;
				}
				maxItemsToMove -= numItemsInTargetSlot;
				numItemsToMove = Math.min(numItemsToMove - 1, maxItemsToMove);
				// If the stack on mouse isn't empty, it should be possible to put it into the selected slot.
				if (!stackOnMouse.isEmpty() && !selectedSlot.mayPlace(stackOnMouse)) {
					break;
				}
				// Put down the stack on mouse and pick up the items, take the crafted batch, put the items back.
				clickSlot(container, selectedSlot, Button.LEFT, false);
				clickSlot(container, targetSlot, Button.LEFT, false);
				clickSlot(container, selectedSlot, Button.LEFT, false);
				continue;
			}

			// Remember if we had an item on mouse to pick it back up.
			boolean hadItemOnMouse = !stackOnMouse.isEmpty();
			// If the stack on mouse isn't empty, it should be possible to put it into the target slot.
			if (hadItemOnMouse && !targetSlot.mayPlace(stackOnMouse)) {
				break;
			}
			// Click the target slot to pick up items (right click in most cases, see the push logic).
			Button pickUpButton = stackOnMouse.isEmpty() && targetSlotStack.getCount() == 1 ? Button.LEFT : Button.RIGHT;
			clickSlot(container, targetSlot, pickUpButton, false);

			int numPickedUp = mc.player.containerMenu.getCarried().getCount();
			int numItemsToMoveFromTargetSlot = Math.min(numPickedUp, numItemsToMove);
			if (numItemsToMoveFromTargetSlot == numPickedUp) {
				// If we want to move all picked-up items, just left click the selected slot.
				clickSlot(container, selectedSlot, Button.LEFT, false);
			} else {
				// Otherwise right click the required number of times.
				for (int i = 0; i < numItemsToMoveFromTargetSlot; i++) {
					clickSlot(container, selectedSlot, Button.RIGHT, false);
				}
			}
			maxItemsToMove -= numItemsToMoveFromTargetSlot;
			numItemsToMove -= numItemsToMoveFromTargetSlot;

			// Click the target slot again to put down left-over items and pick up whatever was on mouse.
			boolean hasLeftoverItems = !mc.player.containerMenu.getCarried().isEmpty();
			if (hadItemOnMouse || hasLeftoverItems) {
				Button putDownButton = hadItemOnMouse && hasLeftoverItems ? Button.RIGHT : Button.LEFT;
				clickSlot(container, targetSlot, putDownButton, false);
			}
		}
		return true;
	}

	/**
	 * Whether the other inventory is above the selected slot, for the position aware scroll direction.
	 * Uses the same idea of the "other" inventory as everywhere else.
	 */
	private boolean otherInventoryIsAbove(Slot selectedSlot, List<Slot> slots) {
		boolean selectedIsInPlayerInventory = selectedSlot.container == mc.player.getInventory();
		int otherInventorySlotsBelow = 0;
		int otherInventorySlotsAbove = 0;
		for (Slot slot : slots) {
			if ((slot.container == mc.player.getInventory()) != selectedIsInPlayerInventory) {
				if (slot.y < selectedSlot.y) {
					otherInventorySlotsAbove++;
				} else {
					otherInventorySlotsBelow++;
				}
			}
		}
		return otherInventorySlotsAbove > otherInventorySlotsBelow;
	}

	/** Can items from one stack go into the other? Unlike the game's own check, empty stacks fit anything. */
	private static boolean areStacksCompatible(ItemStack a, ItemStack b) {
		return a.isEmpty() || b.isEmpty() || (ItemStack.isSameItem(a, b) && ItemStack.isSameItemSameComponents(a, b));
	}

	/** A slot to pull items from into {@code selectedSlot}, from the other inventory. */
	private @Nullable Slot findPullSlot(AbstractContainerScreen<?> container, List<Slot> slots, Slot selectedSlot) {
		boolean firstToLast = order.is("first_to_last");
		int startIndex = firstToLast ? 0 : slots.size() - 1;
		int endIndex = firstToLast ? slots.size() : -1;
		int step = firstToLast ? 1 : -1;
		ItemStack selectedSlotStack = selectedSlot.getItem();
		boolean findInPlayerInventory = selectedSlot.container != mc.player.getInventory();
		for (int i = startIndex; i != endIndex; i += step) {
			Slot slot = slots.get(i);
			if (isIgnored(container, slot)) {
				continue;
			}
			boolean slotInPlayerInventory = slot.container == mc.player.getInventory();
			if (findInPlayerInventory != slotInPlayerInventory) {
				continue;
			}
			ItemStack stack = slot.getItem();
			// Can't pull from an empty or incompatible stack.
			if (stack.isEmpty() || !areStacksCompatible(selectedSlotStack, stack)) {
				continue;
			}
			return slot;
		}
		return null;
	}

	/**
	 * Slots in the other inventory that can take {@code itemCount} items from {@code selectedSlot}, in
	 * order: partly filled stacks of the same item first, then empty slots. With
	 * {@code mustDistributeAll}, null when not all of the items fit.
	 */
	private @Nullable List<Slot> findPushSlots(AbstractContainerScreen<?> container, List<Slot> slots, Slot selectedSlot,
											   int itemCount, boolean mustDistributeAll) {
		ItemStack selectedSlotStack = selectedSlot.getItem();
		boolean findInPlayerInventory = selectedSlot.container != mc.player.getInventory();
		List<Slot> result = new ArrayList<>();
		// Applicable empty slots, used once applicable non-empty slots run out.
		List<Slot> goodEmptySlots = new ArrayList<>();
		for (int i = 0; i != slots.size() && itemCount > 0; i++) {
			Slot slot = slots.get(i);
			if (isIgnored(container, slot)) {
				continue;
			}
			boolean slotInPlayerInventory = slot.container == mc.player.getInventory();
			if (findInPlayerInventory != slotInPlayerInventory || isCraftingOutput(slot)) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				// Empty slots need to be able to accept the target item.
				if (slot.mayPlace(selectedSlotStack)) {
					goodEmptySlots.add(slot);
				}
			} else if (areStacksCompatible(selectedSlotStack, stack) && stack.getCount() < slot.getMaxStackSize(stack)) {
				// Non-empty slots should have a compatible stack, not maxed out.
				result.add(slot);
				itemCount -= Math.min(itemCount, slot.getMaxStackSize(stack) - stack.getCount());
			}
		}
		// If some items still weren't distributed, use the empty slots.
		for (int i = 0; i != goodEmptySlots.size() && itemCount > 0; i++) {
			Slot slot = goodEmptySlots.get(i);
			result.add(slot);
			itemCount -= Math.min(itemCount, slot.getMaxStackSize(selectedSlotStack));
		}
		if (mustDistributeAll && itemCount > 0) {
			return null;
		}
		return result;
	}

	// ======================================================================= the screen

	private static @Nullable Slot slotAt(AbstractContainerScreen<?> container, double x, double y) {
		return ((AbstractContainerScreenAccessor) container).aurora$slotAt(x, y);
	}

	private static void clickSlot(AbstractContainerScreen<?> container, Slot slot, Button button, boolean shift) {
		((AbstractContainerScreenAccessor) container).aurora$slotClicked(slot, slot.index, button.click,
			shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP);
	}

	/** Stops the game's own right-button drag, so releasing the button adds nothing more. */
	private static void disableRMBDraggingFunctionality(AbstractContainerScreen<?> container) {
		AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) container;
		access.aurora$setSkipNextRelease(true);
		if (access.aurora$isQuickCrafting() && access.aurora$quickCraftingButton() == InputConstants.MOUSE_BUTTON_RIGHT) {
			access.aurora$setQuickCrafting(false);
		}
	}

	private static boolean isCraftingOutput(Slot slot) {
		return slot instanceof ResultSlot || slot instanceof FurnaceResultSlot || slot instanceof MerchantResultSlot;
	}

	/** In the creative inventory only the player's own slots take part, not the item catalogue. */
	private boolean isIgnored(AbstractContainerScreen<?> container, Slot slot) {
		return container instanceof CreativeModeInventoryScreen && slot.container != mc.player.getInventory();
	}
}
