package dev.aurora.module.visual;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.aurora.Aurora;
import dev.aurora.gui.Theme;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.ModeSetting;
import dev.aurora.setting.NumberSetting;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Northern lights in the night sky: slow curtains of green light fading to violet over the
 * northern horizon, with rays that drift along them. Drawn with the sky itself, behind the stars'
 * neighbours and every block, and only where the stars are out: not by day, not in the rain.
 */
public final class AuroraSkyModule extends Module {
	private static final float RADIUS = 100f;
	private static final int SEGMENTS = 80;
	/** Rows of each curtain from its soft lower edge to its faded top. */
	private static final int ROWS = 4;
	private static final int QUADS = Curtain.ALL.length * SEGMENTS * (ROWS - 1);
	private static final int VERTICES = QUADS * 4;

	/** Additive, like light: the curtains brighten the sky and the stars shine through. */
	private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withBindGroupLayout(BindGroupLayouts.PROJECTION)
		.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
		.withLocation(Aurora.id("pipeline/aurora_sky"))
		.withVertexShader("core/position_color")
		.withFragmentShader("core/position_color")
		.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
		.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.withCull(false)
		.build();

	/** Where one curtain hangs: angles in degrees, azimuth 0 = north, positive = towards east. */
	private record Curtain(float azimuth, float span, float base, float height, float phase) {
		static final Curtain[] ALL = {
			new Curtain(-28, 105, 17, 30, 0f),
			new Curtain(14, 85, 25, 24, 2.1f),
			new Curtain(46, 70, 20, 20, 4.3f),
		};
	}

	private final NumberSetting brightness = add(new NumberSetting("brightness", 70, 10, 100, 5).suffix("%"));
	private final NumberSetting speed = add(new NumberSetting("speed", 100, 25, 300, 25).suffix("%"));
	private final ModeSetting colors = add(new ModeSetting("colors", "natural", "natural", "theme"));

	private static @Nullable AuroraSkyModule instance;
	private @Nullable GpuBuffer buffer;
	private RenderSystem.@Nullable AutoStorageIndexBuffer indices;
	private float alpha;
	private double time;
	private long lastNanos;

	public AuroraSkyModule() {
		super("aurora_sky", Category.VISUAL, Items.ENDER_EYE, true);
		instance = this;
	}

