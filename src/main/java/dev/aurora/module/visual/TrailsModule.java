package dev.aurora.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.World3D;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** A ribbon of light that follows your feet and fades out behind you. */
public final class TrailsModule extends Module {
	private final NumberSetting length = add(new NumberSetting("length", 1.2, 0.3, 3, 0.1).suffix(" s"));
	private final NumberSetting width = add(new NumberSetting("width", 0.9, 0.2, 1.8, 0.1).suffix(" m"));
	private final BooleanSetting firstPerson = add(new BooleanSetting("first_person", false));

	private record Point(Vec3 pos, long time) {
	}

	private final Deque<Point> points = new ArrayDeque<>();

	public TrailsModule() {
		super("trails", Category.VISUAL, Items.FIREWORK_ROCKET, false);
	}

	@Override
	protected void onDisable() {
		points.clear();
	}

	public void render(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera, float partialTick) {
		LocalPlayer player = mc.player;
		if (player == null) {
			points.clear();
			return;
		}
		long now = System.currentTimeMillis();
		Vec3 pos = new Vec3(
			Mth.lerp(partialTick, player.xo, player.getX()),
			Mth.lerp(partialTick, player.yo, player.getY()),
			Mth.lerp(partialTick, player.zo, player.getZ()));
		Point last = points.peekLast();
		if (last == null || last.pos.distanceToSqr(pos) > 0.0025) {
			points.addLast(new Point(pos, now));
		}
		long life = Math.round(length.get() * 1000);
		while (!points.isEmpty() && now - points.peekFirst().time > life) {
			points.pollFirst();
		}
		if (points.size() < 2 || (!firstPerson.on() && mc.options.getCameraType().isFirstPerson())) {
			return;
		}
		List<Point> list = new ArrayList<>(points);
		list.add(new Point(pos, now));
		int n = list.size();
		// Side vector (perpendicular to travel on the ground) for every point.
		float[] side = new float[n * 2];
		for (int i = 0; i < n; i++) {
			Vec3 prev = list.get(Math.max(0, i - 1)).pos;
			Vec3 next = list.get(Math.min(n - 1, i + 1)).pos;
			double dx = next.x - prev.x;
			double dz = next.z - prev.z;
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len < 1e-4) {
				side[i * 2] = i > 0 ? side[(i - 1) * 2] : 1;
				side[i * 2 + 1] = i > 0 ? side[(i - 1) * 2 + 1] : 0;
			} else {
				side[i * 2] = (float) (dz / len);
				side[i * 2 + 1] = (float) (-dx / len);
			}
		}
		float half = width.floatValue() / 2f;
		int a = Theme.accent();
		int b = Theme.accent2();
		Vec3 origin = list.get(0).pos;
		World3D.submit(poseStack, collector, camera, origin, (pose, buffer) -> {
			for (int i = 0; i < n - 1; i++) {
				Point p0 = list.get(i);
				Point p1 = list.get(i + 1);
				float f0 = 1f - Math.min(1f, (now - p0.time) / (float) life);
				float f1 = 1f - Math.min(1f, (now - p1.time) / (float) life);
				int c0 = ColorUtil.lerp(b, a, i / (float) n);
				int c1 = ColorUtil.lerp(b, a, (i + 1) / (float) n);
				int core0 = ColorUtil.withAlpha(c0, Math.round(210 * f0));
				int core1 = ColorUtil.withAlpha(c1, Math.round(210 * f1));
				int edge0 = ColorUtil.withAlpha(c0, 0);
				int edge1 = ColorUtil.withAlpha(c1, 0);
				float x0 = (float) (p0.pos.x - origin.x), y0 = (float) (p0.pos.y - origin.y) + 0.03f, z0 = (float) (p0.pos.z - origin.z);
				float x1 = (float) (p1.pos.x - origin.x), y1 = (float) (p1.pos.y - origin.y) + 0.03f, z1 = (float) (p1.pos.z - origin.z);
				// The ribbon narrows as it ages; bright along the middle, soft at both edges.
				float w0 = half * (0.35f + 0.65f * f0);
				float w1 = half * (0.35f + 0.65f * f1);
				float s0x = side[i * 2] * w0, s0z = side[i * 2 + 1] * w0;
				float s1x = side[(i + 1) * 2] * w1, s1z = side[(i + 1) * 2 + 1] * w1;
				World3D.quad(buffer, pose,
					x0 + s0x, y0, z0 + s0z, edge0,
					x0, y0, z0, core0,
					x1, y1, z1, core1,
					x1 + s1x, y1, z1 + s1z, edge1);
				World3D.quad(buffer, pose,
					x0, y0, z0, core0,
					x0 - s0x, y0, z0 - s0z, edge0,
					x1 - s1x, y1, z1 - s1z, edge1,
					x1, y1, z1, core1);
				// A low wall of light along the centre line, so the trail reads from the side too.
				float h0 = 0.55f * f0;
				float h1 = 0.55f * f1;
				World3D.quad(buffer, pose,
					x0, y0, z0, core0,
					x0, y0 + h0, z0, edge0,
					x1, y1 + h1, z1, edge1,
					x1, y1, z1, core1);
			}
		});
	}
}
