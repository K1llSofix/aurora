package dev.aurora.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.World3D;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.Easing;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Soft glowing rings that ripple out on the ground where you jump (and land). */
public final class JumpCirclesModule extends Module {
	private static final int SEGMENTS = 64;

	private final NumberSetting radius = add(new NumberSetting("radius", 1.4, 0.5, 3, 0.1).suffix(" m"));
	private final NumberSetting duration = add(new NumberSetting("duration", 1.2, 0.4, 3, 0.1).suffix(" s"));
	private final BooleanSetting landing = add(new BooleanSetting("landing", true));

	private record Ring(Vec3 pos, long start, float scale) {
	}

	private final List<Ring> rings = new ArrayList<>();
	private boolean wasOnGround = true;

	public JumpCirclesModule() {
		super("jump_circles", Category.VISUAL, Items.SLIME_BALL, false);
	}

	@Override
	public void tick() {
		if (mc.player == null) {
			rings.clear();
			return;
		}
		boolean onGround = mc.player.onGround();
		Vec3 feet = mc.player.position();
		if (wasOnGround && !onGround && mc.player.getDeltaMovement().y > 0.1) {
			rings.add(new Ring(new Vec3(feet.x, Math.floor(feet.y * 16) / 16.0, feet.z), System.currentTimeMillis(), 1f));
		} else if (!wasOnGround && onGround && landing.on()) {
			rings.add(new Ring(feet, System.currentTimeMillis(), 0.7f));
		}
		wasOnGround = onGround;
		if (rings.size() > 24) {
			rings.remove(0);
		}
	}

	@Override
	protected void onDisable() {
		rings.clear();
	}

	public void render(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera) {
		if (rings.isEmpty()) {
			return;
		}
		long life = Math.round(duration.get() * 1000);
		long now = System.currentTimeMillis();
		int a = Theme.accent();
		int b = Theme.accent2();
		for (Iterator<Ring> it = rings.iterator(); it.hasNext(); ) {
			Ring ring = it.next();
			float p = (now - ring.start) / (float) life;
			if (p >= 1f) {
				it.remove();
				continue;
			}
			float r = (float) (radius.get() * ring.scale) * Easing.OUT_CUBIC.apply(p);
			float fade = 1f - Easing.IN_OUT_CUBIC.apply(p);
			float width = 0.18f + 0.22f * (1 - p);
			World3D.submit(poseStack, collector, camera, ring.pos.add(0, 0.02, 0), (pose, buffer) -> {
				float rIn = Math.max(0, r - width);
				float rMid = Math.max(0, r - width * 0.35f);
				for (int i = 0; i < SEGMENTS; i++) {
					double a0 = Math.PI * 2 * i / SEGMENTS;
					double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
					float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
					float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
					// Colour flows around the ring from one accent to the other and back.
					float t0 = (float) (0.5 - 0.5 * Math.cos(a0));
					float t1 = (float) (0.5 - 0.5 * Math.cos(a1));
					int col0 = ColorUtil.lerp(a, b, t0);
					int col1 = ColorUtil.lerp(a, b, t1);
					int peak0 = ColorUtil.withAlpha(col0, Math.round(210 * fade));
					int peak1 = ColorUtil.withAlpha(col1, Math.round(210 * fade));
					int soft0 = ColorUtil.withAlpha(col0, Math.round(50 * fade));
					int soft1 = ColorUtil.withAlpha(col1, Math.round(50 * fade));
					int clear0 = ColorUtil.withAlpha(col0, 0);
					int clear1 = ColorUtil.withAlpha(col1, 0);
					World3D.quad(buffer, pose,
						c0 * rIn, 0, s0 * rIn, soft0,
						c0 * rMid, 0, s0 * rMid, peak0,
						c1 * rMid, 0, s1 * rMid, peak1,
						c1 * rIn, 0, s1 * rIn, soft1);
					World3D.quad(buffer, pose,
						c0 * rMid, 0, s0 * rMid, peak0,
						c0 * r, 0, s0 * r, clear0,
						c1 * r, 0, s1 * r, clear1,
						c1 * rMid, 0, s1 * rMid, peak1);
					// Faint fill towards the centre.
					World3D.quad(buffer, pose,
						0, 0, 0, clear0,
						c0 * rIn, 0, s0 * rIn, soft0,
						c1 * rIn, 0, s1 * rIn, soft1,
						0, 0, 0, clear1);
				}
			});
		}
	}
}
