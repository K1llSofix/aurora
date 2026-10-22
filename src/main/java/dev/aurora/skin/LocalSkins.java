package dev.aurora.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Skins for offline accounts, painted or picked in Aurora Launcher. The launcher passes its skin
 * folder as {@code -Daurora.skins}; without it, {@code config/aurora/skins} is used. A skin is
 * {@code <uuid>.png} with an optional {@code <uuid>.json} like {@code {"slim": true}}.
 *
 * <p>Only this game shows the skin: servers and other players keep their own idea of it.
 */
public final class LocalSkins {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Path DIR = folder();
	/** How often a file is looked at again, so edits in the launcher show up without a restart. */
	private static final long RECHECK_MS = 2000;
	private static final Map<UUID, Entry> CACHE = new HashMap<>();

	private record Entry(long modified, long checkedAt, @Nullable PlayerSkin skin) {
	}

	private LocalSkins() {
	}

	private static Path folder() {
		String fromLauncher = System.getProperty("aurora.skins");
		if (fromLauncher != null && !fromLauncher.isBlank()) {
			return Path.of(fromLauncher);
		}
		return FabricLoader.getInstance().getConfigDir().resolve("aurora").resolve("skins");
	}

	/**
	 * The local skin for a profile, or null to let the game use its own. Textures are created on
	 * the render thread only; other threads get the cached result.
	 */
	public static @Nullable PlayerSkin forProfile(@Nullable UUID id) {
		if (id == null) {
			return null;
		}
		long now = System.currentTimeMillis();
		Entry cached = CACHE.get(id);
		if (cached != null && now - cached.checkedAt < RECHECK_MS) {
			return cached.skin;
		}
		if (!RenderSystem.isOnRenderThread()) {
			return cached == null ? null : cached.skin;
		}
		Path file = DIR.resolve(id.toString().toLowerCase(Locale.ROOT) + ".png");
		long modified;
		try {
			modified = Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : -1;
		} catch (IOException e) {
			modified = -1;
		}
		if (cached != null && cached.modified == modified) {
			CACHE.put(id, new Entry(modified, now, cached.skin));
			return cached.skin;
		}
		PlayerSkin skin = modified < 0 ? null : load(id, file);
		if (skin == null && cached != null && cached.skin != null) {
			Minecraft.getInstance().getTextureManager().release(cached.skin.body().texturePath());
		}
		CACHE.put(id, new Entry(modified, now, skin));
		return skin;
	}

	private static @Nullable PlayerSkin load(UUID id, Path file) {
		NativeImage image;
		try (InputStream in = Files.newInputStream(file)) {
			image = process(NativeImage.read(in));
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Aurora: could not load the skin {}: {}", file, e.toString());
			return null;
		}
		Identifier texture = Identifier.fromNamespaceAndPath("aurora", "skins/" + id.toString().toLowerCase(Locale.ROOT));
		Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(texture::toString, image));
		PlayerModelType model = slim(file) ? PlayerModelType.SLIM : PlayerModelType.WIDE;
		return new PlayerSkin(new ClientAsset.ResourceTexture(texture, texture), null, null, model, true);
	}

	private static boolean slim(Path png) {
		Path json = png.resolveSibling(png.getFileName().toString().replace(".png", ".json"));
		try {
			JsonObject o = JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8)).getAsJsonObject();
			return o.has("slim") && o.get("slim").getAsBoolean();
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}

	/**
	 * What the game does to downloaded skins: an old 64×32 skin gets its left limbs mirrored from
	 * the right ones, and the base layer is made opaque so stray transparent pixels are not holes.
	 */
	private static NativeImage process(NativeImage image) {
		int w = image.getWidth();
		int h = image.getHeight();
		if (w != 64 || (h != 32 && h != 64)) {
			image.close();
			throw new IllegalStateException("skin is " + w + "x" + h + ", expected 64x64 or 64x32");
		}
		if (h == 32) {
			NativeImage full = new NativeImage(64, 64, true);
			full.copyFrom(image);
			image.close();
			image = full;
			image.fillRect(0, 32, 64, 32, 0);
			image.copyRect(4, 16, 16, 32, 4, 4, true, false);
			image.copyRect(8, 16, 16, 32, 4, 4, true, false);
			image.copyRect(0, 20, 24, 32, 4, 12, true, false);
			image.copyRect(4, 20, 16, 32, 4, 12, true, false);
			image.copyRect(8, 20, 8, 32, 4, 12, true, false);
			image.copyRect(12, 20, 16, 32, 4, 12, true, false);
			image.copyRect(44, 16, -8, 32, 4, 4, true, false);
			image.copyRect(48, 16, -8, 32, 4, 4, true, false);
			image.copyRect(40, 20, 0, 32, 4, 12, true, false);
			image.copyRect(44, 20, -8, 32, 4, 12, true, false);
			image.copyRect(48, 20, -16, 32, 4, 12, true, false);
			image.copyRect(52, 20, -8, 32, 4, 12, true, false);
		}
		opaque(image, 0, 0, 32, 16);
		opaque(image, 0, 16, 64, 32);
		opaque(image, 16, 48, 48, 64);
		return image;
	}

	private static void opaque(NativeImage image, int x0, int y0, int x1, int y1) {
		for (int x = x0; x < x1; x++) {
			for (int y = y0; y < y1; y++) {
				image.setPixel(x, y, ARGB.opaque(image.getPixel(x, y)));
			}
		}
	}
}
