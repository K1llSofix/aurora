package dev.aurora.gui.background;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.logging.LogUtils;
import dev.aurora.Aurora;
import dev.aurora.gui.render.Render2D;
import dev.aurora.module.ClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Replaces the rotating panorama behind every menu with one of Aurora's animated scenes or the
 * user's own picture. The scene clock follows the vanilla "Panorama Speed" accessibility option,
 * so setting it to zero holds the picture still.
 */
public final class MenuBackground {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final AuroraScene AURORA = new AuroraScene();
	private static final SunsetScene SUNSET = new SunsetScene();
	private static final SynthwaveScene SYNTHWAVE = new SynthwaveScene();
	private static final ImageBackground IMAGES = new ImageBackground();

	private static double clock;
	private static long lastFrame;
	private static float parallaxX;
	private static float parallaxY;

	private MenuBackground() {
	}

	private static String mode() {
		ClientSettings client = Aurora.client();
		return client == null ? "panorama" : client.menuBackground.get();
	}

	/** True when Aurora draws the background rather than the vanilla panorama. */
	public static boolean active() {
		return !mode().equals("panorama");
	}

	/** Draws the chosen background in place of the panorama; false leaves it to vanilla. */
	public static boolean render(GuiGraphicsExtractor g, int width, int height) {
		Minecraft mc = Minecraft.getInstance();
		String mode = mode();
		IMAGES.update(mode.equals("image"), mc.getWindow().getWidth(), mc.getWindow().getHeight());
		if (mode.equals("panorama") || width <= 0 || height <= 0) {
			return false;
		}
		tick(mc, width, height);
		boolean parallax = Aurora.client().menuParallax.on();
		Frame frame = new Frame(g, width, height, clock, Render2D.pixel(g), parallax ? parallaxX : 0, parallax ? parallaxY : 0);
		switch (mode) {
			case "sunset" -> SUNSET.render(frame);
			case "synthwave" -> SYNTHWAVE.render(frame);
			case "image" -> {
				// Until a picture is loaded (or if the folder is empty) the aurora scene stands in.
				if (IMAGES.coverage() < 1) {
					AURORA.render(frame);
				}
				IMAGES.draw(frame);
			}
			default -> AURORA.render(frame);
		}
		return true;
	}

	private static void tick(Minecraft mc, int width, int height) {
		long now = System.nanoTime();
		double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
		lastFrame = now;
		clock += dt * mc.options.panoramaSpeed().get();
		if (!Double.isFinite(clock)) {
			clock = 0;
		}
		// The parallax offsets move every layer except the sky, so a single NaN here would wipe the
		// scene down to an empty gradient for the rest of the session.
		if (!Float.isFinite(parallaxX) || !Float.isFinite(parallaxY)) {
			parallaxX = 0;
			parallaxY = 0;
		}
		// The view leans gently towards the mouse; smoothing keeps it calm when the mouse jumps.
		float tx = (float) (mc.mouseHandler.getScaledXPos(mc.getWindow()) / width * 2 - 1);
		float ty = (float) (mc.mouseHandler.getScaledYPos(mc.getWindow()) / height * 2 - 1);
		// A minimised window reports a zero size and the scaled mouse position comes out as NaN
		// (0/0); hold the current lean until the window is back.
		if (!Float.isFinite(tx) || !Float.isFinite(ty)) {
			return;
		}
		tx = Math.max(-1f, Math.min(1f, tx));
		ty = Math.max(-1f, Math.min(1f, ty));
		float k = (float) (1 - Math.exp(-dt * 2.5));
		parallaxX += (tx - parallaxX) * k;
		parallaxY += (ty - parallaxY) * k;
	}

	/** Opens (creating it if needed) the folder the custom pictures are read from. */
	public static void openFolder() {
		try {
			Files.createDirectories(ImageBackground.DIR);
		} catch (IOException e) {
			LOGGER.warn("Aurora could not create {}: {}", ImageBackground.DIR, e.toString());
		}
		Blaze3D.openPath(ImageBackground.DIR);
		IMAGES.rescanSoon();
	}
}
