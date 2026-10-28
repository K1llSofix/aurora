package dev.aurora.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.gui.widget.Ui;
import dev.aurora.module.utility.ScreenshotsModule;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.Easing;
import dev.aurora.util.Motion;
import dev.aurora.util.WinClipboard;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Your screenshots without leaving the game: a grid of thumbnails, newest first; a click opens one
 * large, with copy to clipboard, the folder and delete. Arrow keys flip through them.
 */
public final class GalleryScreen extends Screen {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int LIMIT = 300;
	private static final int THUMB_PX = 256;
	private static final int FULL_PX = 1920;
	private static final int PARALLEL_LOADS = 3;

	private record Shot(Path path, long modified) {
		String name() {
			return path.getFileName().toString();
		}
	}

	/** A picture on the GPU and its size in pixels. */
	private record Picture(DynamicTexture texture, int width, int height) {
	}

	private final @Nullable Screen parent;
	private final long openedAt = System.currentTimeMillis();
	private final List<Shot> shots = new ArrayList<>();
	private final Map<Path, Picture> thumbs = new HashMap<>();
	private final Set<Path> loading = new HashSet<>();
	private final Map<String, Animation> hovers = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();
	private @Nullable Shot open;
	private @Nullable Picture full;
	private @Nullable Path fullLoading;
	private boolean confirmDelete;
	private String status = "";
	private int statusColor = Theme.TEXT_DIM;
	private float scroll;
	private boolean closed;

	private record Hit(String id, float x, float y, float w, float h, @Nullable Shot shot) {
	}

	public GalleryScreen(@Nullable Screen parent) {
		super(Component.translatable("aurora.gallery.title"));
		this.parent = parent;
		reload();
	}

	private static Path folder() {
		return ScreenshotsModule.folder().toPath();
	}

