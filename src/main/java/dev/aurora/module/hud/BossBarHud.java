package dev.aurora.module.hud;

import dev.aurora.Aurora;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.Render2D;
import dev.aurora.mixin.BossHealthOverlayAccessor;
import dev.aurora.module.HudModule;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Boss bars as a movable HUD element with smooth rounded bars in the boss's colour. */
public final class BossBarHud extends HudModule {
	private static final float BAR_W = 182;
	private static final float BAR_H = 5;
	private static final float ENTRY = 19;

	private final BooleanSetting glow = add(new BooleanSetting("glow", true));

	private record Bar(Component name, float progress, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
	}

	private final List<Bar> bars = new ArrayList<>();

	public BossBarHud() {
		super("boss_bar", Items.DRAGON_HEAD, true, 0.5f, 0f, 0, 3);
	}

	private Collection<LerpingBossEvent> events() {
		return ((BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).aurora$events().values();
	}

	@Override
	public boolean hasContent() {
		return !events().isEmpty();
	}

	@Override
	public void layout(boolean editing) {
		bars.clear();
		int max = Math.max(1, (int) ((mc.getWindow().getGuiScaledHeight() / 3f) / ENTRY));
		for (LerpingBossEvent event : events()) {
			if (bars.size() >= max) {
				break;
			}
			bars.add(new Bar(event.getName(), event.getProgress(), event.getColor(), event.getOverlay()));
		}
		if (bars.isEmpty() && editing) {
			bars.add(new Bar(Component.translatable("entity.minecraft.ender_dragon"), 0.62f, BossEvent.BossBarColor.PINK,
				BossEvent.BossBarOverlay.PROGRESS));
		}
		width = BAR_W;
		height = Math.max(ENTRY, bars.size() * ENTRY);
	}

	private static int colorOf(BossEvent.BossBarColor color) {
		return switch (color) {
			case PINK -> 0xFFF472B6;
			case BLUE -> 0xFF60A5FA;
			case RED -> 0xFFF87171;
			case GREEN -> 0xFF4ADE80;
			case YELLOW -> 0xFFFACC15;
			case PURPLE -> 0xFFC084FC;
			case WHITE -> 0xFFE5E7EB;
		};
	}

	private static int notches(BossEvent.BossBarOverlay overlay) {
		return switch (overlay) {
			case NOTCHED_6 -> 6;
			case NOTCHED_10 -> 10;
			case NOTCHED_12 -> 12;
			case NOTCHED_20 -> 20;
			default -> 0;
		};
	}

	@Override
	public void render(GuiGraphicsExtractor g, float partialTick, boolean editing) {
		Font font = mc.font;
		boolean shadow = Aurora.client().hudShadow.on();
		float y = 0;
		for (Bar bar : bars) {
			int nameWidth = font.width(bar.name());
			g.text(font, bar.name(), Math.round(BAR_W / 2f - nameWidth / 2f), Math.round(y), -1, shadow);
			float by = y + 10;
			int color = colorOf(bar.color());
			int dark = ColorUtil.lerp(color, 0xFF000000, 0.35f);
			Render2D.roundRect(g, 0, by, BAR_W, BAR_H, BAR_H / 2f, 0xA60B0D12);
			Render2D.roundOutline(g, 0, by, BAR_W, BAR_H, BAR_H / 2f, 0.6f, 0x24FFFFFF);
			float fill = Math.max(0, Math.min(1, bar.progress())) * BAR_W;
			if (fill > 0.5f) {
				if (glow.on()) {
					Render2D.glow(g, 0, by, fill, BAR_H, BAR_H / 2f, 5, ColorUtil.fade(color, 0.35f));
				}
				Render2D.roundRect(g, 0, by, Math.max(fill, BAR_H), BAR_H, BAR_H / 2f,
					Render2D.Paint.horizontal(0, BAR_W, dark, color));
				Render2D.roundRect(g, 1, by + 0.8f, Math.max(fill - 2, 1), 1.2f, 0.6f, ColorUtil.fade(0xFFFFFFFF, 0.35f));
			}
			int n = notches(bar.overlay());
			for (int i = 1; i < n; i++) {
				float nx = BAR_W * i / n;
				Render2D.rect(g, nx - 0.5f, by + 0.5f, 1, BAR_H - 1, 0x99000000);
			}
			y += ENTRY;
		}
		if (bars.isEmpty()) {
			Render2D.rect(g, 0, 0, BAR_W, 1, Theme.BORDER);
		}
	}
}
