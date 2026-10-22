package dev.aurora.module.utility;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.aurora.gui.Notifications;
import dev.aurora.gui.Theme;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.util.WinClipboard;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLVideo;
import org.slf4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.function.Consumer;

/**
 * Screenshots (F2) made handier: the picture is copied to the clipboard, ready to paste into
 * Discord or Telegram, a toast shows what was captured, and the chat message gets a link to the
 * screenshots folder next to the file link.
 */
public final class ScreenshotsModule extends Module {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int PREVIEW_W = 320;

	private final BooleanSetting copy = add(new BooleanSetting("copy", true)).visibleWhen(WinClipboard::supported);
	private final BooleanSetting preview = add(new BooleanSetting("preview", true));
	private final BooleanSetting folder = add(new BooleanSetting("folder_link", true));

	private static ScreenshotsModule instance;

	public ScreenshotsModule() {
		super("screenshots", Category.UTILITY, Items.PAINTING, true);
		instance = this;
	}

	private static @Nullable ScreenshotsModule active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	/**
	 * Wraps the game's "Saved screenshot as ..." callback, which runs on a background thread once
	 * the file is written.
	 */
	public static Consumer<Component> wrap(Consumer<Component> callback) {
		return message -> {
			ScreenshotsModule module = active();
			File file = module == null ? null : savedFile(message);
			if (file == null) {
				callback.accept(message);
				return;
			}
			if (module.folder.on() && file.getParentFile() != null) {
				MutableComponent link = Component.translatable("aurora.screenshot.folder").withStyle(s -> s
					.withColor(Theme.accent() & 0xFFFFFF)
					.withUnderlined(true)
					.withClickEvent(new ClickEvent.OpenFile(file.getParentFile().getAbsoluteFile())));
				message = message.copy().append(Component.literal(" ")).append(link);
			}
			callback.accept(message);
			module.process(file);
		};
	}

	/** The file named in a successful screenshot message. */
	private static @Nullable File savedFile(Component message) {
		if (!(message.getContents() instanceof TranslatableContents contents) || !"screenshot.success".equals(contents.getKey())
			|| contents.getArgs().length == 0 || !(contents.getArgs()[0] instanceof Component name)) {
			return null;
		}
		if (name.getStyle().getClickEvent() instanceof ClickEvent.OpenFile open) {
			File file = new File(open.path());
			return file.isFile() ? file : null;
		}
		return null;
	}

	/** Still on the background thread: reads the picture back, makes the preview and the bitmap. */
	private void process(File file) {
		boolean wantCopy = copy.on() && WinClipboard.supported();
		boolean wantPreview = preview.on();
		if (!wantCopy && !wantPreview) {
			return;
		}
		NativeImage small = null;
		byte[] bitmap = null;
		int width;
		int height;
		try (InputStream in = Files.newInputStream(file.toPath()); NativeImage full = NativeImage.read(in)) {
			width = full.getWidth();
			height = full.getHeight();
			if (wantCopy) {
				bitmap = WinClipboard.bitmap(full);
			}
			if (wantPreview) {
				int w = Math.min(PREVIEW_W, width);
				int h = Math.max(1, Math.round(height * (w / (float) width)));
				small = new NativeImage(w, h, false);
				full.resizeSubRectTo(0, 0, width, height, small);
			}
		} catch (Exception e) {
			LOGGER.warn("Could not read screenshot {}", file, e);
			if (small != null) {
				small.close();
			}
			return;
		}
		NativeImage picture = small;
		byte[] data = bitmap;
		int w = width;
		int h = height;
		mc.execute(() -> show(file, picture, data, w, h));
	}

	/** On the render thread: the clipboard wants the game window, the toast a texture. */
	private void show(File file, @Nullable NativeImage picture, byte @Nullable [] bitmap, int width, int height) {
		boolean copied = bitmap != null && WinClipboard.put(nativeWindow(), bitmap);
		String status = bitmap == null ? file.getName()
			: copied ? I18n.get("aurora.screenshot.copied") : I18n.get("aurora.screenshot.copy_failed");
		Notifications.Kind kind = bitmap != null && !copied ? Notifications.Kind.DISABLED : Notifications.Kind.ENABLED;
		if (picture != null) {
			DynamicTexture texture = new DynamicTexture(() -> "aurora_screenshot_preview", picture);
			Notifications.pushPicture(I18n.get("aurora.screenshot.saved"), status, kind, texture, width, height);
		} else {
			Notifications.push(I18n.get("aurora.screenshot.saved"), status, kind);
		}
	}

	/** The game window as Windows knows it. */
	private static long nativeWindow() {
		int properties = SDLVideo.SDL_GetWindowProperties(mc.getWindow().handle());
		return properties == 0 ? 0 : SDLProperties.SDL_GetPointerProperty(properties, SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0L);
	}

	/** Opens the screenshots folder (used by the menu). */
	public static void openFolder() {
		File dir = new File(mc.gameDirectory, "screenshots");
		dir.mkdirs();
		Blaze3D.openPath(dir.toPath());
	}
}
