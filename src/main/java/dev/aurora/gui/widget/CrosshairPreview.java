package dev.aurora.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.module.visual.CrosshairModule;
import dev.aurora.util.Animation;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Live preview on the Crosshair settings page: a small first-person scene (meadow, snow, desert,
 * cave, Nether, night) with a zombie wandering past, so colour, size and the dynamic options can be
 * judged without leaving the menu. Clicking the scene swings at the zombie, which shows the
 * cooldown ring and the on-target colour; the movement buttons show the dynamic spread.
 */
public final class CrosshairPreview {
	private static final Identifier ZOMBIE = Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png");
	private static final int BEZEL = 0xFF0A0C11;
	private static final int SUN = 1, CLOUDS = 2, STARS = 4, CEILING = 8;
	private static final String[] MOTIONS = {"stand", "walk", "sprint", "jump"};
	private static final int[] ZOOMS = {1, 2, 4};
	/** A sword's attack cooldown: 1.6 attacks a second. */
	private static final float COOLDOWN_MS = 625;
	/** Height of everything under the scene in the full layout. */
	private static final float CONTROLS_H = 96;

	/** Ground texture and tint, distant ground, sky gradient, fog, and the light on the zombie. */
	private record Scene(String id, String ground, int tint, int far, int skyTop, int skyBottom, int fog, int light, int flags) {
		boolean has(int flag) {
			return (flags & flag) != 0;
		}
	}

	private static final Scene[] SCENES = {
		new Scene("plains", "grass_block_top", 0xFF8DB858, 0xFF6E9845, 0xFF5D8EE0, 0xFFB6D0F5, 0xFFBCD4F2, 0xFFFFFFFF, SUN | CLOUDS),
		new Scene("snow", "snow", 0xFFFFFFFF, 0xFFE6ECF2, 0xFF8EA6C4, 0xFFD9E2EC, 0xFFDCE4EE, 0xFFFFFFFF, CLOUDS),
		new Scene("desert", "sand", 0xFFFFFFFF, 0xFFD9CA98, 0xFF4F8AE0, 0xFFE4D3A6, 0xFFE2D3A9, 0xFFFFFFFF, SUN),
		new Scene("cave", "stone", 0xFF7A7A7A, 0xFF2A2B2E, 0xFF060607, 0xFF111215, 0xFF0F1013, 0xFF9A9A9A, CEILING),
		new Scene("nether", "netherrack", 0xFFE0E0E0, 0xFF4E1715, 0xFF210505, 0xFF571510, 0xFF4A120E, 0xFFE8C8C0, 0),
		new Scene("night", "grass_block_top", 0xFF31492B, 0xFF18241A, 0xFF02040C, 0xFF0D1830, 0xFF0C162B, 0xFF5E6878, STARS),
	};

	// Remembered while the game runs, like the menu's other choices.
	private static int scene;
	private static int motion;
	private static int zoom;

	private record Hit(float x, float y, float w, float h, Runnable action, @Nullable String tooltip) {
	}

	private final CrosshairModule module;
	private final Animation spread = new Animation(0, 12);
	private final Animation onTarget = new Animation(0, 14);
	private final Animation cooldownAlpha = new Animation(0, 10);
	private final Animation motionIndicator = new Animation(motion, 16);
	private final List<Hit> hits = new ArrayList<>();
	private long lastFrame = System.nanoTime();
	/** Seconds since the preview opened: moves the zombie, the clouds and the jumps. */
	private float clock;
	/** Blocks walked, which scrolls the ground. */
	private float walked;
	private long swingAt = Long.MIN_VALUE;
	private long hurtAt = Long.MIN_VALUE;
	/** The crosshair is on the zombie this frame. */
	private boolean aimed;
	private float sceneX, sceneY, sceneW, sceneH;

	public CrosshairPreview(CrosshairModule module) {
		this.module = module;
	}

	/** Shortest height that still fits the scene with its controls under it. */
	public static float fullHeight(float width) {
		return Math.min(width * 0.92f, 150) + 14 + CONTROLS_H;
	}

