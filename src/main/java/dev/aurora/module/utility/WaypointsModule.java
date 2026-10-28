package dev.aurora.module.utility;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import dev.aurora.gui.Notifications;
import dev.aurora.gui.render.World3D;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.util.ColorUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Your own saved points in each world: a soft beam and a name with the distance, seen from afar.
 * A key adds one where you stand, and the place you died is marked by itself. They hold only
 * coordinates you were at yourself.
 */
public final class WaypointsModule extends Module {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** Colours new points take in turn. */
	public static final int[] COLORS = {0xFF7C8CFF, 0xFF34D399, 0xFFFBBF24, 0xFFF472B6, 0xFF38BDF8, 0xFFA78BFA, 0xFFFB923C};
	private static final int DEATH_COLOR = 0xFFF87171;
	private static final int FULL_BRIGHT = 0xF000F0;

	/** One saved point. Fields are public for Gson. */
	public static final class Point {
		public String name = "";
		public int x;
		public int y;
		public int z;
		public String dimension = "minecraft:overworld";
		public int color = COLORS[0];
		public boolean visible = true;
		public boolean death;

		public Vec3 center() {
			return new Vec3(x + 0.5, y, z + 0.5);
		}
	}

	private final BooleanSetting beams = add(new BooleanSetting("beams", true));
	private final BooleanSetting labels = add(new BooleanSetting("labels", true));
	private final BooleanSetting deathPoint = add(new BooleanSetting("death", true));

	private static @Nullable WaypointsModule instance;
	private final Path file = FabricLoader.getInstance().getConfigDir().resolve("aurora").resolve("waypoints.json");
	private Map<String, List<Point>> worlds = new LinkedHashMap<>();
	private boolean loaded;
	private boolean wasDead;

	public WaypointsModule() {
		super("waypoints", Category.UTILITY, Items.BEACON, true);
		instance = this;
	}

	public static @Nullable WaypointsModule active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	public static @Nullable WaypointsModule get() {
		return instance;
	}

	// ======================================================================= storage

