package dev.aurora.module.performance;

import com.mojang.renderpearl.api.device.GpuSurface;
import dev.aurora.Aurora;
import dev.aurora.gui.Notifications;
import dev.aurora.mixin.MinecraftSurfaceAccessor;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.setting.Setting;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

/**
 * More frames on modest hardware without changing how the game plays: far-away decorations,
 * mobs, block entities and particles are simply not drawn, the window rests when it is in the
 * background, and one click sets every video option for a weak, average or strong PC.
 *
 * <p>Frames: "latest frames only" shows the newest finished frame instead of a queue of older
 * ones, and the stable frame rate holds the game at a level the computer keeps up every frame.
 * Memory: garbage is collected behind loading screens, where a pause cannot be felt.
 *
 * <p>Everything here only skips drawing; nothing is hidden that the vanilla game would show at
 * the same distance with its own entity distance option.
 */
public final class PerformanceModule extends Module {
	public final BooleanSetting latestFrames = add(new BooleanSetting("latest_frames", false).onChange(v -> refreshSurface()));
	public final BooleanSetting stableFrames = add(new BooleanSetting("stable_frames", false).onChange(v -> resetPacing()));
	public final BooleanSetting cleanOnLoading = add(new BooleanSetting("clean_on_loading", true));
	public final NumberSetting entityDistance = add(new NumberSetting("entity_distance", 96, 16, 256, 8).format(PerformanceModule::blocks));
	public final NumberSetting objectDistance = add(new NumberSetting("object_distance", 48, 8, 160, 4).format(PerformanceModule::blocks));
	public final BooleanSetting players = add(new BooleanSetting("cull_players", false));
	public final NumberSetting blockEntityDistance = add(new NumberSetting("block_entity_distance", 48, 8, 160, 4).format(PerformanceModule::blocks));
	public final NumberSetting particleLimit = add(new NumberSetting("particle_limit", 4000, 500, 16000, 500).format(v -> Integer.toString((int) v)));
	public final NumberSetting particleDistance = add(new NumberSetting("particle_distance", 32, 8, 96, 4).format(PerformanceModule::blocks));
	public final NumberSetting backgroundFps = add(new NumberSetting("background_fps", 15, 0, 60, 5)
		.format(v -> v <= 0 ? I18n.get("options.off") : (int) v + " FPS"));

	private static PerformanceModule instance;

	public PerformanceModule() {
		super("performance", Category.PERFORMANCE, Items.REDSTONE_TORCH, true);
		instance = this;
	}

	/** The module while it is switched on, for the render hooks; null otherwise. */
	public static PerformanceModule active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	/** Settings the menu shows under their own headings (frames, memory) rather than in the list. */
	public boolean shownSeparately(Setting<?> setting) {
		return setting == latestFrames || setting == stableFrames || setting == cleanOnLoading;
	}

	@Override
	protected void onEnable() {
		refreshSurface();
		resetPacing();
	}

	@Override
	protected void onDisable() {
		refreshSurface();
	}

	@Override
	public void tick() {
		reviewPacing();
		cleanBetweenWorlds();
	}

	/** A line of the menu: what something is doing right now, and whether it needs attention. */
	public record Status(String text, boolean warning) {
	}

	// ======================================================================= latest frames

	public static boolean wantsLatestFrames() {
		PerformanceModule p = active();
		return p != null && p.latestFrames.on();
	}

	/** Makes the game choose its present mode again on the next frame, so a switch works at once. */
	private static void refreshSurface() {
		if (mc != null) {
			((MinecraftSurfaceAccessor) mc).aurora$setWindowSurfaceNeedsReconfiguring(true);
		}
	}

	/** How frames reach the screen now, or why "latest frames only" cannot work with this graphics API. */
	public Status presentStatus() {
		GpuSurface surface = ((MinecraftSurfaceAccessor) mc).aurora$windowSurface();
		if (surface == null) {
			return new Status("—", false);
		}
		if (latestFrames.on() && !surface.supportedPresentModes().contains(GpuSurface.PresentMode.MAILBOX)) {
			return new Status(I18n.get("aurora.performance.present.unsupported"), true);
		}
		return surface.currentConfiguration()
			.map(c -> new Status(I18n.get("aurora.performance.present." + c.presentMode().name().toLowerCase(Locale.ROOT)), false))
			.orElse(new Status("—", false));
	}

	// ======================================================================= stable frame rate

	/** Recent frames the stable cap looks at: a few seconds at any frame rate. */
	private static final int PACING_FRAMES = 240;
	private final float[] workMs = new float[PACING_FRAMES];
	private final float[] sortedMs = new float[PACING_FRAMES];
	private int workHead;
	private int workCount;
	private long frameStart;
	private long waited;
	/** The frame rate the computer holds every frame; 0 while it is still being measured. */
	private int stableCap;
	private long nextReview;

