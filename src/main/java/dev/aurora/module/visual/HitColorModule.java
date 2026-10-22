package dev.aurora.module.visual;

import dev.aurora.mixin.OverlayTextureAccessor;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.ColorSetting;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.item.Items;

/** Recolours the hurt tint by rewriting the red half of the entity overlay texture. */
public final class HitColorModule extends Module {
	public static final int VANILLA = 0xB3FF0000;

	private final ColorSetting color = add(new ColorSetting("color", 0x9966CCFF, true));
	private int applied = VANILLA;

	public HitColorModule() {
		super("hit_color", Category.VISUAL, Items.REDSTONE, false);
	}

	@Override
	public void tick() {
		apply(color.color());
	}

	@Override
	protected void onDisable() {
		apply(VANILLA);
	}

	private void apply(int argb) {
		if (argb == applied || mc.gameRenderer == null) {
			return;
		}
		DynamicTexture texture = ((OverlayTextureAccessor) mc.gameRenderer.overlayTexture()).aurora$texture();
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) {
				texture.getPixels().setPixel(x, y, argb);
			}
		}
		texture.upload();
		applied = argb;
	}
}
