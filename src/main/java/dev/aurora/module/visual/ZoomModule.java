package dev.aurora.module.visual;

import dev.aurora.Aurora;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.Animation;
import net.minecraft.world.item.Items;

/** Hold-to-zoom with an eased transition, scroll adjustment and sensitivity compensation. */
public final class ZoomModule extends Module {
	private final NumberSetting level = add(new NumberSetting("level", 4, 1.5, 15, 0.5).suffix("x"));
	private final BooleanSetting smooth = add(new BooleanSetting("smooth", true));
	private final BooleanSetting scroll = add(new BooleanSetting("scroll", true));
	private final BooleanSetting cinematic = add(new BooleanSetting("cinematic", false));
	private final BooleanSetting sensitivity = add(new BooleanSetting("sensitivity", true));

	private final Animation factor = new Animation(1f, 13f);
	private float scrollMultiplier = 1f;
	private boolean zooming;
	private boolean savedSmoothCamera;

	public ZoomModule() {
		super("zoom", Category.VISUAL, Items.SPYGLASS, true);
	}

	public boolean zooming() {
		return isEnabled() && zooming;
	}

	@Override
	public void tick() {
		boolean want = Aurora.zoomKey.isDown() && mc.gui.screen() == null;
		if (want && !zooming) {
			zooming = true;
			scrollMultiplier = 1f;
			if (cinematic.on()) {
				savedSmoothCamera = mc.options.smoothCamera;
				mc.options.smoothCamera = true;
			}
		} else if (!want && zooming) {
			zooming = false;
			if (cinematic.on()) {
				mc.options.smoothCamera = savedSmoothCamera;
			}
		}
	}

	@Override
	protected void onDisable() {
		if (zooming && cinematic.on()) {
			mc.options.smoothCamera = savedSmoothCamera;
		}
		zooming = false;
		factor.snap(1f);
	}

	private float target() {
		return zooming() ? (float) (level.get() * scrollMultiplier) : 1f;
	}

	/** Current FOV divisor; 1 means no zoom. */
	public float fovDivisor() {
		if (!isEnabled()) {
			return 1f;
		}
		float t = target();
		return smooth.on() ? Math.max(1f, factor.target(t).get()) : t;
	}

	/** @return true if the scroll was used for zooming and must not change the hotbar slot. */
	public boolean onScroll(double amount) {
		if (!zooming() || !scroll.on() || amount == 0) {
			return false;
		}
		float next = scrollMultiplier * (amount > 0 ? 1.18f : 1f / 1.18f);
		float total = (float) (level.get() * next);
		if (total >= 1.1f && total <= 60f) {
			scrollMultiplier = next;
		}
		return true;
	}

	public double mouseFactor() {
		if (!zooming() || !sensitivity.on()) {
			return 1.0;
		}
		return 1.0 / Math.max(1f, factor.peek());
	}
}