	private void reload() {
		shots.clear();
		try (Stream<Path> files = Files.list(folder())) {
			files.filter(p -> {
					String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
					return (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")) && Files.isRegularFile(p);
				})
				.map(p -> {
					try {
						return new Shot(p, Files.getLastModifiedTime(p).toMillis());
					} catch (Exception e) {
						return new Shot(p, 0);
					}
				})
				.sorted(Comparator.comparingLong(Shot::modified).reversed())
				.limit(LIMIT)
				.forEach(shots::add);
		} catch (Exception e) {
			// No folder yet: no screenshots.
		}
	}

	private Animation hover(String id, boolean on) {
		return hovers.computeIfAbsent(id, k -> new Animation(0, 14)).target(on);
	}

	// ======================================================================= loading

	/**
	 * Reads and shrinks a picture in the background, then puts it on the GPU on the render thread.
	 * {@code done} gets null when the file cannot be read.
	 */
	private void load(Path path, int maxWidth, Consumer<@Nullable Picture> done) {
		CompletableFuture.supplyAsync(() -> {
			try (InputStream in = Files.newInputStream(path); NativeImage image = NativeImage.read(in)) {
				int w = Math.min(maxWidth, image.getWidth());
				int h = Math.max(1, Math.round(image.getHeight() * (w / (float) image.getWidth())));
				NativeImage small = new NativeImage(w, h, false);
				image.resizeSubRectTo(0, 0, image.getWidth(), image.getHeight(), small);
				return small;
			} catch (Exception e) {
				LOGGER.warn("Could not read screenshot {}", path, e);
				return null;
			}
		}, Util.nonCriticalIoPool()).thenAcceptAsync(image -> {
			if (image == null) {
				done.accept(null);
				return;
			}
			if (closed) {
				image.close();
				return;
			}
			int w = image.getWidth();
			int h = image.getHeight();
			DynamicTexture texture = new DynamicTexture(() -> "aurora_gallery_" + path.getFileName(), image);
			// The pixels now live on the GPU; free the copy in memory.
			image.close();
			done.accept(new Picture(texture, w, h));
		}, minecraft);
	}

	private void requestThumb(Shot shot) {
		if (thumbs.containsKey(shot.path) || loading.contains(shot.path) || loading.size() >= PARALLEL_LOADS) {
			return;
		}
		loading.add(shot.path);
		load(shot.path, THUMB_PX, picture -> {
			loading.remove(shot.path);
			if (picture != null) {
				thumbs.put(shot.path, picture);
			}
		});
	}

	private void openShot(Shot shot) {
		open = shot;
		confirmDelete = false;
		status = "";
		if (full != null) {
			full.texture.close();
			full = null;
		}
		fullLoading = shot.path;
		load(shot.path, FULL_PX, picture -> {
			if (picture == null) {
				return;
			}
			// Flipping on before it arrived: this one is no longer wanted.
			if (open == shot && fullLoading == shot.path) {
				full = picture;
				fullLoading = null;
			} else {
				picture.texture.close();
			}
		});
	}

	private void closeShot() {
		open = null;
		confirmDelete = false;
		status = "";
		if (full != null) {
			full.texture.close();
			full = null;
		}
		fullLoading = null;
	}

	// ======================================================================= drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Render2D.rect(g, 0, 0, width, height, 0xB005070D);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		hits.clear();
		float appear = Easing.OUT_CUBIC.clamped(Motion.progress(openedAt, 260));
		Ui.alpha = appear;
		float w = Math.min(620, width - 24);
		float h = Math.min(420, height - 24);
		float x = (width - w) / 2f;
		float y = (height - h) / 2f + (1 - appear) * 10;
		Render2D.shadow(g, x, y, w, h, 14, 20, Ui.c(0x8C000000));
		Render2D.roundRect(g, x, y, w, h, 14, Render2D.Paint.vertical(y, h, Ui.c(0xF5171A23), Ui.c(0xF50E1016)));
		Render2D.roundOutline(g, x, y, w, h, 14, 0.8f, Render2D.Paint.horizontal(x, w, Ui.c(ColorUtil.fade(Theme.accent(), 0.5f)), Ui.c(0x1FFFFFFF)));

		if (open == null) {
			grid(g, mouseX, mouseY, x, y, w, h);
		} else {
			viewer(g, mouseX, mouseY, x, y, w, h);
		}
		closeButton(g, mouseX, mouseY, x + w - 24, y + 14);
		Ui.alpha = 1f;
	}

