package dev.aurora.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.gui.widget.TextField;
import dev.aurora.module.utility.InventorySearchModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Inventory search: a box above the container, highlights in its slots, and the keys while typing. */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerSearchMixin extends Screen {
	@Shadow
	@Final
	protected AbstractContainerMenu menu;

	@Shadow
	protected int leftPos;

	@Shadow
	protected int topPos;

	@Shadow
	@Final
	protected int imageWidth;

	@Unique
	private final TextField aurora$search = new TextField(I18n.get("aurora.search.inventory"), 48);

	protected ContainerSearchMixin(Component title) {
		super(title);
	}

	/** The creative inventory has its own search. */
	@Unique
	private InventorySearchModule aurora$module() {
		return (Object) this instanceof CreativeModeInventoryScreen ? null : InventorySearchModule.active();
	}

	@Unique
	private void aurora$focus(boolean focused) {
		aurora$search.setFocused(focused);
		if (focused) {
			minecraft.textInputManager().startTextInput(aurora$search);
		} else {
			minecraft.textInputManager().stopTextInput(aurora$search);
		}
	}

	@Inject(method = "extractContents", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlots(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V",
		shift = At.Shift.AFTER))
	private void aurora$markSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		InventorySearchModule search = aurora$module();
		if (search != null) {
			search.markSlots(graphics, menu, aurora$search.value());
		}
	}

	@Inject(method = "extractContents", at = @At("TAIL"))
	private void aurora$drawSearch(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (aurora$module() == null) {
			return;
		}
		aurora$search.w = Math.min(imageWidth, 160);
		aurora$search.h = 14;
		aurora$search.x = leftPos + (imageWidth - aurora$search.w) / 2f;
		aurora$search.y = Math.max(2, topPos - 18);
		aurora$search.render(graphics, mouseX, mouseY);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void aurora$clickSearch(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (aurora$module() == null) {
			return;
		}
		boolean was = aurora$search.focused();
		if (aurora$search.mouseClicked(event.x(), event.y())) {
			aurora$focus(true);
			cir.setReturnValue(true);
		} else if (was) {
			aurora$focus(false);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void aurora$typeSearch(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (aurora$module() == null) {
			return;
		}
		if (aurora$search.focused()) {
			if (event.isConfirmation()) {
				aurora$focus(false);
			} else if (!aurora$search.keyPressed(event) && event.isEscape()) {
				// Escape empties the box first, then leaves it; only then does it close the screen.
				aurora$focus(false);
			}
			// While typing, letters and numbers must not drop items, swap slots or close the screen.
			cir.setReturnValue(true);
			return;
		}
		if (event.key() == InputConstants.KEY_F && event.hasControlDownWithQuirk()) {
			aurora$focus(true);
			cir.setReturnValue(true);
		}
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (aurora$module() != null && aurora$search.charTyped(event)) {
			return true;
		}
		return super.charTyped(event);
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void aurora$stopTyping(CallbackInfo ci) {
		minecraft.textInputManager().stopTextInput(aurora$search);
	}
}
