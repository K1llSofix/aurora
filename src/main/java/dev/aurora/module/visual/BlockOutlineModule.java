package dev.aurora.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aurora.gui.render.World3D;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/** Custom colour and thickness for the block selection box, plus an optional translucent fill. */
public final class BlockOutlineModule extends Module {
	private final ColorSetting color = add(new ColorSetting("color", 0xE6A78BFA, true));
	private final NumberSetting width = add(new NumberSetting("width", 3, 1, 8, 0.5).suffix("px"));
	private final BooleanSetting fill = add(new BooleanSetting("fill", true));
	private final ColorSetting fillColor = add(new ColorSetting("fill_color", 0x2EA78BFA, true)).visibleWhen(fill::on);

	public BlockOutlineModule() {
		super("block_outline", Category.VISUAL, Items.GLASS, true);
	}

	/** Draws the outline (and fill) in place of vanilla's; poseStack is already at the block. */
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, BlockOutlineRenderState state, boolean improvedTransparency) {
		if (fill.on()) {
			int c = fillColor.color();
			collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
				for (AABB box : state.shape().toAabbs()) {
					AABB b = box.inflate(0.002);
					float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
					float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
					World3D.quad(buffer, pose, x0, y0, z0, c, x1, y0, z0, c, x1, y0, z1, c, x0, y0, z1, c);
					World3D.quad(buffer, pose, x0, y1, z0, c, x0, y1, z1, c, x1, y1, z1, c, x1, y1, z0, c);
					World3D.quad(buffer, pose, x0, y0, z0, c, x0, y1, z0, c, x1, y1, z0, c, x1, y0, z0, c);
					World3D.quad(buffer, pose, x0, y0, z1, c, x1, y0, z1, c, x1, y1, z1, c, x0, y1, z1, c);
					World3D.quad(buffer, pose, x0, y0, z0, c, x0, y0, z1, c, x0, y1, z1, c, x0, y1, z0, c);
					World3D.quad(buffer, pose, x1, y0, z0, c, x1, y1, z0, c, x1, y1, z1, c, x1, y0, z1, c);
				}
			});
		}
		collector.submitShapeOutline(poseStack, state.shape(),
			improvedTransparency ? RenderTypes.linesTranslucentNoDepthWrite() : RenderTypes.linesTranslucent(),
			color.color(), width.floatValue(), state.isTranslucent());
	}
}