	private void grid(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w, float h) {
		FontRenderer.SEMIBOLD.drawMiddle(g, I18n.get("aurora.gallery.title"), x + 18, y + 20, 13, Ui.c(Theme.TEXT));
		String subtitle = shots.isEmpty() ? I18n.get("aurora.gallery.empty") : I18n.get("aurora.gallery.count", shots.size());
		FontRenderer.MEDIUM.drawMiddle(g, subtitle, x + 18, y + 35, 7.5f, Ui.c(Theme.TEXT_DIM));
		String folderLabel = I18n.get("aurora.ui.open_folder");
		float fw = FontRenderer.SEMIBOLD.width(folderLabel, 8) + 20;
		button(g, mx, my, "folder", folderLabel, x + w - 40 - fw, y + 9, fw, 20, false);

		float top = y + 50;
		float bottom = y + h - 12;
		float left = x + 14;
		float inner = w - 28;
		int columns = Math.max(2, (int) (inner / 140));
		float gap = 8;
		float cw = (inner - gap * (columns - 1)) / columns;
		float ch = cw * 9 / 16f;
		int rows = (shots.size() + columns - 1) / columns;
		float content = rows * (ch + gap);
		scroll = Math.max(0, Math.min(scroll, Math.max(0, content - (bottom - top))));
		g.enableScissor(Math.round(left - 2), Math.round(top), Math.round(left + inner + 2), Math.round(bottom));
		for (int i = 0; i < shots.size(); i++) {
			Shot shot = shots.get(i);
			float cx = left + (i % columns) * (cw + gap);
			float cy = top + (i / columns) * (ch + gap) - scroll;
			if (cy + ch < top || cy > bottom) {
				continue;
			}
			requestThumb(shot);
			String id = "shot" + i;
			boolean inside = Ui.inside(mx, my, cx, cy, cw, ch) && my >= top && my <= bottom;
			float hv = hover(id, inside).get();
			Render2D.roundRect(g, cx, cy, cw, ch, 6, Ui.c(0xFF0B0D12));
			Picture thumb = thumbs.get(shot.path);
			if (thumb != null) {
				drawCover(g, thumb, cx, cy, cw, ch, Ui.c(0xFFFFFFFF));
			} else {
				float t = (float) (System.nanoTime() / 1e9);
				Render2D.arc(g, cx + cw / 2f, cy + ch / 2f, 6, 1.4f, (t * 360) % 360, 110, Ui.c(ColorUtil.fade(Theme.accent(), 0.7f)));
			}
			if (hv > 0.01f) {
				Render2D.rect(g, cx, cy + ch - 16, cw, 16, Ui.c(ColorUtil.withAlpha(0x05070D, Math.round(180 * hv))));
				FontRenderer.MEDIUM.drawMiddle(g, FontRenderer.MEDIUM.ellipsize(date(shot), 7, cw - 10), cx + 5, cy + ch - 8, 7,
					Ui.c(ColorUtil.fade(Theme.TEXT, hv)));
			}
			Render2D.roundOutline(g, cx, cy, cw, ch, 6, 0.8f + hv * 0.4f,
				Ui.c(ColorUtil.lerp(0x1FFFFFFF, Theme.accent(), hv)));
			if (inside) {
				hits.add(new Hit("shot", cx, cy, cw, ch, shot));
			}
		}
		g.disableScissor();
	}

	private void viewer(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w, float h) {
		Shot shot = open;
		String title = FontRenderer.SEMIBOLD.ellipsize(shot.name(), 11, w - 70);
		FontRenderer.SEMIBOLD.drawMiddle(g, title, x + 18, y + 20, 11, Ui.c(Theme.TEXT));
		int index = shots.indexOf(shot);
		FontRenderer.MEDIUM.drawMiddle(g, date(shot) + "  ·  " + (index + 1) + " / " + shots.size(), x + 18, y + 35, 7.5f, Ui.c(Theme.TEXT_DIM));

		float top = y + 48;
		float bottom = y + h - 44;
		float left = x + 14;
		float inner = w - 28;
		float boxH = bottom - top;
		Render2D.roundRect(g, left, top, inner, boxH, 8, Ui.c(0xFF07080C));
		Picture picture = full != null ? full : thumbs.get(shot.path);
		if (picture != null) {
			float scale = Math.min(inner / picture.width, boxH / picture.height);
			float pw = picture.width * scale;
			float ph = picture.height * scale;
			float px = left + (inner - pw) / 2f;
			float py = top + (boxH - ph) / 2f;
			Render2D.texture(g, setup(picture), px, py, px + pw, py + ph, 0, 0, 1, 1, Ui.c(0xFFFFFFFF));
		}
		if (full == null) {
			float t = (float) (System.nanoTime() / 1e9);
			Render2D.arc(g, left + inner - 14, top + 14, 5, 1.4f, (t * 360) % 360, 110, Ui.c(Theme.accent()));
		}
		// Arrows on the sides when there is a neighbour.
		if (index > 0) {
			arrow(g, mx, my, "prev", left + 16, top + boxH / 2f, -1);
		}
		if (index < shots.size() - 1) {
			arrow(g, mx, my, "next", left + inner - 16, top + boxH / 2f, 1);
		}

		float by = y + h - 34;
		float bw = (inner - 3 * 8) / 4f;
		button(g, mx, my, "back", I18n.get("gui.back"), left, by, bw, 22, false);
		if (WinClipboard.supported()) {
			button(g, mx, my, "copy", I18n.get("aurora.gallery.copy"), left + (bw + 8), by, bw, 22, true);
		}
		button(g, mx, my, "folder", I18n.get("aurora.ui.open_folder"), left + (bw + 8) * 2, by, bw, 22, false);
		dangerButton(g, mx, my, I18n.get(confirmDelete ? "aurora.gallery.delete_confirm" : "aurora.gallery.delete"), left + (bw + 8) * 3, by, bw, 22);
		if (!status.isEmpty()) {
			FontRenderer.MEDIUM.drawCentered(g, status, x + w / 2f, bottom + 4.5f, 7.5f, Ui.c(statusColor));
		}
	}

