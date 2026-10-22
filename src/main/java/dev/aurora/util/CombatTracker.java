package dev.aurora.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Remembers who the player is fighting: target, last reach, combo. Fed by an attack hook. */
public final class CombatTracker {
	private static @Nullable LivingEntity target;
	private static long lastAttack;
	private static double lastReach = -1;
	private static long lastReachTime;
	private static int combo;
	private static long lastComboHit;
	private static int lastHurtTime;

	private CombatTracker() {
	}

	public static void onAttack(Entity entity) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		if (entity instanceof LivingEntity living) {
			target = living;
			lastAttack = System.currentTimeMillis();
		}
		lastReach = reach(player, entity);
		lastReachTime = System.currentTimeMillis();
		combo++;
		lastComboHit = System.currentTimeMillis();
	}

	/** Called every tick: ends combos when we get hit or stop hitting, forgets dead targets. */
	public static void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			target = null;
			combo = 0;
			return;
		}
		if (player.hurtTime > lastHurtTime) {
			combo = 0;
		}
		lastHurtTime = player.hurtTime;
		if (System.currentTimeMillis() - lastComboHit > 2500) {
			combo = 0;
		}
		if (target != null && (target.isRemoved() || !target.isAlive() && System.currentTimeMillis() - lastAttack > 1500)) {
			target = null;
		}
	}

	/** Distance from the eyes to the closest point of the entity's hitbox, like the server checks. */
	public static double reach(LocalPlayer player, Entity entity) {
		Vec3 eye = player.getEyePosition();
		AABB box = entity.getBoundingBox();
		double x = Math.max(box.minX, Math.min(eye.x, box.maxX));
		double y = Math.max(box.minY, Math.min(eye.y, box.maxY));
		double z = Math.max(box.minZ, Math.min(eye.z, box.maxZ));
		return eye.distanceTo(new Vec3(x, y, z));
	}

	public static @Nullable LivingEntity target(long keepMillis) {
		if (target != null && System.currentTimeMillis() - lastAttack <= keepMillis) {
			return target;
		}
		return null;
	}

	public static long millisSinceAttack() {
		return System.currentTimeMillis() - lastAttack;
	}

	public static double lastReach() {
		return lastReach;
	}

	public static long millisSinceReach() {
		return System.currentTimeMillis() - lastReachTime;
	}

	public static int combo() {
		return combo;
	}
}