	/** Called at the start of every frame. */
	public static void onFrameStart() {
		PerformanceModule p = active();
		if (p != null) {
			p.sampleFrame();
		}
	}

	/** Called by the game's frame limiter: time spent waiting there is not time the frame needed. */
	public static void onLimiterWait(long nanos) {
		if (instance != null) {
			instance.waited += nanos;
		}
	}

	private void sampleFrame() {
		long now = System.nanoTime();
		if (frameStart != 0 && stableFrames.on() && mc.getWindow().isFocused()) {
			float ms = (now - frameStart - waited) / 1e6f;
			// Pauses (loading, dragging the window) are not frames.
			if (ms > 0 && ms < 250) {
				workMs[workHead] = ms;
				workHead = (workHead + 1) % PACING_FRAMES;
				workCount = Math.min(workCount + 1, PACING_FRAMES);
			}
		}
		frameStart = now;
		waited = 0;
	}

	private void resetPacing() {
		workCount = 0;
		workHead = 0;
		stableCap = 0;
		nextReview = 0;
	}

	/**
	 * Every two seconds: the frame rate that even the slowest tenth of frames reaches, a little
	 * lower for headroom. The cap drops at once when frames get heavier and rises slowly, so the
	 * frame rate stays even instead of jumping up and down.
	 */
	private void reviewPacing() {
		if (!stableFrames.on()) {
			stableCap = 0;
			return;
		}
		long now = System.currentTimeMillis();
		if (now < nextReview || workCount < 60) {
			return;
		}
		nextReview = now + 2000;
		System.arraycopy(workMs, 0, sortedMs, 0, workCount);
		Arrays.sort(sortedMs, 0, workCount);
		float slow = sortedMs[Math.min(workCount - 1, (int) (workCount * 0.9f))];
		int target = Math.max(30, (int) (1000f / slow * 0.95f) / 5 * 5);
		if (stableCap == 0 || target < stableCap) {
			stableCap = target;
		} else if (target > stableCap * 1.1f) {
			stableCap = Math.min(target, stableCap + 10);
		}
	}

	/** What the stable frame rate holds the game at, or that the frame limit is already lower. */
	public Status pacingStatus() {
		if (stableCap == 0) {
			return new Status(I18n.get("aurora.performance.pacing.measuring"), false);
		}
		int limit = mc.options.framerateLimit().get();
		if (limit < 260 && stableCap >= limit) {
			return new Status(I18n.get("aurora.performance.pacing.enough", limit), false);
		}
		return new Status(I18n.get("aurora.performance.pacing.cap", stableCap), false);
	}

	// ======================================================================= memory

	private boolean wasLoading;
	private boolean hadLevel;

	/**
	 * Collects garbage right after a world has loaded and after leaving one, while a loading screen
	 * or a menu is up, so the memory left over from loading is not collected in the middle of a fight.
	 */
	private void cleanBetweenWorlds() {
		boolean loading = mc.gui.screen() instanceof LevelLoadingScreen;
		boolean hasLevel = mc.level != null;
		if (cleanOnLoading.on() && ((wasLoading && !loading && hasLevel) || (hadLevel && !hasLevel))) {
			// On its own thread: a concurrent collector (ZGC) then does not hold up the game at all.
			Thread.ofPlatform().daemon().name("Aurora memory cleanup").start(System::gc);
		}
		wasLoading = loading;
		hadLevel = hasLevel;
	}

