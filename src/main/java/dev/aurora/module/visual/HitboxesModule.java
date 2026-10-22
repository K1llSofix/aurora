package dev.aurora.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.World3D;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.ColorSetting;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity hitboxes like F3+B, drawn nicely: crisp coloured edges (or just the corners), a soft fill
 * that fades upwards, the look direction and a highlight on the entity under the crosshair. The
 * boxes are depth tested, so walls hide them exactly like they hide the entity itself; invisible
 * entities are skipped.
 */
public final class HitboxesModule extends Module {
	private final ModeSetting style = add(new ModeSetting("style", "box", "box", "corners", "fill"));
	private final BooleanSetting fill = add(new BooleanSetting("fill", true)).visibleWhen(() -> !style.is("fill"));
	private final NumberSetting width = add(new NumberSetting("width", 2.5, 1, 6, 0.5).suffix("px"))
		.visibleWhen(() -> !style.is("fill"));
	private final ModeSetting colors = add(new ModeSetting("colors", "type", "type", "theme", "custom"));
	private final ColorSetting color = add(new ColorSetting("color", 0xFFA78BFA, true)).visibleWhen(() -> colors.is("custom"));
	private final BooleanSetting look = add(new BooleanSetting("look", true));
	private final BooleanSetting eyes = add(new BooleanSetting("eyes", false));
	private final BooleanSetting highlight = add(new BooleanSetting("highlight", true));
	private final BooleanSetting players = add(new BooleanSetting("players", true));
	private final BooleanSetting mobs = add(new BooleanSetting("mobs", true));
	private final BooleanSetting animals = add(new BooleanSetting("animals", true));
	private final BooleanSetting items = add(new BooleanSetting("items", false));
	private final BooleanSetting other = add(new BooleanSetting("other", false));
	private final BooleanSetting self = add(new BooleanSetting("self", false));
	private final NumberSetting range = add(new NumberSetting("range", 48, 8, 128, 4).suffix(" m"));

	private record Box(AABB box, Vec3 eye, Vec3 look, float eyeY, int color, float alpha, boolean target) {
	}

	private final List<Box> boxes = new ArrayList<>();

	public HitboxesModule() {
		super("hitboxes", Category.VISUAL, Items.TARGET, false);
	}

	private int colorOf(Entity e) {
		if (colors.is("theme")) {
			return Theme.accent();
		}
		if (colors.is("custom")) {
			return color.color();
		}
		if (e instanceof Player) {
			return Theme.accent();
		}
		if (e instanceof Enemy) {
			return 0xFFFF5D73;
		}
		if (e instanceof Mob) {
			return 0xFF4ADE80;
		}
		if (e instanceof ItemEntity || e instanceof ExperienceOrb) {
			return 0xFFFBBF24;
		}
		if (e instanceof Projectile) {
			return 0xFF38BDF8;
		}
		return 0xFFE2E8F0;
	}

	private boolean wanted(Entity e) {
		if (e instanceof Player) {
			return players.on();
		}
		if (e instanceof Enemy) {
			return mobs.on();
		}
		// Every other mob: animals, villagers, fish and bats, but also golems, allays and the like,
		// which are neither animals nor enemies.
		if (e instanceof Mob) {
			return animals.on();
		}
		if (e instanceof ItemEntity || e instanceof ExperienceOrb) {
			return items.on();
		}
		return other.on();
	}

