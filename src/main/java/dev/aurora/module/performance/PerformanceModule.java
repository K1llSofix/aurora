package dev.aurora.module.performance;

import dev.aurora.Aurora;
import dev.aurora.gui.Notifications;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.NumberSetting;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.TextureFilteringMethod;
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

/**
 * More frames on modest hardware without changing how the game plays: far-away decorations,
 * mobs, block entities and particles are simply not drawn, the window rests when it is in the
 * background, and one click sets every video option for a weak, average or strong PC.
 *
 * <p>Everything here only skips drawing; nothing is hidden that the vanilla game would show at
 * the same distance with its own entity distance option.
 */
public final class PerformanceModule extends Module {
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

	/** The frame cap while the window is in the background (the game only slows minimised windows). */
	public int frameLimit(int limit) {
		int background = backgroundFps.intValue();
		if (background > 0 && !mc.getWindow().isFocused()) {
			return Math.min(limit, background);
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
