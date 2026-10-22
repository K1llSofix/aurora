package dev.aurora.gui.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/** Textured, tinted quads (x, y, u, v per vertex) sharing one texture, e.g. a run of glyphs. */
record TexturedQuadsState(
	Matrix3x2f pose,
	TextureSetup textureSetup,
	float[] data,
	int[] colors,
	int vertexCount,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
	@Override
	public void buildVertices(VertexConsumer consumer) {
		for (int i = 0; i < vertexCount; i++) {
			int o = i * 4;
			consumer.addVertexWith2DPose(pose, data[o], data[o + 1]).setUv(data[o + 2], data[o + 3]).setColor(colors[i]);
		}
	}

	@Override
	public RenderPipeline pipeline() {
		return RenderPipelines.GUI_TEXTURED;
	}
}