	/** The garbage collector the game runs with, and whether its pauses can be felt. */
	public Status collectorStatus() {
		String name = "?";
		for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
			String n = bean.getName();
			if (n.startsWith("ZGC")) {
				name = "ZGC";
			} else if (n.startsWith("Shenandoah")) {
				name = "Shenandoah";
			} else if (n.startsWith("G1")) {
				name = "G1";
			} else if (n.startsWith("PS ")) {
				name = "Parallel";
			} else if (n.equals("Copy") || n.equals("MarkSweepCompact")) {
				name = "Serial";
			} else {
				continue;
			}
			break;
		}
		boolean smooth = name.equals("ZGC") || name.equals("Shenandoah");
		return new Status(I18n.get(smooth ? "aurora.performance.collector.smooth" : "aurora.performance.collector.other", name), !smooth);
	}

	private static String blocks(double v) {
		return I18n.get("aurora.unit.blocks", (int) v);
	}

	// ======================================================================= culling

	/** Decorations and items: the bulk of what servers fill their lobbies with. */
	private static boolean isObject(Entity e) {
		return e instanceof ArmorStand || e instanceof HangingEntity || e instanceof Display || e instanceof ItemEntity
			|| e instanceof ExperienceOrb || e instanceof VehicleEntity;
	}

	/** True when an entity is too far away to be worth drawing. */
	public boolean skipEntity(Entity e, double camX, double camY, double camZ) {
		if (e == mc.getCameraEntity() || e instanceof EnderDragon || e instanceof WitherBoss || e.isCurrentlyGlowing()) {
			return false;
		}
		if (mc.player != null && (e == mc.player.getVehicle() || e.getVehicle() == mc.player)) {
			return false;
		}
		double limit;
		if (isObject(e)) {
			limit = objectDistance.get();
		} else if (e instanceof Player) {
			if (!players.on()) {
				return false;
			}
			limit = entityDistance.get();
		} else {
			limit = entityDistance.get();
		}
		double dx = e.getX() - camX;
		double dy = e.getY() - camY;
		double dz = e.getZ() - camZ;
		return dx * dx + dy * dy + dz * dz > limit * limit;
	}

	/** True when a chest, sign, banner or other block entity is too far away to draw. */
	public boolean skipBlockEntity(BlockPos pos, Vec3 camera) {
		double limit = blockEntityDistance.get();
		return pos.distToCenterSqr(camera) > limit * limit;
	}

	/** True when a new particle should not be spawned: too many already, or too far away. */
	public boolean skipParticle(double x, double y, double z, int alive) {
		if (alive >= particleLimit.intValue()) {
			return true;
		}
		Vec3 camera = mc.gameRenderer.mainCamera().position();
		double limit = particleDistance.get();
		return camera.distanceToSqr(x, y, z) > limit * limit;
	}

	/**
	 * The frame cap: lower while the window is in the background (the game only slows minimised
	 * windows), and the stable frame rate once it has been measured.
	 */
	public int frameLimit(int limit) {
		int background = backgroundFps.intValue();
		if (background > 0 && !mc.getWindow().isFocused()) {
			return Math.min(limit, background);
		}
		if (stableFrames.on() && stableCap > 0 && stableCap < limit) {
			return stableCap;
		}
		return limit;
	}

	// ======================================================================= presets

	/**
	 * Sets video options and this module for a kind of computer: "potato" (weak laptops and old
	 * PCs), "balanced" or "quality". Every value can still be changed afterwards.
	 */
	public void applyPreset(String which) {
		Options o = mc.options;
		switch (which) {
			case "potato" -> {
				set(o.graphicsPreset(), GraphicsPreset.CUSTOM);
				set(o.renderDistance(), 6);
				set(o.simulationDistance(), 5);
				set(o.biomeBlendRadius(), 0);
				set(o.prioritizeChunkUpdates(), PrioritizeChunkUpdates.NONE);
				set(o.ambientOcclusion(), false);
				set(o.cloudStatus(), CloudStatus.OFF);
				set(o.particles(), ParticleStatus.MINIMAL);
				set(o.mipmapLevels(), 0);
				set(o.entityShadows(), false);
				set(o.entityDistanceScaling(), 0.5);
				set(o.menuBackgroundBlurriness(), 0);
				set(o.cutoutLeaves(), false);
				set(o.improvedTransparency(), false);
				set(o.weatherRadius(), 3);
				set(o.maxAnisotropyBit(), 1);
				set(o.textureFiltering(), TextureFilteringMethod.NONE);
				set(o.enableVsync(), false);
				tune(48, 24, 24, 1000, 16, 10);
			}
			case "balanced" -> {
				set(o.graphicsPreset(), GraphicsPreset.CUSTOM);
				set(o.renderDistance(), 10);
				set(o.simulationDistance(), 8);
				set(o.biomeBlendRadius(), 1);
				set(o.prioritizeChunkUpdates(), PrioritizeChunkUpdates.NONE);
				set(o.ambientOcclusion(), true);
				set(o.cloudStatus(), CloudStatus.FAST);
				set(o.particles(), ParticleStatus.DECREASED);
				set(o.mipmapLevels(), 2);
				set(o.entityShadows(), false);
				set(o.entityDistanceScaling(), 0.75);
				set(o.menuBackgroundBlurriness(), 3);
				set(o.cutoutLeaves(), true);
				set(o.improvedTransparency(), false);
				set(o.weatherRadius(), 6);
				set(o.textureFiltering(), TextureFilteringMethod.NONE);
				tune(96, 48, 48, 4000, 32, 15);
			}
			default -> {
				set(o.graphicsPreset(), GraphicsPreset.FANCY);
				tune(160, 96, 96, 12000, 64, 30);
			}
		}
		setEnabled(true);
		o.save();
		Aurora.config().markDirty();
		Notifications.push(I18n.get("aurora.performance.applied"), I18n.get("aurora.performance.preset." + which), Notifications.Kind.ENABLED);
	}

	private void tune(int entities, int objects, int blockEntities, int particles, int particleRange, int background) {
		entityDistance.set((double) entities);
		objectDistance.set((double) objects);
		blockEntityDistance.set((double) blockEntities);
		particleLimit.set((double) particles);
		particleDistance.set((double) particleRange);
		backgroundFps.set((double) background);
	}

	private static <T> void set(OptionInstance<T> option, T value) {
		if (!value.equals(option.get())) {
			option.set(value);
		}
	}
}