	private void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			if (Files.isRegularFile(file)) {
				Map<String, List<Point>> read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
					new TypeToken<LinkedHashMap<String, List<Point>>>() {
					}.getType());
				if (read != null) {
					worlds = read;
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Could not read {}", file, e);
		}
	}

	public void store() {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("waypoints.json.tmp");
			Files.writeString(tmp, GSON.toJson(worlds), StandardCharsets.UTF_8);
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			LOGGER.warn("Could not save {}", file, e);
		}
	}

	/** Which world the points belong to: the server's address, or the singleplayer world's name. */
	public static @Nullable String worldKey() {
		if (mc.level == null) {
			return null;
		}
		var server = mc.getSingleplayerServer();
		if (server != null) {
			return "singleplayer:" + server.getWorldData().getLevelName();
		}
		ServerData data = mc.getCurrentServer();
		if (data == null) {
			return "unknown";
		}
		return (data.isRealm() ? "realms:" + data.name : "server:" + data.ip).toLowerCase(Locale.ROOT);
	}

	/** A readable name of the current world for the list's title. */
	public static String worldName() {
		var server = mc.getSingleplayerServer();
		if (server != null) {
			return server.getWorldData().getLevelName();
		}
		ServerData data = mc.getCurrentServer();
		return data == null ? "" : data.isRealm() ? data.name : data.ip;
	}

	/** The points of the world you are in (a live list: change it, then call {@link #store()}). */
	public List<Point> points() {
		load();
		String key = worldKey();
		if (key == null) {
			return new ArrayList<>();
		}
		return worlds.computeIfAbsent(key, k -> new ArrayList<>());
	}

	private static String dimension() {
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	// ======================================================================= actions

	/** Adds a point where you stand (the key, or "Add here" in the list). */
	public @Nullable Point addHere() {
		if (mc.player == null || mc.level == null) {
			return null;
		}
		List<Point> points = points();
		Point point = new Point();
		int number = (int) points.stream().filter(p -> !p.death).count() + 1;
		point.name = I18n.get("aurora.waypoints.default_name", number);
		point.x = mc.player.getBlockX();
		point.y = mc.player.getBlockY();
		point.z = mc.player.getBlockZ();
		point.dimension = dimension();
		point.color = COLORS[(number - 1) % COLORS.length];
		points.add(point);
		store();
		Notifications.push(I18n.get("aurora.waypoints.added"), point.name + " · " + point.x + " " + point.y + " " + point.z,
			Notifications.Kind.ENABLED);
		return point;
	}

	@Override
	public void tick() {
		if (mc.player == null || mc.level == null) {
			wasDead = false;
			return;
		}
		boolean dead = mc.player.isDeadOrDying();
		if (dead && !wasDead && deathPoint.on()) {
			markDeath();
		}
		wasDead = dead;
	}

	/** Moves this world's death point to where you just died. */
	private void markDeath() {
		List<Point> points = points();
		points.removeIf(p -> p.death);
		Point point = new Point();
		point.name = I18n.get("aurora.waypoints.death_name");
		point.x = mc.player.getBlockX();
		point.y = mc.player.getBlockY();
		point.z = mc.player.getBlockZ();
		point.dimension = dimension();
		point.color = DEATH_COLOR;
		point.death = true;
		points.add(point);
		store();
	}

	// ======================================================================= drawing

	/** Beams and names of this dimension's points; called while the world collects what to draw. */
	public void render(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera, CameraRenderState cameraState) {
		// F1 hides them along with the HUD, like name tags, for clean screenshots.
		if (mc.level == null || mc.gui.hud.isHidden() || (!beams.on() && !labels.on())) {
			return;
		}
		String dimension = dimension();
		// Far points are drawn on the way to them, just inside the view distance, at the size they
		// would have there, so the far plane never cuts them off.
		double reach = Math.max(24, mc.options.getEffectiveRenderDistance() * 16 * 0.75);
		for (Point point : points()) {
			if (!point.visible || !point.dimension.equals(dimension)) {
				continue;
			}
			Vec3 target = point.center();
			Vec3 delta = target.subtract(camera);
			double distance = delta.length();
			double scale = distance > reach ? reach / distance : 1;
			Vec3 at = camera.add(delta.scale(scale));
			float size = (float) scale;
			if (beams.on()) {
				beam(poseStack, collector, camera, at, size, point.color, distance);
			}
			if (labels.on()) {
				label(poseStack, collector, camera, cameraState, at, point, distance);
			}
		}
	}

	private void beam(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera, Vec3 at, float size, int color, double distance) {
		// Close up the beam steps aside so it does not cover the view while you stand in it.
		float fade = (float) Math.min(1, Math.max(0, (distance - 2) / 6));
		if (fade <= 0) {
			return;
		}
		// Wider for far points, so a beam stays visible across the map.
		float half = 0.15f * size * (float) Math.max(1, Math.min(3, distance / 40));
		float height = 160 * size;
		int core = ColorUtil.withAlpha(color, Math.round(170 * fade));
		int glow = ColorUtil.withAlpha(color, Math.round(55 * fade));
		int top = ColorUtil.withAlpha(color, 0);
		World3D.submit(poseStack, collector, camera, at, (pose, buffer) -> {
			// Two crossed planes read as a column from every side; a wider pair around it glows.
			column(buffer, pose, half, height, core, top);
			column(buffer, pose, half * 3, height, glow, top);
		});
	}

	private static void column(VertexConsumer buffer, PoseStack.Pose pose, float half, float height, int bottom, int top) {
		World3D.quad(buffer, pose, -half, 0, 0, bottom, half, 0, 0, bottom, half, height, 0, top, -half, height, 0, top);
		World3D.quad(buffer, pose, 0, 0, -half, bottom, 0, 0, half, bottom, 0, height, half, top, 0, height, -half, top);
	}

	private void label(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera, CameraRenderState cameraState, Vec3 at,
					   Point point, double distance) {
		String text = point.name + "  " + formatDistance(distance);
		FormattedCharSequence line = Component.literal(text).getVisualOrderText();
		Font font = mc.font;
		// The same size on screen at any distance, larger than a name tag.
		double fromEye = at.distanceTo(camera);
		double grow = Math.max(1, fromEye / 4.5);
		float scale = (float) (0.025 * grow);
		poseStack.pushPose();
		poseStack.translate(at.x - camera.x, at.y - camera.y + 1.2 * grow, at.z - camera.z);
		poseStack.rotate(cameraState.orientation);
		poseStack.scale(scale, -scale, scale);
		float x = -font.width(line) / 2f;
		int background = ColorUtil.withAlpha(ColorUtil.lerp(0x0E1118, point.color & 0xFFFFFF, 0.25f), 150);
		collector.submitText(poseStack, x, 0, line, false, Font.DisplayMode.SEE_THROUGH, FULL_BRIGHT, 0xFFFFFFFF, background, 0);
		poseStack.popPose();
	}

	public static String formatDistance(double distance) {
		return distance >= 1000
			? I18n.get("aurora.unit.kilometers", String.format(Locale.ROOT, "%.1f", distance / 1000))
			: I18n.get("aurora.unit.meters", Math.round(distance));
	}
}