	/** The date in the game's language, not the system's. */
	private String date(Shot shot) {
		if (shot.modified <= 0) {
			return shot.name();
		}
		Locale locale = Locale.forLanguageTag(minecraft.options.languageCode.replace('_', '-'));
		return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale).format(Instant.ofEpochMilli(shot.modified).atZone(ZoneId.systemDefault()));
	}

	private static TextureSetup setup(Picture picture) {
		return TextureSetup.singleTexture(picture.texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
	}

	/** Fills the box, cropping the picture's longer side like a photo frame. */
	private static void drawCover(GuiGraphicsExtractor g, Picture picture, float x, float y, float w, float h, int color) {
		float boxAspect = w / h;
		float aspect = picture.width / (float) picture.height;
		float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
		if (aspect > boxAspect) {
			float keep = boxAspect / aspect;
			u0 = (1 - keep) / 2f;
			u1 = u0 + keep;
		} else {
			float keep = aspect / boxAspect;
			v0 = (1 - keep) / 2f;
			v1 = v0 + keep;
		}
		Render2D.texture(g, setup(picture), x, y, x + w, y + h, u0, v0, u1, v1, color);
	}

	private void arrow(GuiGraphicsExtractor g, int mx, int my, String id, float cx, float cy, int direction) {
		float hv = hover(id, Ui.inside(mx, my, cx - 12, cy - 12, 24, 24)).get();
		Render2D.circle(g, cx, cy, 11, Ui.c(ColorUtil.withAlpha(0x0B0D12, Math.round(150 + 80 * hv))));
		Render2D.chevron(g, cx, cy, 7, 1.6f, direction > 0 ? 0 : 2, Ui.c(ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv)));
		hits.add(new Hit(id, cx - 12, cy - 12, 24, 24, null));
	}

	private void button(GuiGraphicsExtractor g, int mx, int my, String id, String label, float x, float y, float w, float h, boolean primary) {
		float hv = hover(id, Ui.inside(mx, my, x, y, w, h)).get();
		if (primary) {
			Render2D.roundRect(g, x, y, w, h, 8, Ui.accentPaint(x, w, 0.75f + 0.25f * hv));
		} else {
			Render2D.roundRect(g, x, y, w, h, 8, Ui.c(ColorUtil.lerp(0xCC161922, 0xE61F2430, hv)));
			Render2D.roundOutline(g, x, y, w, h, 8, 0.7f, Ui.c(ColorUtil.lerp(0x24FFFFFF, 0x47FFFFFF, hv)));
		}
		String text = FontRenderer.SEMIBOLD.ellipsize(label, 8, w - 12);
		FontRenderer.SEMIBOLD.drawCentered(g, text, x + w / 2f, y + h / 2f, 8, Ui.c(primary ? Theme.onAccent() : Theme.TEXT));
		hits.add(new Hit(id, x, y, w, h, null));
	}

	private void dangerButton(GuiGraphicsExtractor g, int mx, int my, String label, float x, float y, float w, float h) {
		float hv = hover("delete", Ui.inside(mx, my, x, y, w, h)).get();
		int fill = confirmDelete ? Theme.DANGER : ColorUtil.lerp(0xCC161922, ColorUtil.withAlpha(Theme.DANGER, 0x40), hv);
		Render2D.roundRect(g, x, y, w, h, 8, Ui.c(fill));
		Render2D.roundOutline(g, x, y, w, h, 8, 0.7f, Ui.c(ColorUtil.withAlpha(Theme.DANGER, Math.round(90 + 120 * hv))));
		String text = FontRenderer.SEMIBOLD.ellipsize(label, 8, w - 12);
		FontRenderer.SEMIBOLD.drawCentered(g, text, x + w / 2f, y + h / 2f, 8, Ui.c(confirmDelete ? 0xFFFFFFFF : Theme.DANGER));
		hits.add(new Hit("delete", x, y, w, h, null));
	}

	private void closeButton(GuiGraphicsExtractor g, int mx, int my, float cx, float cy) {
		float hv = hover("x", Ui.inside(mx, my, cx - 9, cy - 9, 18, 18)).get();
		if (hv > 0.01f) {
			Render2D.circle(g, cx, cy, 9, Ui.c(ColorUtil.fade(0xFFFFFFFF, 0.08f * hv)));
		}
		Render2D.cross(g, cx, cy, 7, 1.3f, Ui.c(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv)));
		hits.add(new Hit("close", cx - 9, cy - 9, 18, 18, null));
	}

	// ======================================================================= actions

	private void step(int direction) {
		if (open == null) {
			return;
		}
		int index = shots.indexOf(open) + direction;
		if (index >= 0 && index < shots.size()) {
			openShot(shots.get(index));
		}
	}

	private void delete() {
		if (open == null) {
			return;
		}
		if (!confirmDelete) {
			confirmDelete = true;
			return;
		}
		Shot shot = open;
		try {
			Files.deleteIfExists(shot.path);
		} catch (Exception e) {
			status = I18n.get("aurora.gallery.delete_failed");
			statusColor = Theme.DANGER;
			confirmDelete = false;
			return;
		}
		int index = shots.indexOf(shot);
		shots.remove(shot);
		Picture thumb = thumbs.remove(shot.path);
		if (thumb != null) {
			thumb.texture.close();
		}
		if (shots.isEmpty()) {
			closeShot();
		} else {
			openShot(shots.get(Math.min(index, shots.size() - 1)));
		}
	}

	private void copy() {
		if (open == null) {
			return;
		}
		status = I18n.get("aurora.gallery.copying");
		statusColor = Theme.TEXT_DIM;
		ScreenshotsModule.copyToClipboard(open.path.toFile(), copied -> {
			status = I18n.get(copied ? "aurora.screenshot.copied" : "aurora.screenshot.copy_failed");
			statusColor = copied ? Theme.SUCCESS : Theme.DANGER;
		});
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 1) {
			return super.mouseClicked(event, doubleClick);
		}
		for (Hit hit : hits) {
			if (!Ui.inside(event.x(), event.y(), hit.x(), hit.y(), hit.w(), hit.h())) {
				continue;
			}
			if (!"delete".equals(hit.id())) {
				confirmDelete = false;
			}
			switch (hit.id()) {
				case "close" -> onClose();
				case "shot" -> openShot(hit.shot());
				case "back" -> closeShot();
				case "prev" -> step(-1);
				case "next" -> step(1);
				case "copy" -> copy();
				case "folder" -> ScreenshotsModule.openFolder();
				case "delete" -> delete();
				default -> {
				}
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (open == null) {
			scroll -= (float) scrollY * 30;
		} else {
			step(scrollY > 0 ? -1 : 1);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (open != null) {
			switch (event.key()) {
				case InputConstants.KEY_LEFT -> {
					step(-1);
					return true;
				}
				case InputConstants.KEY_RIGHT -> {
					step(1);
					return true;
				}
				case InputConstants.KEY_DELETE -> {
					delete();
					return true;
				}
				default -> {
				}
			}
			if (event.isEscape()) {
				closeShot();
				return true;
			}
			if (event.isCopy() && WinClipboard.supported()) {
				copy();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void removed() {
		closed = true;
		thumbs.values().forEach(p -> p.texture.close());
		thumbs.clear();
		if (full != null) {
			full.texture.close();
			full = null;
		}
	}
}
