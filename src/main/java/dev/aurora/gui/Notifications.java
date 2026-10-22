package dev.aurora.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.aurora.Aurora;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.Easing;
import dev.aurora.util.Motion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Small toasts in the bottom-right corner, e.g. when a module is toggled with its keybind. */
public final class Notifications {
	public enum Kind {ENABLED, DISABLED, INFO}

	private static final float WIDTH = 150;
	private static final float HEIGHT = 32;
	private static final long LIFETIME = 2600;
	private static final long FADE = 280;

	private static final long PICTURE_LIFETIME = 5000;
	private static final float PICTURE_MAX_H = 96;

	private static final class Toast {
		final String title;
		String message;
		Kind kind;
		final long created = System.currentTimeMillis();
		final Animation y = new Animation(-1, 16);
		/** A picture above the text (a screenshot), owned by the toast. */
		@Nullable DynamicTexture picture;
		float pictureAspect;

		Toast(String title, String message, Kind kind) {
			this.title = title;
			this.message = message;
			this.kind = kind;
		}

		long age() {
			return System.currentTimeMillis() - created;
		}

		long lifetime() {
			return picture != null ? PICTURE_LIFETIME : LIFETIME;
		}

		float pictureHeight() {
			return picture == null ? 0 : Math.min(PICTURE_MAX_H, (WIDTH - 12) / pictureAspect);
		}

		float height() {
			return HEIGHT + (picture == null ? 0 : pictureHeight() + 6);
		}

		void release() {
			if (picture != null) {
				picture.close();
				picture = null;
			}
		}
	}

	/** Lets the code that showed a toast change its text later, e.g. when work in the background ends. */
	public static final class Handle {
		private final Toast toast;

		private Handle(Toast toast) {
			this.toast = toast;
		}

		/** Call on the render thread. */
		public void update(String message, Kind kind) {
			toast.message = message;
			toast.kind = kind;
		}
	}

	private static final List<Toast> TOASTS = new ArrayList<>();

	private Notifications() {
	}

	public static void push(String title, String message, Kind kind) {
		if (!Aurora.client().notifications.on()) {
			return;
		}
		// Re-toggling the same module replaces its toast instead of stacking duplicates.
		TOASTS.removeIf(t -> {
			if (t.title.equals(title)) {
				t.release();
				return true;
			}
			return false;
		});
		TOASTS.add(new Toast(title, message, kind));
		if (TOASTS.size() > 5) {
			TOASTS.remove(0).release();
		}
	}

	/**
	 * A toast with a picture above its text. The toast owns the texture and frees it when it goes;
	 * a new picture toast replaces the previous one. Returns null when notifications are off (the
	 * texture is freed then too).
	 */
	public static @Nullable Handle pushPicture(String title, String message, Kind kind, DynamicTexture picture, int width, int height) {
		if (!Aurora.client().notifications.on()) {
			picture.close();
			return null;
		}
		TOASTS.removeIf(t -> {
			if (t.picture != null || t.title.equals(title)) {
				t.release();
				return true;
			}
			return false;
		});
		Toast toast = new Toast(title, message, kind);
		toast.picture = picture;
		toast.pictureAspect = Math.max(0.2f, width / (float) Math.max(1, height));
		TOASTS.add(toast);
		if (TOASTS.size() > 5) {
			TOASTS.remove(0).release();
		}
		return new Handle(toast);
	}

	public static void render(GuiGraphicsExtractor g) {
		if (TOASTS.isEmpty()) {
			return;
		}
		float sw = g.guiWidth();
		float sh = g.guiHeight();
		float slot = sh - 8;
		for (Iterator<Toast> it = TOASTS.iterator(); it.hasNext(); ) {
			Toast t = it.next();
			if (t.age() > t.lifetime() + FADE) {
				t.release();
				it.remove();
			}
		}
		for (int i = TOASTS.size() - 1; i >= 0; i--) {
			Toast t = TOASTS.get(i);
			float targetY = slot - t.height();
			if (t.y.peek() < 0) {
				t.y.snap(targetY);
			}
			float y = t.y.target(targetY).get();
			slot -= t.height() + 6;

			long age = t.age();
			long lifetime = t.lifetime();
			float in = Easing.OUT_EXPO.clamped(age * Motion.speedMultiplier() / (float) FADE);
			float out = age > lifetime ? Easing.IN_OUT_CUBIC.clamped((age - lifetime) / (float) FADE) : 0f;
			float visible = in * (1 - out);
			float x = sw - 8 - WIDTH + (1 - in) * (WIDTH + 12) + out * 24;
			draw(g, t, x, y, visible, age);
		}
	}