	public void render(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera, float partialTick) {
		if (mc.level == null || mc.player == null) {
			return;
		}
		boxes.clear();
		double maxDistance = range.get();
		boolean firstPerson = mc.options.getCameraType() == CameraType.FIRST_PERSON;
		Entity cameraEntity = mc.getCameraEntity();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == cameraEntity && (firstPerson || !self.on())) {
				continue;
			}
			if (e.isSpectator() || e.isInvisibleTo(mc.player) || !wanted(e)) {
				continue;
			}
			Vec3 pos = e.getPosition(partialTick);
			double distance = pos.distanceTo(camera);
			if (distance > maxDistance) {
				continue;
			}
			float fade = 1f - smooth((float) (maxDistance * 0.7), (float) maxDistance, (float) distance);
			AABB box = e.getBoundingBox().move(pos.subtract(e.position())).move(-camera.x, -camera.y, -camera.z);
			Vec3 eye = e.getEyePosition(partialTick).subtract(camera);
			boolean target = highlight.on() && e == mc.crosshairPickEntity;
			boxes.add(new Box(box, eye, e.getViewVector(partialTick), (float) (eye.y), colorOf(e), fade, target));
		}
		if (boxes.isEmpty()) {
			return;
		}
		List<Box> frame = List.copyOf(boxes);
		boolean drawFill = style.is("fill") || fill.on();
		boolean drawEdges = !style.is("fill");
		if (drawFill) {
			World3D.submit(poseStack, collector, camera, camera, (pose, buffer) -> {
				for (Box b : frame) {
					fill(buffer, pose, b);
				}
			});
		}
		if (drawEdges || look.on() || eyes.on()) {
			poseStack.pushPose();
			collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, buffer) -> {
				for (Box b : frame) {
					float w = width.floatValue() + (b.target ? 1f : 0f);
					float a = b.alpha * (b.target ? 1f : 0.85f);
					int edge = ColorUtil.fade(b.target ? ColorUtil.lerp(b.color, 0xFFFFFFFF, 0.35f) : b.color, a);
					if (drawEdges) {
						if (style.is("corners")) {
							corners(buffer, pose, b.box, edge, w);
						} else {
							edges(buffer, pose, b.box, edge, ColorUtil.fade(edge, 0.55f), w);
						}
					}
					if (eyes.on()) {
						int eyeColor = ColorUtil.fade(0xFFF87171, a * 0.8f);
						AABB box = b.box;
						float y = b.eyeY;
						line(buffer, pose, box.minX, y, box.minZ, box.maxX, y, box.minZ, eyeColor, eyeColor, w * 0.7f);
						line(buffer, pose, box.maxX, y, box.minZ, box.maxX, y, box.maxZ, eyeColor, eyeColor, w * 0.7f);
						line(buffer, pose, box.maxX, y, box.maxZ, box.minX, y, box.maxZ, eyeColor, eyeColor, w * 0.7f);
						line(buffer, pose, box.minX, y, box.maxZ, box.minX, y, box.minZ, eyeColor, eyeColor, w * 0.7f);
					}
					if (look.on()) {
						int from = ColorUtil.fade(0xFF60A5FA, a);
						Vec3 end = b.eye.add(b.look.scale(1.6));
						line(buffer, pose, b.eye.x, b.eye.y, b.eye.z, end.x, end.y, end.z, from, from & 0xFFFFFF, w * 0.8f);
					}
				}
			});
			poseStack.popPose();
		}
	}

	private static float smooth(float edge0, float edge1, float x) {
		float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
		return t * t * (3 - 2 * t);
	}

	/** Six faces, brighter at the feet and fading towards the top. */
	private static void fill(VertexConsumer buffer, PoseStack.Pose pose, Box b) {
		AABB box = b.box.inflate(0.002);
		float strength = b.alpha * (b.target ? 0.26f : 0.14f);
		int low = ColorUtil.fade(b.color, strength);
		int high = ColorUtil.fade(b.color, strength * 0.15f);
		float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
		float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
		World3D.quad(buffer, pose, x0, y0, z0, low, x1, y0, z0, low, x1, y0, z1, low, x0, y0, z1, low);
		World3D.quad(buffer, pose, x0, y1, z0, high, x0, y1, z1, high, x1, y1, z1, high, x1, y1, z0, high);
		World3D.quad(buffer, pose, x0, y0, z0, low, x0, y1, z0, high, x1, y1, z0, high, x1, y0, z0, low);
		World3D.quad(buffer, pose, x0, y0, z1, low, x1, y0, z1, low, x1, y1, z1, high, x0, y1, z1, high);
		World3D.quad(buffer, pose, x0, y0, z0, low, x0, y0, z1, low, x0, y1, z1, high, x0, y1, z0, high);
		World3D.quad(buffer, pose, x1, y0, z0, low, x1, y1, z0, high, x1, y1, z1, high, x1, y0, z1, low);
	}

	/** The twelve edges; vertical edges fade from the bottom colour to a softer top. */
	private static void edges(VertexConsumer buffer, PoseStack.Pose pose, AABB b, int color, int top, float w) {
		double x0 = b.minX, y0 = b.minY, z0 = b.minZ, x1 = b.maxX, y1 = b.maxY, z1 = b.maxZ;
		line(buffer, pose, x0, y0, z0, x1, y0, z0, color, color, w);
		line(buffer, pose, x1, y0, z0, x1, y0, z1, color, color, w);
		line(buffer, pose, x1, y0, z1, x0, y0, z1, color, color, w);
		line(buffer, pose, x0, y0, z1, x0, y0, z0, color, color, w);
		line(buffer, pose, x0, y1, z0, x1, y1, z0, top, top, w);
		line(buffer, pose, x1, y1, z0, x1, y1, z1, top, top, w);
		line(buffer, pose, x1, y1, z1, x0, y1, z1, top, top, w);
		line(buffer, pose, x0, y1, z1, x0, y1, z0, top, top, w);
		line(buffer, pose, x0, y0, z0, x0, y1, z0, color, top, w);
		line(buffer, pose, x1, y0, z0, x1, y1, z0, color, top, w);
		line(buffer, pose, x1, y0, z1, x1, y1, z1, color, top, w);
		line(buffer, pose, x0, y0, z1, x0, y1, z1, color, top, w);
	}

	/** Only the corners, three short strokes each, like a viewfinder frame. */
	private static void corners(VertexConsumer buffer, PoseStack.Pose pose, AABB b, int color, float w) {
		double lx = Math.min(0.3, b.getXsize() * 0.28);
		double ly = Math.min(0.3, b.getYsize() * 0.22);
		double lz = Math.min(0.3, b.getZsize() * 0.28);
		for (int i = 0; i < 8; i++) {
			double x = (i & 1) == 0 ? b.minX : b.maxX;
			double y = (i & 2) == 0 ? b.minY : b.maxY;
			double z = (i & 4) == 0 ? b.minZ : b.maxZ;
			double sx = (i & 1) == 0 ? lx : -lx;
			double sy = (i & 2) == 0 ? ly : -ly;
			double sz = (i & 4) == 0 ? lz : -lz;
			int clear = ColorUtil.fade(color, 0.15f);
			line(buffer, pose, x, y, z, x + sx, y, z, color, clear, w);
			line(buffer, pose, x, y, z, x, y + sy, z, color, clear, w);
			line(buffer, pose, x, y, z, x, y, z + sz, color, clear, w);
		}
	}

	private static void line(VertexConsumer buffer, PoseStack.Pose pose, double x0, double y0, double z0,
							 double x1, double y1, double z1, int c0, int c1, float width) {
		float nx = (float) (x1 - x0);
		float ny = (float) (y1 - y0);
		float nz = (float) (z1 - z0);
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1e-5f) {
			return;
		}
		nx /= len;
		ny /= len;
		nz /= len;
		buffer.addVertex(pose, (float) x0, (float) y0, (float) z0).setColor(c0).setNormal(pose, nx, ny, nz).setLineWidth(width);
		buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(c1).setNormal(pose, nx, ny, nz).setLineWidth(width);
	}
}
