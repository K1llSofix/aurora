package dev.aurora.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.World3D;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** A translucent gradient cone over your head, slowly rotating, visible in third person. */
public final class ChinaHatModule extends Module {
	private static final int SEGMENTS = 72;

	private final NumberSetting size = add(new NumberSetting("size", 0.65, 0.3, 1.2, 0.05).suffix(" m"));
	private final NumberSetting height = add(new NumberSetting("height", 0.3, 0.1, 0.6, 0.05).suffix(" m"));
	private final NumberSetting opacity = add(new NumberSetting("opacity", 55, 15, 100, 5).suffix("%"));

	public ChinaHatModule() {
		super("china_hat", Category.VISUAL, Items.GOLDEN_HELMET, false);
	}

	public void render(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera, float partialTick) {
		LocalPlayer player = mc.player;
		if (player == null || mc.options.getCameraType().isFirstPerson() || player.isInvisible()) {
			return;
		}
		double x = Mth.lerp(partialTick, player.xo, player.getX());
		double y = Mth.lerp(partialTick, player.yo, player.getY());
		double z = Mth.lerp(partialTick, player.zo, player.getZ());
		float top = player.getBbHeight() + (player.isCrouching() ? 0.02f : 0.06f);
		float r = size.floatValue();
		float h = height.floatValue();
		float alpha = opacity.floatValue() / 100f;
		double spin = (System.currentTimeMillis() % 8000L) / 8000.0 * Math.PI * 2;
		int a = Theme.accent();
		int b = Theme.accent2();
		World3D.submit(poseStack, collector, camera, new Vec3(x, y + top, z), (pose, buffer) -> {
			for (int i = 0; i < SEGMENTS; i++) {
				double a0 = spin + Math.PI * 2 * i / SEGMENTS;
				double a1 = spin + Math.PI * 2 * (i + 1) / SEGMENTS;
				float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
				float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
				float t0 = (float) (0.5 + 0.5 * Math.sin(Math.PI * 2 * i / SEGMENTS));
				float t1 = (float) (0.5 + 0.5 * Math.sin(Math.PI * 2 * (i + 1) / SEGMENTS));
				int rim0 = ColorUtil.withAlpha(ColorUtil.lerp(a, b, t0), Math.round(255 * alpha));
				int rim1 = ColorUtil.withAlpha(ColorUtil.lerp(a, b, t1), Math.round(255 * alpha));
				int tip = ColorUtil.withAlpha(ColorUtil.lerp(a, b, 0.5f), Math.round(255 * alpha * 0.55f));
				// Cone surface (apex, rim, rim).
				World3D.quad(buffer, pose,
					0, h, 0, tip,
					c0 * r, 0, s0 * r, rim0,
					c1 * r, 0, s1 * r, rim1,
					0, h, 0, tip);
				// Bright rim band just below the edge.
				float rr = r * 0.97f;
				int edge0 = ColorUtil.withAlpha(ColorUtil.shade(ColorUtil.lerp(a, b, t0), 0.35f), Math.round(255 * Math.min(1f, alpha + 0.3f)));
				int edge1 = ColorUtil.withAlpha(ColorUtil.shade(ColorUtil.lerp(a, b, t1), 0.35f), Math.round(255 * Math.min(1f, alpha + 0.3f)));
				World3D.quad(buffer, pose,
					c0 * rr, h * 0.03f, s0 * rr, edge0,
					c0 * r, 0, s0 * r, edge0,
					c1 * r, 0, s1 * r, edge1,
					c1 * rr, h * 0.03f, s1 * rr, edge1);
			}
		});
	}
}
