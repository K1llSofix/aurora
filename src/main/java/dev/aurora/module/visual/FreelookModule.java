package dev.aurora.module.visual;

import dev.aurora.Aurora;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.ModeSetting;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

/** Look around freely while your character keeps facing the same way (a "perspective" mod). */
public final class FreelookModule extends Module {
	private final ModeSetting mode = add(new ModeSetting("mode", "hold", "hold", "toggle"));
	private final ModeSetting view = add(new ModeSetting("view", "back", "back", "front", "first"));

	private boolean active;
	private float yaw;
	private float pitch;
	private CameraType previous = CameraType.FIRST_PERSON;

	public FreelookModule() {
		super("freelook", Category.VISUAL, Items.ENDER_EYE, true);
	}

	public boolean active() {
		return isEnabled() && active;
	}

	public float yaw() {
		return yaw;
	}

	public float pitch() {
		return pitch;
	}

	@Override
	public void tick() {
		if (mc.player == null) {
			stop();
			return;
		}
		// Count presses instead of sampling the key, so a quick tap still toggles.
		int presses = 0;
		while (Aurora.freelookKey.consumeClick()) {
			presses++;
		}
		boolean want = mode.is("toggle") ? (presses % 2 == 1) != active : Aurora.freelookKey.isDown();
		if (want && !active) {
			start();
		} else if (!want && active) {
			stop();
		}
	}

	private void start() {
		yaw = mc.player.getYRot();
		pitch = mc.player.getXRot();
		previous = mc.options.getCameraType();
		CameraType type = switch (view.get()) {
			case "front" -> CameraType.THIRD_PERSON_FRONT;
			case "first" -> CameraType.FIRST_PERSON;
			default -> CameraType.THIRD_PERSON_BACK;
		};
		mc.options.setCameraType(type);
		active = true;
	}

	private void stop() {
		if (active) {
			mc.options.setCameraType(previous);
			active = false;
		}
	}

	@Override
	protected void onDisable() {
		stop();
	}

	/** Mouse movement while active turns the camera instead of the player. */
	public void turn(double dx, double dy) {
		yaw += (float) dx * 0.15f;
		pitch = Mth.clamp(pitch + (float) dy * 0.15f, -90f, 90f);
	}
}
