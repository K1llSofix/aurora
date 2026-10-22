package dev.aurora.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.aurora.Aurora;
import dev.aurora.module.visual.HotbarModule;
import dev.aurora.module.visual.OverlaysModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public abstract class HudMixin {
	/**
	 * Hotbar: the selection glides between slots, and the glass style replaces the vanilla bar,
	 * selection and offhand sprites with Aurora panels. Other sprites pass through untouched.
	 */
	@WrapOperation(method = "extractItemHotbar",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void aurora$hotbar(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite,
							   int x, int y, int w, int h, Operation<Void> original) {
		HotbarModule hotbar = Aurora.modules().get(HotbarModule.class);
		String path = sprite.getPath();
		if (hotbar == null || !hotbar.isEnabled() || !path.startsWith("hud/hotbar")) {
			original.call(graphics, pipeline, sprite, x, y, w, h);
			return;
		}
		switch (path) {
			case "hud/hotbar" -> {
				if (hotbar.glass()) {
					hotbar.drawBackground(graphics, x, y, w, h);
				} else {
					original.call(graphics, pipeline, sprite, x, y, w, h);
				}
			}
			case "hud/hotbar_selection" -> {
				Minecraft mc = Minecraft.getInstance();
				int selected = mc.player == null ? 0 : mc.player.getInventory().getSelectedSlot();
				float slot = hotbar.selection(selected);
				float shift = (slot - selected) * 20f;
				if (hotbar.glass()) {
					hotbar.drawSelection(graphics, x + 1 + shift, y + 1);
				} else {
					graphics.pose().pushMatrix();
					graphics.pose().translate(shift, 0);
					original.call(graphics, pipeline, sprite, x, y, w, h);
					graphics.pose().popMatrix();
				}
			}
			case "hud/hotbar_offhand_left", "hud/hotbar_offhand_right" -> {
				if (hotbar.glass()) {
					hotbar.drawOffhand(graphics, x + (path.endsWith("left") ? 3 : 4), y + 1);
				} else {
					original.call(graphics, pipeline, sprite, x, y, w, h);
				}
			}
			default -> original.call(graphics, pipeline, sprite, x, y, w, h);
		}
	}

	/** No pumpkin overlay: skip the equipment camera overlay (carved pumpkin blur). */
	@WrapWithCondition(method = "extractCameraOverlays",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V", ordinal = 0))
	private boolean aurora$pumpkin(Hud hud, GuiGraphicsExtractor graphics, Identifier texture, float alpha) {
		OverlaysModule overlays = Aurora.modules().get(OverlaysModule.class);
		return overlays == null || !overlays.hidePumpkin();
	}
}
