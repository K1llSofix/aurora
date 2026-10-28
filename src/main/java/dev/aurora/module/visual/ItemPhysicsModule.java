package dev.aurora.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Dropped items lie on the ground instead of hovering and spinning: flat items such as ingots
 * lie on their side, blocks stand on the ground, each turned its own way. In the air an item
 * tumbles, in water it drifts.
 */
public final class ItemPhysicsModule extends Module {
	/** Flat items (sprites) are this thin; anything deeper is drawn as a block. */
	private static final double FLAT_DEPTH = 0.0625;

	/** Extra fields of the item render state, filled from the entity. */
	public interface State {
		boolean aurora$onGround();

		boolean aurora$inFluid();

		void aurora$setPhysics(boolean onGround, boolean inFluid);
	}

	private final BooleanSetting tumble = add(new BooleanSetting("tumble", true));

	private static @Nullable ItemPhysicsModule instance;

	public ItemPhysicsModule() {
		super("item_physics", Category.VISUAL, Items.IRON_INGOT, false);
		instance = this;
	}

	public static @Nullable ItemPhysicsModule active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	/** Replaces the vanilla hover offset; the pose stack stands at the item's feet. */
	public void place(PoseStack pose, ItemEntityRenderState state, AABB box) {
		State physics = (State) state;
		// The vanilla bob offset is random per item: a stable way to turn each one differently.
		float turn = state.bobOffset;
		boolean flat = box.getZsize() <= FLAT_DEPTH;
		if (physics.aurora$inFluid()) {
			float drift = (float) Math.sin(state.ageInTicks / 14f + turn) * 0.04f;
			pose.translate(0, (float) -box.minY + 0.0625f + drift, 0);
			pose.rotate(Axis.YP, turn + state.ageInTicks * 0.01f);
			pose.rotateDegrees(Axis.XP, (float) Math.sin(state.ageInTicks / 20f + turn) * 12f);
			return;
		}
		if (!physics.aurora$onGround()) {
			pose.translate(0, (float) -box.minY + 0.0625f, 0);
			pose.rotate(Axis.YP, turn);
			if (tumble.on()) {
				pose.rotateDegrees(Axis.XP, state.ageInTicks * 18f + turn * 57.3f);
			}
			return;
		}
		if (flat) {
			// Turned onto its side its depth becomes its height; a stack of several lies in a pile.
			double spread = box.getZsize() * 1.5 * Math.max(0, state.count - 1) / 2.0;
			pose.translate(0, (float) (box.maxZ + spread) + 0.002f, 0);
			pose.rotate(Axis.YP, turn);
			pose.rotateDegrees(Axis.XP, 90f);
		} else {
			pose.translate(0, (float) -box.minY, 0);
			pose.rotate(Axis.YP, turn);
		}
	}
}
