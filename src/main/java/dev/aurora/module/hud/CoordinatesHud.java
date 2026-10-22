package dev.aurora.module.hud;

import dev.aurora.gui.HudDraw;
import dev.aurora.module.HudModule;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.ModeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CoordinatesHud extends HudModule {
	private final ModeSetting layout = add(new ModeSetting("layout", "vertical", "vertical", "line"));
	private final BooleanSetting direction = add(new BooleanSetting("direction", true));
	private final BooleanSetting biome = add(new BooleanSetting("biome", true));
	private final BooleanSetting decimals = add(new BooleanSetting("decimals", false));
	private final BooleanSetting nether = add(new BooleanSetting("nether", false));

	private record Row(String label, String value) {
	}

	private final List<Row> rows = new ArrayList<>();

	public CoordinatesHud() {
		super("coordinates", Items.COMPASS, true, 0, 0, 4, 40);
	}

	private String num(double v) {
		return decimals.on() ? String.format(Locale.ROOT, "%.1f", v) : Long.toString((long) Math.floor(v));
	}

	private void collect(boolean editing) {
		rows.clear();
		if (mc.player == null) {
			rows.add(new Row("X", "128"));
			rows.add(new Row("Y", "64"));
			rows.add(new Row("Z", "-256"));
			if (direction.on()) rows.add(new Row("F", I18n.get("aurora.direction.north") + " (-Z)"));
			if (biome.on()) rows.add(new Row("B", "Plains"));
			return;
		}
		double x = mc.player.getX();
		double y = mc.player.getY();
		double z = mc.player.getZ();
		rows.add(new Row("X", num(x)));
		rows.add(new Row("Y", num(y)));
		rows.add(new Row("Z", num(z)));
		if (nether.on() && mc.level != null) {
			boolean inNether = mc.level.dimension() == net.minecraft.world.level.Level.NETHER;
			double f = inNether ? 8 : 1 / 8.0;
			rows.add(new Row(inNether ? "OW" : "N", num(x * f) + ", " + num(z * f)));
		}
		if (direction.on()) {
			Direction d = mc.player.getDirection();
			String axis = switch (d) {
				case NORTH -> "-Z";
				case SOUTH -> "+Z";
				case WEST -> "-X";
				case EAST -> "+X";
				default -> "";
			};
			rows.add(new Row("F", I18n.get("aurora.direction." + d.getName()) + " (" + axis + ")"));
		}
		if (biome.on() && mc.level != null) {
			BlockPos pos = mc.player.blockPosition();
			String name = mc.level.getBiome(pos).unwrapKey()
				.map(key -> I18n.get(key.identifier().toLanguageKey("biome")))
				.orElse("?");
			rows.add(new Row("B", name));
		}
	}

	@Override
	public void layout(boolean editing) {
		collect(editing);
		float pad = HudDraw.contentInset();
		if (layout.is("line")) {
			float w = 0;
			for (Row r : rows) {
				w += HudDraw.width(r.label) + 3 + HudDraw.width(r.value) + 7;
			}
			width = w - 7 + pad + HudDraw.padX();
			height = HudDraw.rowHeight();
		} else {
			float lw = 0;
			float vw = 0;
			for (Row r : rows) {
				lw = Math.max(lw, HudDraw.width(r.label));
				vw = Math.max(vw, HudDraw.width(r.value));
			}
			width = pad + lw + 6 + vw + HudDraw.padX();
			height = rows.size() * 11f + 5;
		}
	}

	@Override
	public void render(GuiGraphicsExtractor g, float partialTick, boolean editing) {
		HudDraw.panel(g, 0, 0, width, height);
		float x0 = HudDraw.contentInset();
		if (layout.is("line")) {
			float x = x0;
			for (Row r : rows) {
				x += HudDraw.text(g, r.label, x, height / 2f, HudDraw.labelColor()) + 3;
				x += HudDraw.text(g, r.value, x, height / 2f, HudDraw.textColor()) + 7;
			}
			return;
		}
		float lw = 0;
		for (Row r : rows) {
			lw = Math.max(lw, HudDraw.width(r.label));
		}
		float y = 2.5f + 5.5f;
		for (Row r : rows) {
			HudDraw.text(g, r.label, x0, y, HudDraw.labelColor());
			HudDraw.text(g, r.value, x0 + lw + 6, y, HudDraw.textColor());
			y += 11f;
		}
	}
}
