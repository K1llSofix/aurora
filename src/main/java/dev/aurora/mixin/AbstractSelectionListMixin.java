package dev.aurora.mixin;

import dev.aurora.gui.VanillaStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.layouts.LayoutElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin {
	/** Rounded accent highlight instead of the white box around the selected row. */
	@Inject(method = "extractSelection", at = @At("HEAD"), cancellable = true)
	private void aurora$selection(GuiGraphicsExtractor graphics, @Coerce LayoutElement entry, int outlineColor, CallbackInfo ci) {
		if (VanillaStyle.enabled()) {
			VanillaStyle.selection(graphics, entry.getX(), entry.getY() + 1, entry.getWidth(), entry.getHeight() - 2, outlineColor == -1);
			ci.cancel();
		}
	}

	/** Worlds, servers, languages, packs: every row of a pickable list sits on its own card. */
	@Inject(method = "extractItem", at = @At("HEAD"))
	private void aurora$card(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, @Coerce LayoutElement entry, CallbackInfo ci) {
		AbstractSelectionList<?> self = (AbstractSelectionList<?>) (Object) this;
		if (!VanillaStyle.enabled() || !(self instanceof ObjectSelectionList<?>)) {
			return;
		}
		boolean inList = mouseY >= self.getY() && mouseY < self.getBottom();
		boolean hovered = inList && mouseX >= entry.getX() && mouseX < entry.getX() + entry.getWidth()
			&& mouseY >= entry.getY() && mouseY < entry.getY() + entry.getHeight();
		VanillaStyle.card(graphics, entry.getX(), entry.getY() + 1, entry.getWidth(), entry.getHeight() - 2, hovered);
	}
}