	/**
	 * Draws the preview in the given box: with a title and the controls under the scene when there
	 * is room, otherwise just the scene with small buttons on top of it.
	 */
	public void render(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w, float h) {
		hits.clear();
		tick();
		if (h < 14 + 94 + CONTROLS_H) {
			scene(g, mx, my, x, y, w, h, true);
		} else {
			FontRenderer.SEMIBOLD.drawMiddle(g, I18n.get("aurora.preview.title").toUpperCase(Locale.ROOT), x + 2, y + 6, 7, Ui.c(Theme.TEXT_MUTED));
			float vh = Math.min(w * 0.92f, h - 14 - CONTROLS_H);
			scene(g, mx, my, x, y + 14, w, vh, false);
			float cy = y + 14 + vh + 10;
			label(g, "aurora.preview.background", x, cy);
			sceneChips(g, mx, my, x, cy + 9, w);
			cy += 9 + 14 + 9;
			label(g, "aurora.preview.motion", x, cy);
			motionSwitch(g, mx, my, x, cy + 9, w, 16);
			cy += 9 + 16 + 8;
			String hint = FontRenderer.MEDIUM.ellipsize(I18n.get("aurora.preview.hint"), 7, w - 4);
			FontRenderer.MEDIUM.drawMiddle(g, hint, x + 2, cy + 3.5f, 7, Ui.c(Theme.TEXT_MUTED));
		}
		for (Hit hit : hits) {
			if (hit.tooltip != null && Ui.inside(mx, my, hit.x, hit.y, hit.w, hit.h)) {
				g.setTooltipForNextFrame(Component.literal(hit.tooltip), mx, my);
			}
		}
	}

	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 1) {
			return false;
		}
		for (Hit hit : hits) {
			if (Ui.inside(mx, my, hit.x, hit.y, hit.w, hit.h)) {
				hit.action.run();
				return true;
			}
		}
		if (Ui.inside(mx, my, sceneX, sceneY, sceneW, sceneH)) {
			swing();
			return true;
		}
		return false;
	}

	private void tick() {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		clock += dt;
		walked = (walked + speed() * dt) % 1024f;
	}

	/** Blocks a second, as in the game. */
	private static float speed() {
		return motion == 2 ? 5.6f : motion == 1 ? 4.3f : 0f;
	}

	private void swing() {
		long now = System.currentTimeMillis();
		float strength = strength(now);
		swingAt = now;
		SoundEvent sound = SoundEvents.PLAYER_ATTACK_NODAMAGE;
		if (aimed) {
			hurtAt = now;
			sound = strength > 0.9f ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK;
		}
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1f, 0.5f));
	}

	private float strength(long now) {
		return swingAt == Long.MIN_VALUE ? 1f : ColorUtil.clamp01((now - swingAt) / COOLDOWN_MS);
	}

	// ======================================================================= the scene

	private void scene(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w, float h, boolean compact) {
		Scene s = SCENES[scene];
		long now = System.currentTimeMillis();
		float px = Render2D.pixel(g);
		// One setting pixel of the crosshair, measured before the zoom so that zooming magnifies it.
		float unit = px * 2;
		float ix = x + 2.5f, iy = y + 2.5f, iw = w - 5, ih = h - 5;
		sceneX = ix;
		sceneY = iy;
		sceneW = iw;
		sceneH = ih;
		// Centre on the physical pixel grid, like the real crosshair.
		float cx = Math.round((ix + iw / 2f) / px) * px;
		float cy = Math.round((iy + ih / 2f) / px) * px;

		boolean walking = motion == 1 || motion == 2;
		float lift = 0;
		boolean airborne = false;
		if (motion == 3) {
			float cycle = clock % 1.15f;
			if (cycle < 0.6f) {
				float p = cycle / 0.6f;
				lift = 1.25f * 4 * p * (1 - p);
				airborne = true;
			}
		}

		Render2D.shadow(g, x, y, w, h, 8, 10, Ui.c(0x60000000));
		g.enableScissor(Math.round(ix), Math.round(iy), Math.round(ix + iw), Math.round(iy + ih));
		g.pose().pushMatrix();
		float z = ZOOMS[zoom];
		g.pose().translate(cx, cy);
		g.pose().scale(z, z);
		g.pose().translate(-cx, -cy);

		float top = iy;
		float bottom = iy + ih;
		float horizon = iy + ih * 0.4f;
		float f = ih * 0.95f;
		float eye = 1.62f + lift;
		Render2D.verticalGradient(g, ix, top, iw, horizon - top, Ui.c(s.skyTop), Ui.c(s.skyBottom));
		if (s.has(STARS)) {
			stars(g, ix, top, iw, horizon, px);
			moon(g, ix, top, iw, horizon);
		}
		if (s.has(SUN)) {
			sun(g, ix, top, iw, horizon);
		}
		if (s.has(CLOUDS)) {
			clouds(g, ix, top, iw, horizon, "snow".equals(s.id) ? 0xD8EEF2F6 : 0xE6FFFFFF);
		}
		TextureAtlasSprite ground = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS)
			.getSprite(Identifier.withDefaultNamespace("block/" + s.ground));
		TextureSetup atlas = setup(ground.atlasLocation());
		float phase = walked % 1f;
		plane(g, atlas, ground, s.tint, s.far, s.fog, ix, iw, cx, horizon, bottom, f, eye, phase, 1);
		if (s.has(CEILING)) {
			plane(g, atlas, ground, multiply(s.tint, 0xFF8A8A8A), s.far, s.fog, ix, iw, cx, horizon, top, f, 2.6f - lift, phase, -1);
		}
		aimed = zombie(g, s, cx, cy, horizon, f, eye, now);
		// A soft vignette, as on a real screen.
		Render2D.verticalGradient(g, ix, bottom - ih * 0.18f, iw, ih * 0.18f, 0x00000000, Ui.c(0x30000000));

		float targetSpread = module.dynamicSpread() ? CrosshairModule.spread(motion == 2, walking, airborne) : 0;
		float sp = spread.target(targetSpread).get();
		float on = onTarget.target(module.highlightsTargets() && aimed ? 1 : 0).get();
		float strength = strength(now);
		float ca = cooldownAlpha.target(module.showsCooldown() && strength < 1 ? 1 : 0).get();
		float saved = Render2D.globalAlpha;
		Render2D.globalAlpha = saved * Ui.alpha;
		module.paint(g, cx, cy, unit, sp, on, strength, ca);
		Render2D.globalAlpha = saved;

		g.pose().popMatrix();
		g.disableScissor();
		// The bezel hides the square corners of the picture.
		Render2D.roundOutline(g, x, y, w, h, 8, 2.5f, Ui.c(BEZEL));
		Render2D.roundOutline(g, x, y, w, h, 8, 0.7f, Ui.c(Theme.BORDER_STRONG));

		String zoomText = ZOOMS[zoom] + "x";
		pill(g, mx, my, x + w - 7, y + 7, zoomText, true, () -> zoom = (zoom + 1) % ZOOMS.length, I18n.get("aurora.preview.zoom"));
		if (compact) {
			float bx = x + 7;
			float by = y + h - 7 - 13;
			bx += pill(g, mx, my, bx, by, I18n.get("aurora.preview.scene." + s.id), false,
				() -> scene = (scene + 1) % SCENES.length, I18n.get("aurora.preview.background")) + 4;
			pill(g, mx, my, bx, by, I18n.get("aurora.preview.motion." + MOTIONS[motion]), false,
				() -> motion = (motion + 1) % MOTIONS.length, I18n.get("aurora.preview.motion"));
		}
	}

	private static TextureSetup setup(Identifier texture) {
		return TextureSetup.singleTexture(Minecraft.getInstance().getTextureManager().getTexture(texture).getTextureView(),
			RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
	}

	/**
	 * The floor ({@code dir} 1) or a ceiling ({@code dir} -1) in perspective, block by block from
	 * far to near, sinking into the fog with distance. {@code height} is how far it is from the eye.
	 */
	private static void plane(GuiGraphicsExtractor g, TextureSetup atlas, TextureAtlasSprite sprite, int tint, int far, int fog,
							  float x, float w, float cx, float horizon, float edge, float f, float height, float phase, int dir) {
		float span = Math.abs(edge - horizon);
		float reach = f * height;
		// Beyond the last block row: a haze from the fog into the colour of distant ground.
		float hazeEnd = horizon + dir * Math.min(span, reach / 15f);
		int haze = ColorUtil.lerp(far, fog, 0.85f);
		if (dir > 0) {
			Render2D.verticalGradient(g, x, horizon, w, hazeEnd - horizon, Ui.c(fog), Ui.c(haze));
		} else {
			Render2D.verticalGradient(g, x, hazeEnd, w, horizon - hazeEnd, Ui.c(haze), Ui.c(fog));
		}
		for (int k = 16; k >= 1; k--) {
			float near = Math.max(k - phase, 0.35f);
			float farD = k + 1 - phase;
			float y0 = horizon + dir * reach / farD;
			float y1 = horizon + dir * reach / near;
			if (Math.abs(y0 - horizon) >= span) {
				continue;
			}
			float a = Math.min(y0, y1);
			float b = Math.max(y0, y1);
			float depth = (near + farD) / 2f;
			float tile = f / depth;
			// The viewer stands a little off a block seam, so no line runs under the crosshair.
			float shift = 0.3f;
			int j0 = (int) Math.floor((x - cx) / tile + shift) - 1;
			int j1 = (int) Math.ceil((x + w - cx) / tile + shift) + 1;
			for (int j = j0; j < j1; j++) {
				float l = cx + (j - shift) * tile;
				Render2D.texture(g, atlas, l, a, l + tile, b, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), Ui.c(tint));
			}
			float mist = ColorUtil.clamp01((depth - 3f) / 14f) * 0.9f;
			if (mist > 0.01f) {
				Render2D.rect(g, x, a, w, b - a, Ui.c(ColorUtil.fade(fog, mist)));
			}
		}
	}

	/** A zombie shuffling left and right; returns whether the crosshair is on it. */
	private boolean zombie(GuiGraphicsExtractor g, Scene s, float cx, float cy, float horizon, float f, float eye, long now) {
		float since = hurtAt == Long.MIN_VALUE ? Float.MAX_VALUE : now - hurtAt;
		// Knocked back a little, then it walks up again.
		float knock = since > 700 ? 0 : (float) Math.sin(since / 700f * Math.PI) * 0.9f;
		float hurt = ColorUtil.clamp01(1 - since / 400f);
		float depth = 5.2f + knock;
		float side = 1.5f * (float) Math.sin(clock * Math.PI * 2 / 9.0);
		float tx = f * 1.95f / depth / 32f;
		float zx = cx + f * side / depth;
		float feet = horizon + f * eye / depth;
		float top = feet - 32 * tx;
		int tint = Ui.c(multiply(s.light, ColorUtil.lerp(0xFFFFFFFF, 0xFFFF5A5A, hurt)));
		TextureSetup skin = setup(ZOMBIE);
		Render2D.roundRect(g, zx - 6 * tx, feet - 1.2f * tx, 12 * tx, 2.4f * tx, 1.2f * tx, Ui.c(0x45000000));
		part(g, skin, 8, 8, 8, 8, zx - 4 * tx, top, tx, tint);
		part(g, skin, 20, 20, 8, 12, zx - 4 * tx, top + 8 * tx, tx, tint);
		part(g, skin, 4, 20, 4, 12, zx - 4 * tx, top + 20 * tx, tx, tint);
		part(g, skin, 4, 20, 4, 12, zx, top + 20 * tx, tx, tint);
		// Arms held out towards us: we see the ends of them.
		part(g, skin, 48, 16, 4, 4, zx - 8 * tx, top + 8 * tx, tx, tint);
		part(g, skin, 48, 16, 4, 4, zx + 4 * tx, top + 8 * tx, tx, tint);
		return Ui.inside(cx, cy, zx - 4 * tx, top, 8 * tx, 32 * tx) || Ui.inside(cx, cy, zx - 8 * tx, top + 8 * tx, 16 * tx, 4 * tx);
	}

	private static void part(GuiGraphicsExtractor g, TextureSetup skin, int u, int v, int w, int h, float x, float y, float texel, int color) {
		Render2D.texture(g, skin, x, y, x + w * texel, y + h * texel, u / 64f, v / 64f, (u + w) / 64f, (v + h) / 64f, color);
	}

	private static void sun(GuiGraphicsExtractor g, float x, float top, float w, float horizon) {
		float size = Math.max(6, w * 0.09f);
		float sx = x + w * 0.78f - size / 2f;
		float sy = top + (horizon - top) * 0.3f - size / 2f;
		Render2D.glow(g, sx, sy, size, size, 1, size * 1.2f, Ui.c(0x46FFF0B8));
		Render2D.rect(g, sx, sy, size, size, Ui.c(0xFFFFF3C9));
		Render2D.rect(g, sx + size * 0.2f, sy + size * 0.2f, size * 0.6f, size * 0.6f, Ui.c(0xFFFFFDF2));
	}

	private static void moon(GuiGraphicsExtractor g, float x, float top, float w, float horizon) {
		float size = Math.max(5, w * 0.06f);
		float sx = x + w * 0.24f - size / 2f;
		float sy = top + (horizon - top) * 0.28f - size / 2f;
		Render2D.glow(g, sx, sy, size, size, 1, size, Ui.c(0x28C8D6FF));
		Render2D.rect(g, sx, sy, size, size, Ui.c(0xFFDCE3F0));
		Render2D.rect(g, sx + size * 0.55f, sy + size * 0.2f, size * 0.25f, size * 0.25f, Ui.c(0xFFB9C2D3));
		Render2D.rect(g, sx + size * 0.2f, sy + size * 0.6f, size * 0.2f, size * 0.2f, Ui.c(0xFFB9C2D3));
	}

	private void stars(GuiGraphicsExtractor g, float x, float top, float w, float horizon, float px) {
		Render2D.Batch batch = Render2D.batch();
		float height = horizon - top;
		for (int i = 0; i < 46; i++) {
			float sx = x + hash(i * 3 + 1) * w;
			float sy = top + hash(i * 7 + 2) * height * 0.92f;
			float twinkle = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(clock * (1.2f + hash(i) * 2.4f) + i));
			float size = px * (hash(i * 11 + 5) > 0.8f ? 3 : 2);
			int c = Ui.c(ColorUtil.fade(0xFFFFFFFF, twinkle));
			batch.rect(sx, sy, size, size, c, c, c, c);
		}
		batch.submit(g);
	}

	private void clouds(GuiGraphicsExtractor g, float x, float top, float w, float horizon, int color) {
		float height = horizon - top;
		for (int i = 0; i < 4; i++) {
			float cw = w * (0.2f + 0.07f * (i % 3));
			float span = w + cw * 2;
			float travel = hash(i * 5 + 3) * span + clock * w * 0.01f * (1 + i * 0.35f);
			float cx = x - cw + travel % span;
			float cy = top + height * (0.1f + 0.16f * i);
			float ch = Math.max(2.5f, height * 0.06f);
			Render2D.rect(g, cx, cy, cw, ch, Ui.c(color));
			Render2D.rect(g, cx + cw * 0.18f, cy - ch * 0.7f, cw * 0.45f, ch * 0.7f, Ui.c(color));
		}
	}

	private static float hash(int n) {
		int h = n * 0x27D4EB2D;
		h ^= h >>> 15;
		h *= 0x85EBCA6B;
		h ^= h >>> 13;
		return (h & 0xFFFFFF) / (float) 0x1000000;
	}

	private static int multiply(int a, int b) {
		return ColorUtil.argb(ColorUtil.alpha(a) * ColorUtil.alpha(b) / 255, ColorUtil.red(a) * ColorUtil.red(b) / 255,
			ColorUtil.green(a) * ColorUtil.green(b) / 255, ColorUtil.blue(a) * ColorUtil.blue(b) / 255);
	}

	// ======================================================================= controls

	private static void label(GuiGraphicsExtractor g, String key, float x, float y) {
		FontRenderer.SEMIBOLD.drawMiddle(g, I18n.get(key).toUpperCase(Locale.ROOT), x + 2, y + 3, 6.5f, Ui.c(Theme.TEXT_MUTED));
	}

	private void sceneChips(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w) {
		int n = SCENES.length;
		float gap = 4;
		float cw = Math.min(26, (w - gap * (n - 1)) / n);
		float ch = 14;
		for (int i = 0; i < n; i++) {
			Scene s = SCENES[i];
			float sx = x + i * (cw + gap);
			boolean hov = Ui.inside(mx, my, sx, y, cw, ch);
			Render2D.roundRect(g, sx, y, cw, ch / 2f, 3, 3, 0, 0, Render2D.Paint.vertical(y, ch / 2f, Ui.c(s.skyTop), Ui.c(s.skyBottom)));
			Render2D.roundRect(g, sx, y + ch / 2f, cw, ch / 2f, 0, 0, 3, 3, Render2D.Paint.solid(Ui.c(s.tint == 0xFFFFFFFF ? s.far : multiply(s.tint, 0xFFE0E0E0))));
			if (i == scene) {
				Render2D.roundOutline(g, sx - 2, y - 2, cw + 4, ch + 4, 5, 1.2f, Ui.accentPaint(sx, cw));
			} else {
				Render2D.roundOutline(g, sx, y, cw, ch, 3, 0.6f, Ui.c(hov ? 0x66FFFFFF : Theme.BORDER_STRONG));
			}
			int index = i;
			hits.add(new Hit(sx, y, cw, ch, () -> scene = index, I18n.get("aurora.preview.scene." + s.id)));
		}
	}

	private void motionSwitch(GuiGraphicsExtractor g, int mx, int my, float x, float y, float w, float h) {
		Render2D.roundRect(g, x, y, w, h, h / 2f, Ui.c(Theme.INPUT));
		Render2D.roundOutline(g, x, y, w, h, h / 2f, 0.6f, Ui.c(Theme.BORDER));
		float seg = (w - 4) / MOTIONS.length;
		float ix = x + 2 + seg * motionIndicator.target(motion).get();
		Render2D.roundRect(g, ix, y + 2, seg, h - 4, (h - 4) / 2f, Ui.accentPaint(ix, seg));
		for (int i = 0; i < MOTIONS.length; i++) {
			float sx = x + 2 + seg * i;
			boolean hov = Ui.inside(mx, my, sx, y, seg, h);
			String text = FontRenderer.MEDIUM.ellipsize(I18n.get("aurora.preview.motion." + MOTIONS[i]), 7, seg - 4);
			FontRenderer.MEDIUM.drawCentered(g, text, sx + seg / 2f, y + h / 2f, 7,
				Ui.c(i == motion ? Theme.TEXT : hov ? Theme.TEXT_DIM : Theme.TEXT_MUTED));
			int index = i;
			hits.add(new Hit(sx, y, seg, h, () -> motion = index, null));
		}
	}

	/** A small button over the scene; returns its width. */
	private float pill(GuiGraphicsExtractor g, int mx, int my, float x, float y, String text, boolean fromRight, Runnable action, String tooltip) {
		float w = FontRenderer.SEMIBOLD.width(text, 7) + 10;
		float h = 13;
		float left = fromRight ? x - w : x;
		boolean hov = Ui.inside(mx, my, left, y, w, h);
		Render2D.roundRect(g, left, y, w, h, h / 2f, Ui.c(hov ? 0xD920242E : 0xB30D0F14));
		Render2D.roundOutline(g, left, y, w, h, h / 2f, 0.6f, Ui.c(hov ? 0x40FFFFFF : Theme.BORDER_STRONG));
		FontRenderer.SEMIBOLD.drawCentered(g, text, left + w / 2f, y + h / 2f, 7, Ui.c(Theme.TEXT));
		hits.add(new Hit(left, y, w, h, action, tooltip));
		return w;
	}
}