	private static void draw(GuiGraphicsExtractor g, Toast t, float x, float y, float alpha, long age) {
		if (alpha <= 0.01f) {
			return;
		}
		int iconColor = switch (t.kind) {
			case ENABLED -> Theme.SUCCESS;
			case DISABLED -> Theme.DANGER;
			case INFO -> Theme.accent();
		};
		float total = t.height();
		Render2D.shadow(g, x, y, WIDTH, total, 7, 12, ColorUtil.fade(0x80000000, alpha));
		Render2D.roundRect(g, x, y, WIDTH, total, 7, Render2D.Paint.vertical(y, total,
			ColorUtil.fade(0xF5171A22, alpha), ColorUtil.fade(0xF50F1117, alpha)));
		Render2D.roundOutline(g, x, y, WIDTH, total, 7, 0.6f, ColorUtil.fade(Theme.BORDER, alpha));
		if (t.picture != null) {
			float ph = t.pictureHeight();
			TextureSetup setup = TextureSetup.singleTexture(t.picture.getTextureView(),
				RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			// Crop to the frame instead of squashing very tall or wide pictures.
			float frameAspect = (WIDTH - 12) / ph;
			float u0 = 0, u1 = 1, v0 = 0, v1 = 1;
			if (t.pictureAspect > frameAspect) {
				float keep = frameAspect / t.pictureAspect;
				u0 = (1 - keep) / 2f;
				u1 = 1 - u0;
			} else if (t.pictureAspect < frameAspect) {
				float keep = t.pictureAspect / frameAspect;
				v0 = (1 - keep) / 2f;
				v1 = 1 - v0;
			}
			Render2D.texture(g, setup, x + 6, y + 6, x + WIDTH - 6, y + 6 + ph, u0, v0, u1, v1, ColorUtil.fade(0xFFFFFFFF, alpha));
			Render2D.roundOutline(g, x + 5.5f, y + 5.5f, WIDTH - 11, ph + 1, 2.5f, 0.6f, ColorUtil.fade(0x30FFFFFF, alpha));
			y += ph + 6;
		}

		float cx = x + 16;
		float cy = y + HEIGHT / 2f - 1;
		Render2D.circle(g, cx, cy, 8, ColorUtil.fade(iconColor, 0.18f * alpha));
		switch (t.kind) {
			case ENABLED -> Render2D.checkmark(g, cx, cy, 7, 1.4f, ColorUtil.fade(iconColor, alpha));
			case DISABLED -> Render2D.cross(g, cx, cy, 5.5f, 1.4f, ColorUtil.fade(iconColor, alpha));
			case INFO -> Render2D.circle(g, cx, cy, 2.2f, ColorUtil.fade(iconColor, alpha));
		}

		float tx = x + 30;
		float maxW = WIDTH - 36;
		FontRenderer.SEMIBOLD.drawMiddle(g, FontRenderer.SEMIBOLD.ellipsize(t.title, 8, maxW), tx, y + 11, 8,
			ColorUtil.fade(Theme.TEXT, alpha));
		FontRenderer.MEDIUM.drawMiddle(g, FontRenderer.MEDIUM.ellipsize(t.message, 7, maxW), tx, y + 21, 7,
			ColorUtil.fade(Theme.TEXT_DIM, alpha));

		// Remaining lifetime.
		float progress = 1f - Math.min(1f, age / (float) t.lifetime());
		float barW = (WIDTH - 14) * progress;
		Render2D.roundRect(g, x + 7, y + HEIGHT - 3, barW, 1.2f, 0.6f,
			Render2D.Paint.horizontal(x + 7, WIDTH - 14, ColorUtil.fade(Theme.accent(), alpha * 0.9f), ColorUtil.fade(Theme.accent2(), alpha * 0.9f)));
	}
}
