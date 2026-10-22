package dev.aurora.gui.background;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.aurora.gui.render.Render2D;
import dev.aurora.util.ColorUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * The user's own pictures from {@code config/aurora/backgrounds}: PNG, JPG, BMP or animated GIF.
 * Files are decoded off the render thread, uploaded a few frames at a time, cross-faded, and shown
 * with a slow pan and zoom; several files play as a slideshow. The folder is watched, so a picture
 * dropped in appears without a restart.
 */
final class ImageBackground {
	private static final Logger LOGGER = LogUtils.getLogger();
	static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("aurora").resolve("backgrounds");
	private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp");
	private static final long SCAN_INTERVAL = 2_000_000_000L;
	private static final double SLIDE_SECONDS = 40;
	private static final double FADE_SECONDS = 1.2;
	/** Pixels across all frames of one animation; keeps a long GIF within ~128 MB of video memory. */
	private static final long FRAME_BUDGET = 32L * 1024 * 1024;
	private static final int MAX_FRAMES = 1500;
	private static final int UPLOADS_PER_FRAME = 8;

	private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "Aurora background loader");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		return thread;
	});

	private record Entry(Path path, long modified, long size) {
		boolean jpeg() {
			String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
			return name.endsWith(".jpg") || name.endsWith(".jpeg");
		}
	}

	/** Frames decoded on the loader thread, waiting to be uploaded. */
	private record Decoded(Entry entry, List<NativeImage> frames, int[] delays, int width, int height) {
		void close() {
			frames.forEach(NativeImage::close);
		}
	}

	/** A picture on the GPU. */
	private static final class Picture {
		final Entry entry;
		final List<DynamicTexture> textures = new ArrayList<>();
		final int[] delays;
		final int totalMs;
		final int width;
		final int height;
		double shownAt;

		Picture(Decoded decoded) {
			entry = decoded.entry();
			delays = decoded.delays();
			width = decoded.width();
			height = decoded.height();
			int sum = 0;
			for (int d : delays) {
				sum += d;
			}
			totalMs = Math.max(1, sum);
		}

		boolean animated() {
			return textures.size() > 1;
		}

		int frameAt(double seconds) {
			if (!animated()) {
				return 0;
			}
			long ms = (long) (seconds * 1000) % totalMs;
			for (int i = 0; i < delays.length; i++) {
				ms -= delays[i];
				if (ms < 0) {
					return Math.min(i, textures.size() - 1);
				}
			}
			return textures.size() - 1;
		}

		double duration() {
			return animated() ? Math.max(SLIDE_SECONDS, Math.ceil(SLIDE_SECONDS * 1000 / totalMs) * totalMs / 1000.0) : SLIDE_SECONDS;
		}

		void close() {
			textures.forEach(DynamicTexture::close);
			textures.clear();
		}
	}

	private List<Entry> entries = List.of();
	private final Set<Entry> failed = new HashSet<>();
	private long lastScan;
	private @Nullable Picture current;
	private @Nullable Picture previous;
	private double fadeStart;
	private @Nullable CompletableFuture<Decoded> pending;
	private @Nullable Entry pendingEntry;
	/** A decoded picture being uploaded over several frames. */
	private @Nullable Decoded uploading;
	private @Nullable Picture building;

	private static double now() {
		return System.nanoTime() / 1e9;
	}

	/** Forces a rescan of the folder on the next frame, e.g. after the user opened it. */
	void rescanSoon() {
		lastScan = 0;
	}

	int count() {
		return entries.size();
	}

	// ======================================================================= lifecycle

	/** Loads, uploads and switches pictures; frees everything when not {@code wanted}. */
	void update(boolean wanted, int screenW, int screenH) {
		if (!wanted) {
			release();
			return;
		}
		long nanos = System.nanoTime();
		if (lastScan == 0 || nanos - lastScan > SCAN_INTERVAL) {
			lastScan = nanos;
			scan();
		}
		double now = now();
		collectPending();
		continueUpload(now);

		List<Entry> usable = new ArrayList<>(entries);
		usable.removeAll(failed);
		if (usable.isEmpty()) {
			if (current != null || previous != null) {
				releasePictures();
			}
			return;
		}
		Entry want = null;
		int at = current == null ? -1 : usable.indexOf(current.entry);
		if (at < 0) {
			want = usable.get(0);
		} else if (usable.size() > 1 && now - current.shownAt > current.duration()) {
			want = usable.get((at + 1) % usable.size());
		}
		if (want != null && pending == null && uploading == null) {
			Entry entry = want;
			pendingEntry = entry;
			pending = CompletableFuture.supplyAsync(() -> {
				try {
					return decode(entry, screenW, screenH);
				} catch (Throwable e) {
					LOGGER.warn("Aurora could not load background {}: {}", entry.path().getFileName(), e.toString());
					return null;
				}
			}, LOADER);
		}
		if (previous != null && now - fadeStart > FADE_SECONDS) {
			previous.close();
			previous = null;
		}
	}

	private void scan() {
		List<Entry> found = new ArrayList<>();
		if (Files.isDirectory(DIR)) {
			try (Stream<Path> files = Files.list(DIR)) {
				files.filter(Files::isRegularFile)
					.filter(p -> {
						String name = p.getFileName().toString();
						int dot = name.lastIndexOf('.');
						return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
					})
					.sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
					.forEach(p -> {
						try {
							found.add(new Entry(p, Files.getLastModifiedTime(p).toMillis(), Files.size(p)));
						} catch (IOException ignored) {
							// Vanished between listing and reading; the next scan settles it.
						}
					});
			} catch (IOException e) {
				LOGGER.warn("Aurora could not list {}: {}", DIR, e.toString());
			}
		}
		entries = List.copyOf(found);
		failed.retainAll(entries);
	}

	private void collectPending() {
		if (pending == null || !pending.isDone()) {
			return;
		}
		Decoded decoded = pending.getNow(null);
		Entry entry = pendingEntry;
		pending = null;
		pendingEntry = null;
		if (decoded == null) {
			if (entry != null) {
				failed.add(entry);
			}
			return;
		}
		uploading = decoded;
		building = new Picture(decoded);
	}

	/** Uploads a few frames per render frame so a long GIF never freezes the menu. */
	private void continueUpload(double now) {
		if (uploading == null || building == null) {
			return;
		}
		List<NativeImage> frames = uploading.frames();
		int done = building.textures.size();
		int until = Math.min(frames.size(), done + UPLOADS_PER_FRAME);
		for (int i = done; i < until; i++) {
			NativeImage image = frames.get(i);
			int index = i;
			DynamicTexture texture = new DynamicTexture(() -> "aurora_background_" + index, image);
			// The pixels now live on the GPU; free the copy in memory.
			image.close();
			building.textures.add(texture);
		}
		if (building.textures.size() < frames.size()) {
			return;
		}
		if (previous != null) {
			previous.close();
		}
		previous = current;
		current = building;
		current.shownAt = now;
		fadeStart = now;
		uploading = null;
		building = null;
	}

	private void releasePictures() {
		if (current != null) {
			current.close();
			current = null;
		}
		if (previous != null) {
			previous.close();
			previous = null;
		}
	}

	private void release() {
		releasePictures();
		if (building != null) {
			building.close();
			building = null;
		}
		if (uploading != null) {
			uploading.close();
			uploading = null;
		}
		if (pending != null) {
			pending.thenAccept(d -> {
				if (d != null) {
					d.close();
				}
			});
			pending = null;
			pendingEntry = null;
		}
		lastScan = 0;
	}

	// ======================================================================= drawing

	/** How much of the screen the pictures cover: below 1 the scene behind must still be drawn. */
	float coverage() {
		if (current == null) {
			return 0;
		}
		if (previous != null) {
			return 1;
		}
		return (float) Math.min(1, (now() - fadeStart) / FADE_SECONDS);
	}

	void draw(Frame f) {
		double now = now();
		float fade = (float) Math.min(1, (now - fadeStart) / FADE_SECONDS);
		if (previous != null) {
			draw(f, previous, 1f);
		}
		if (current != null) {
			draw(f, current, fade);
		}
	}

	private static void draw(Frame f, Picture picture, float alpha) {
		if (picture.textures.isEmpty() || alpha <= 0) {
			return;
		}
		float w = f.w();
		float h = f.h();
		// Cover the screen: crop whichever side of the picture is too long.
		float screenAspect = w / h;
		float imageAspect = picture.width / (float) picture.height;
		float fw = 1;
		float fh = 1;
		if (imageAspect > screenAspect) {
			fw = screenAspect / imageAspect;
		} else {
			fh = imageAspect / screenAspect;
		}
		// Physical pixels per picture pixel: big enlargements of PNG / GIF are pixel art, which
		// stays crisp and still; everything else drifts slowly (the Ken Burns effect).
		float scale = (w / f.px()) / (picture.width * fw);
		boolean pixelArt = scale >= 2.5f && !picture.entry.jpeg();
		float zoom = 1;
		float ox = 0;
		float oy = 0;
		if (!pixelArt) {
			double t = f.t();
			zoom = (float) (1.06 + 0.06 * (0.5 - 0.5 * Math.cos(t * 2 * Math.PI / 70)));
			ox = Math.max(-1f, Math.min(1f, (float) Math.sin(t * 2 * Math.PI / 53) * 0.6f + f.mx() * 0.4f));
			oy = Math.max(-1f, Math.min(1f, (float) Math.sin(t * 2 * Math.PI / 61 + 1) * 0.5f + f.my() * 0.4f));
		}
		fw /= zoom;
		fh /= zoom;
		float cu = 0.5f + ox * (1 - fw) / 2f;
		float cv = 0.5f + oy * (1 - fh) / 2f;
		DynamicTexture texture = picture.textures.get(picture.frameAt(f.t()));
		TextureSetup setup = TextureSetup.singleTexture(texture.getTextureView(),
			RenderSystem.getSamplerCache().getClampToEdge(pixelArt ? FilterMode.NEAREST : FilterMode.LINEAR));
		Render2D.texture(f.g(), setup, 0, 0, w, h, cu - fw / 2, cv - fh / 2, cu + fw / 2, cv + fh / 2,
			ColorUtil.fade(0xFFFFFFFF, alpha));
	}

	// ======================================================================= decoding

	private static Decoded decode(Entry entry, int screenW, int screenH) throws IOException {
		try (ImageInputStream in = ImageIO.createImageInputStream(entry.path().toFile())) {
			if (in == null) {
				throw new IOException("cannot open file");
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (!readers.hasNext()) {
				throw new IOException("unsupported image format");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(in, false, false);
				boolean gif = "gif".equalsIgnoreCase(reader.getFormatName());
				int count = gif ? reader.getNumImages(true) : 1;
				if (count > 1) {
					return animation(entry, reader, Math.min(count, MAX_FRAMES), screenW, screenH);
				}
				BufferedImage image = reader.read(0);
				// Leave room for the zoom of the Ken Burns effect, never enlarge, cap the size.
				float scale = Math.max(screenW / (float) image.getWidth(), screenH / (float) image.getHeight()) * 1.15f;
				scale = Math.min(Math.min(1f, scale), 4096f / Math.max(image.getWidth(), image.getHeight()));
				int tw = Math.max(1, Math.round(image.getWidth() * scale));
				int th = Math.max(1, Math.round(image.getHeight() * scale));
				return new Decoded(entry, List.of(resized(image, tw, th)), new int[]{1000}, tw, th);
			} finally {
				reader.dispose();
			}
		}
	}

	/** Composites GIF frames the way browsers do, honouring frame offsets and disposal. */
	private static Decoded animation(Entry entry, ImageReader reader, int count, int screenW, int screenH) throws IOException {
		int sw = 0;
		int sh = 0;
		IIOMetadata stream = reader.getStreamMetadata();
		if (stream != null) {
			Node screen = child(stream.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
			sw = attribute(screen, "logicalScreenWidth", 0);
			sh = attribute(screen, "logicalScreenHeight", 0);
		}
		int[] left = new int[count];
		int[] top = new int[count];
		int[] delays = new int[count];
		String[] disposal = new String[count];
		for (int i = 0; i < count; i++) {
			Node root = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
			Node descriptor = child(root, "ImageDescriptor");
			Node control = child(root, "GraphicControlExtension");
			left[i] = attribute(descriptor, "imageLeftPosition", 0);
			top[i] = attribute(descriptor, "imageTopPosition", 0);
			sw = Math.max(sw, left[i] + attribute(descriptor, "imageWidth", 0));
			sh = Math.max(sh, top[i] + attribute(descriptor, "imageHeight", 0));
			int delay = attribute(control, "delayTime", 10) * 10;
			// Like browsers: "as fast as possible" delays play at 10 fps.
			delays[i] = delay < 20 ? 100 : delay;
			disposal[i] = control == null ? "none" : attributeText(control, "disposalMethod");
		}
		if (sw <= 0 || sh <= 0) {
			throw new IOException("empty animation");
		}
		float scale = Math.min(1f, Math.max(screenW / (float) sw, screenH / (float) sh));
		double total = (double) count * sw * sh * scale * scale;
		if (total > FRAME_BUDGET) {
			scale *= (float) Math.sqrt(FRAME_BUDGET / total);
		}
		int tw = Math.max(1, Math.round(sw * scale));
		int th = Math.max(1, Math.round(sh * scale));

		BufferedImage canvas = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		List<NativeImage> frames = new ArrayList<>(count);
		try {
			for (int i = 0; i < count; i++) {
				BufferedImage frame = reader.read(i);
				int fw = Math.min(frame.getWidth(), sw - left[i]);
				int fh = Math.min(frame.getHeight(), sh - top[i]);
				int[] saved = "restoreToPrevious".equals(disposal[i]) && fw > 0 && fh > 0
					? canvas.getRGB(left[i], top[i], fw, fh, null, 0, fw) : null;
				g.drawImage(frame, left[i], top[i], null);
				frames.add(resized(canvas, tw, th));
				if ("restoreToBackgroundColor".equals(disposal[i]) && fw > 0 && fh > 0) {
					g.setComposite(AlphaComposite.Clear);
					g.fillRect(left[i], top[i], fw, fh);
					g.setComposite(AlphaComposite.SrcOver);
				} else if (saved != null) {
					canvas.setRGB(left[i], top[i], fw, fh, saved, 0, fw);
				}
			}
		} catch (IOException | RuntimeException | OutOfMemoryError e) {
			frames.forEach(NativeImage::close);
			throw e;
		} finally {
			g.dispose();
		}
		return new Decoded(entry, frames, delays, tw, th);
	}

	/** Copies a Java image into native memory, resampling it to the given size if needed. */
	private static NativeImage resized(BufferedImage image, int width, int height) {
		int w = image.getWidth();
		int h = image.getHeight();
		NativeImage full = new NativeImage(w, h, false);
		int[] row = new int[w];
		for (int y = 0; y < h; y++) {
			image.getRGB(0, y, w, 1, row, 0, w);
			for (int x = 0; x < w; x++) {
				full.setPixel(x, y, row[x]);
			}
		}
		if (w == width && h == height) {
			return full;
		}
		NativeImage out = new NativeImage(width, height, false);
		full.resizeSubRectTo(0, 0, w, h, out);
		full.close();
		return out;
	}

	private static @Nullable Node child(@Nullable Node parent, String name) {
		if (parent == null) {
			return null;
		}
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (name.equals(node.getNodeName())) {
				return node;
			}
		}
		return null;
	}

	private static int attribute(@Nullable Node node, String name, int fallback) {
		String text = attributeText(node, name);
		try {
			return text.isEmpty() ? fallback : Integer.parseInt(text);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static String attributeText(@Nullable Node node, String name) {
		if (node == null) {
			return "";
		}
		NamedNodeMap attributes = node.getAttributes();
		Node value = attributes == null ? null : attributes.getNamedItem(name);
		return value == null ? "" : value.getNodeValue();
	}
}