	public static @Nullable AuroraSkyModule active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	/** Before the sky pass starts: decides the strength and uploads this frame's curtains. */
	public void prepare(SkyRenderState state) {
		alpha = 0;
		if (state.skybox != DimensionType.Skybox.OVERWORLD) {
			return;
		}
		float strength = state.starBrightness * state.rainBrightness * brightness.floatValue() / 100f;
		long now = System.nanoTime();
		// Time runs at the chosen speed and does not jump when the speed changes.
		if (lastNanos != 0) {
			time += Math.min(0.25, (now - lastNanos) / 1e9) * speed.floatValue() / 100f;
		}
		lastNanos = now;
		if (strength < 0.01f) {
			return;
		}
		if (buffer == null) {
			buffer = RenderSystem.getDevice().createBuffer(() -> "Aurora sky vertices",
				GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, (long) VERTICES * DefaultVertexFormat.POSITION_COLOR.getVertexSize());
			indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		}
		indices.getBuffer(QUADS * 6);
		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(VERTICES * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
			BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
			for (Curtain curtain : Curtain.ALL) {
				build(builder, curtain, (float) time);
			}
			try (MeshData mesh = builder.buildOrThrow()) {
				RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), mesh.vertexBuffer());
			}
		}
		alpha = strength;
	}

	/** Inside the sky pass, right after the stars. */
	public void draw(RenderPass pass) {
		if (alpha <= 0 || buffer == null || indices == null) {
			return;
		}
		pass.pushDebugGroup(() -> "Aurora sky");
		pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
		RenderSystem.bindDefaultUniforms(pass);
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
			.writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1f, 1f, 1f, alpha));
		pass.setUniform("DynamicTransforms", transforms);
		pass.setVertexBuffer(0, buffer.slice());
		pass.setIndexBuffer(indices.getBuffer(QUADS * 6), indices.type());
		pass.drawIndexed(QUADS * 6, 1, 0, 0, 0);
		pass.popDebugGroup();
	}

	private void build(BufferBuilder b, Curtain c, float t) {
		boolean theme = colors.is("theme");
		int glow = theme ? Theme.accent() : 0xFF46FFA0;
		int middle = theme ? ColorUtil.lerp(Theme.accent(), Theme.accent2(), 0.5f) : 0xFF3BD5C8;
		int top = theme ? Theme.accent2() : 0xFF8A63FF;
		// Each row: height along the curtain (0 = lower edge, 1 = top), its colour and opacity.
		float[] rowAt = {0f, 0.12f, 0.45f, 1f};
		int[] rowColor = {glow, glow, middle, top};
		float[] rowAlpha = {0f, 1f, 0.42f, 0f};

		float[][] xs = new float[ROWS][SEGMENTS + 1];
		float[][] ys = new float[ROWS][SEGMENTS + 1];
		float[][] zs = new float[ROWS][SEGMENTS + 1];
		int[][] cs = new int[ROWS][SEGMENTS + 1];
		for (int i = 0; i <= SEGMENTS; i++) {
			float s = i / (float) SEGMENTS;
			double tau = Math.PI * 2;
			// The curtain sways, its lower edge rises and falls. The sway stays gentle enough that
			// the curtain never folds back over itself, which would show as a bright seam.
			float azimuth = c.azimuth + c.span * (s - 0.5f)
				+ 4f * (float) Math.sin(tau * 1.1 * s + 0.35 * t + c.phase)
				+ 1.2f * (float) Math.sin(tau * 2.7 * s - 0.6 * t + c.phase * 1.7);
			float base = c.base + 4f * (float) Math.sin(tau * 0.8 * s + 0.25 * t + c.phase);
			float height = c.height * (0.78f + 0.22f * (float) Math.sin(tau * 2.2 * s + 0.45 * t + c.phase));
			// Rays: bright streaks drifting along, and the curtain fading out at both ends.
			float rays = 0.55f + 0.3f * wave(s * 37f + t * 0.9f + c.phase) + 0.15f * wave(s * 91f - t * 1.7f);
			float envelope = (float) Math.pow(Math.sin(Math.PI * s), 0.7);
			float shimmer = 0.85f + 0.15f * (float) Math.sin(t * 1.3 + c.phase);
			float light = rays * envelope * shimmer;
			double az = Math.toRadians(azimuth);
			for (int r = 0; r < ROWS; r++) {
				double el = Math.toRadians(base + height * rowAt[r]);
				float cosEl = (float) Math.cos(el);
				xs[r][i] = RADIUS * cosEl * (float) Math.sin(az);
				ys[r][i] = RADIUS * (float) Math.sin(el);
				zs[r][i] = -RADIUS * cosEl * (float) Math.cos(az);
				cs[r][i] = ColorUtil.withAlpha(rowColor[r], Math.round(230 * rowAlpha[r] * light));
			}
		}
		for (int r = 0; r < ROWS - 1; r++) {
			for (int i = 0; i < SEGMENTS; i++) {
				b.addVertex(xs[r][i], ys[r][i], zs[r][i]).setColor(cs[r][i]);
				b.addVertex(xs[r][i + 1], ys[r][i + 1], zs[r][i + 1]).setColor(cs[r][i + 1]);
				b.addVertex(xs[r + 1][i + 1], ys[r + 1][i + 1], zs[r + 1][i + 1]).setColor(cs[r + 1][i + 1]);
				b.addVertex(xs[r + 1][i], ys[r + 1][i], zs[r + 1][i]).setColor(cs[r + 1][i]);
			}
		}
	}

	/** A soft wave between 0 and 1. */
	private static float wave(float x) {
		float v = 0.5f + 0.5f * (float) Math.sin(x);
		return v * v;
	}
}
