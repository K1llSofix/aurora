package dev.aurora.gui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Helpers for translucent coloured geometry in the world. Shapes are submitted relative to the
 * camera like vanilla does with block outlines, using the double-sided "debug quads" type.
 */
public final class World3D {
	private World3D() {
	}

	@FunctionalInterface
	public interface Builder {
		void build(PoseStack.Pose pose, VertexConsumer buffer);
	}

	/** Submits geometry whose local origin is the world position {@code origin}. */
	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera, Vec3 origin, Builder builder) {
		poseStack.pushPose();
		poseStack.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
		collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), builder::build);
		poseStack.popPose();
	}

	public static void quad(VertexConsumer b, PoseStack.Pose pose,
							float x0, float y0, float z0, int c0,
							float x1, float y1, float z1, int c1,
							float x2, float y2, float z2, int c2,
							float x3, float y3, float z3, int c3) {
		b.addVertex(pose, x0, y0, z0).setColor(c0);
		b.addVertex(pose, x1, y1, z1).setColor(c1);
		b.addVertex(pose, x2, y2, z2).setColor(c2);
		b.addVertex(pose, x3, y3, z3).setColor(c3);
	}
}
