package dev.aurora.gui.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A live 3D "head shot" of any mob: the entity model rendered like the inventory preview, but
 * turned three-quarters and framed on its head. It animates, blinks red when hurt and works for
 * modded mobs too, because it simply uses the entity's own renderer.
 */
public final class EntityPortrait {
	private EntityPortrait() {
	}

	/** Draws the head of {@code entity} inside the local square (x, y, size), honouring the pose. */
	public static void head(GuiGraphicsExtractor g, LivingEntity entity, float x, float y, float size, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		EntityRenderer<? super LivingEntity, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
		EntityRenderState state = renderer.createRenderState(entity, partialTick);
		state.shadowPieces.clear();
		state.outlineColor = 0;

		float modelScale = 1f;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = 180f + 28f;
			living.yRot = 0f;
			living.xRot = 0f;
			modelScale = living.scale <= 0 ? 1f : living.scale;
			living.boundingBoxWidth /= modelScale;
			living.boundingBoxHeight /= modelScale;
			living.scale = 1f;
		}

		// Size of the region to frame, in blocks: a humanoid head is about half a block, small or
		// blobby mobs (slimes, ghasts) fill the frame with their whole body.
		float width = entity.getBbWidth() / modelScale;
		float height = entity.getBbHeight() / modelScale;
		float eye = entity.getEyeHeight() / modelScale;
		float frame = Math.max(0.5f, Math.min(3f, Math.min(width, height) * 0.95f));
		float focus = Math.min(eye + frame * 0.1f, height - frame * 0.42f);
		focus = Math.max(focus, frame * 0.5f);

		// The picture-in-picture renderer works in screen space, so map the local square through
		// the current pose ourselves (HUD elements are translated and scaled).
		Matrix3x2f m = g.pose();
		float sx0 = m.m00() * x + m.m10() * y + m.m20();
		float sy0 = m.m01() * x + m.m11() * y + m.m21();
		float sx1 = m.m00() * (x + size) + m.m10() * (y + size) + m.m20();
		float sy1 = m.m01() * (x + size) + m.m11() * (y + size) + m.m21();
		int x0 = Math.round(Math.min(sx0, sx1));
		int y0 = Math.round(Math.min(sy0, sy1));
		int x1 = Math.round(Math.max(sx0, sx1));
		int y1 = Math.round(Math.max(sy0, sy1));
		if (x1 - x0 < 2 || y1 - y0 < 2) {
			return;
		}
		float pixelsPerBlock = (x1 - x0) / frame;
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf camera = new Quaternionf().rotateX((float) Math.toRadians(-8));
		rotation.mul(camera);
		g.entity(state, pixelsPerBlock, new Vector3f(0f, focus, 0f), rotation, camera, x0, y0, x1, y1);
	}
}
