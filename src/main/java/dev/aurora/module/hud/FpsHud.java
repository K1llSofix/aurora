package dev.aurora.module.hud;

import dev.aurora.gui.HudDraw;
import dev.aurora.gui.render.Render2D;
import dev.aurora.module.TextHudModule;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Items;

import java.util.Arrays;

/**
 * Frames per second, updated four times a second from the real frame times rather than once a
 * second like the game's own counter. Optionally the "1% low" (how smooth it feels: the average
 * of the slowest frames) and a frame time graph that shows every stutter.
 */
public final class FpsHud extends TextHudModule {
	/** Frames kept for the 1% low: several seconds at any frame rate. */
	private static final int WINDOW = 1200;
	private static final int GRAPH_FRAMES = 90;
	private static final float GRAPH_H = 12;
	/** A frame this long (30 FPS) fills the graph. */
	private static final float GRAPH_MS = 33.3f;

	private final BooleanSetting low = add(new BooleanSetting("low", false));
	private final BooleanSetting graph = add(new BooleanSetting("graph", false));
	private final BooleanSetting colorize = add(new BooleanSetting("colorize", false));

	private final float[] frames = new float[WINDOW];
	private final float[] sorted = new float[WINDOW];
	private int head;
	private int count;
	private long lastFrame;
	private long lastUpdate;
	private int fps;
	private int lowFps;

	public FpsHud() {
		super("fps", Items.AMETHYST_SHARD, true, 0, 0, 4, 4);
	}

	@Override
	protected String label() {
		return "FPS";
	}

	@Override
	protected String value(boolean editing) {
		sample();
		if (count == 0) {
			fps = mc.getFps();
		}
		return low.on() ? fps + " · 1% " + lowFps : Integer.toString(fps);
	}

	/** Called once per drawn frame (the HUD lays itself out every frame). */
	private void sample() {
		long now = System.nanoTime();
		if (lastFrame != 0) {
			float ms = (now - lastFrame) / 1e6f;
			// A pause (menus, loading) is not a frame.
			if (ms > 0 && ms < 500) {
				frames[head] = ms;
				head = (head + 1) % WINDOW;
				count = Math.min(count + 1, WINDOW);
			}
		}
		lastFrame = now;
		long nowMs = System.currentTimeMillis();
		if (nowMs - lastUpdate >= 250 && count > 0) {
			lastUpdate = nowMs;
			update();
		}
	}

	private void update() {
		// Average over the last second.
		float total = 0;
		int n = 0;
		while (n < count && total < 1000) {
			total += frames[(head - 1 - n + WINDOW) % WINDOW];
			n++;
		}
		fps = Math.round(n * 1000f / Math.max(total, 0.001f));
		// The slowest 1% of the frames kept, at least one.
		for (int i = 0; i < count; i++) {
			sorted[i] = frames[(head - 1 - i + WINDOW) % WINDOW];
		}
		Arrays.sort(sorted, 0, count);
		int slow = Math.max(1, count / 100);
		float sum = 0;
		for (int i = count - slow; i < count; i++) {
			sum += sorted[i];
		}
		lowFps = Math.round(1000f / Math.max(sum / slow, 0.001f));
	}

	@Override
	protected int valueColor() {
		if (!colorize.on()) {
			return HudDraw.textColor();
		}
		return fps >= 120 ? 0xFF2DD4BF : fps >= 60 ? 0xFF4ADE80 : fps >= 30 ? 0xFFFBBF24 : 0xFFF87171;
	}

	@Override
	public void layout(boolean editing) {
		super.layout(editing);
		if (graph.on()) {
			width = Math.max(width, 72);
			height += GRAPH_H + 3;
		}
	}

	@Override
	public void render(GuiGraphicsExtractor g, float partialTick, boolean editing) {
		super.render(g, partialTick, editing);
		if (!graph.on()) {
			return;
		}
		float x = HudDraw.contentInset();
		float w = width - x - Math.max(HudDraw.padX(), 2);
		float top = height - GRAPH_H - 3;
		float bar = w / GRAPH_FRAMES;
		int n = Math.min(count, GRAPH_FRAMES);
		Render2D.Batch batch = Render2D.batch();
		for (int i = 0; i < GRAPH_FRAMES; i++) {
			float ms;
			if (i >= GRAPH_FRAMES - n) {
				ms = frames[(head - (GRAPH_FRAMES - i) + WINDOW) % WINDOW];
			} else if (editing && count == 0) {
				ms = 7 + 2 * (float) Math.sin(i * 0.7) + (i % 23 == 0 ? 12 : 0);
			} else {
				continue;
			}
			float h = Math.max(0.6f, Math.min(1f, ms / GRAPH_MS) * GRAPH_H);
			int c = ColorUtil.withAlpha(ms <= 1000f / 60 ? 0x4ADE80 : ms <= 1000f / 30 ? 0xFBBF24 : 0xF87171, 0xCC);
			batch.rect(x + i * bar, top + GRAPH_H - h, Math.max(0.5f, bar - 0.25f), h, c, c, c, c);
		}
		batch.submit(g);
		// The 60 FPS line.
		float line = top + GRAPH_H - (1000f / 60 / GRAPH_MS) * GRAPH_H;
		Render2D.rect(g, x, line, w, 0.5f, 0x40FFFFFF);
	}
}
