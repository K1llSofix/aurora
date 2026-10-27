package dev.aurora.module.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * A countdown over lit TNT: the seconds left until it explodes, turning from green to red. It is
 * drawn in the world like a name tag, but only where the TNT itself can be seen, so walls hide it.
 */
public final class TntTimerModule extends Module {
	/** Full brightness, so the numbers read the same in a dark cave as in daylight. */
	private static final int FULL_BRIGHT = 0xF000F0;

	private final BooleanSetting colors = add(new BooleanSetting("colors", true));
	private final BooleanSetting background = add(new BooleanSetting("background", true));
	private final NumberSetting scale = add(new NumberSetting("scale", 100, 50, 200, 10).suffix("%"));

	private static TntTimerModule instance;

	public TntTimerModule() {
		super("tnt_timer", Category.VISUAL, Items.TNT, true);
		instance = this;
	}

	public static @Nullable TntTimerModule active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	/** Draws the countdown of one TNT; the pose stack stands at the TNT's feet. */
	public void submit(TntRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		float ticks = state.fuseRemainingInTicks;
		if (ticks <= 0 || state.isInvisible) {
			return;
		}
		float seconds = ticks / 20f;
		FormattedCharSequence text = Component.literal(String.format(Locale.ROOT, "%.1f", seconds)).getVisualOrderText();
		Font font = mc.font;
		float size = scale.floatValue() / 100f;
		pose.pushPose();
		// Above the block, facing the camera, like a name tag.
		pose.translate(0, state.boundingBoxHeight + 0.5f, 0);
		pose.rotate(camera.orientation);
		pose.scale(0.025f * size, -0.025f * size, 0.025f * size);
		int color = colors.on() ? colorFor(seconds) : 0xFFFFFFFF;
		int back = background.on() ? 0x66000000 : 0;
		collector.submitText(pose, -font.width(text) / 2f, 0, text, false, Font.DisplayMode.NORMAL, FULL_BRIGHT, color, back, 0);
		pose.popPose();
	}

	/** Green while there is time, yellow at two seconds, red in the last one. */
	private static int colorFor(float seconds) {
		if (seconds >= 2) {
			return ColorUtil.lerp(0xFFFFE066, 0xFF5CE88A, Math.min(1, seconds - 2));
		}
		return ColorUtil.lerp(0xFFFF5C5C, 0xFFFFE066, Math.max(0, seconds - 1));
	}
}
