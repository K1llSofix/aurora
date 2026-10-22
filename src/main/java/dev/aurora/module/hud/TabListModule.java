package dev.aurora.module.hud;

import dev.aurora.gui.Theme;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.gui.screen.PauseMenu;
import dev.aurora.gui.widget.Ui;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.Easing;
import dev.aurora.util.Motion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The player list (Tab) in Aurora's style: a glass panel with player heads, ping in
 * milliseconds coloured by quality, scores, the server's header and footer and a player count.
 */
public final class TabListModule extends Module {
	/** Private parts of the vanilla overlay, exposed by a mixin. */
	public interface Source {
		List<PlayerInfo> aurora$players();

		@Nullable Component aurora$header();

		@Nullable Component aurora$footer();
	}

	private static final Identifier[] PING = {
		Identifier.withDefaultNamespace("icon/ping_5"),
		Identifier.withDefaultNamespace("icon/ping_4"),
		Identifier.withDefaultNamespace("icon/ping_3"),
		Identifier.withDefaultNamespace("icon/ping_2"),
		Identifier.withDefaultNamespace("icon/ping_1")
	};
	private static final Identifier PING_UNKNOWN = Identifier.withDefaultNamespace("icon/ping_unknown");
	private static final float ROW = 12;
	private static final float GAP = 6;
	private static final float PAD = 8;

	private final BooleanSetting pingNumbers = add(new BooleanSetting("ping_numbers", true));
	private final BooleanSetting heads = add(new BooleanSetting("heads", true));
	private final BooleanSetting count = add(new BooleanSetting("count", true));

	private long lastFrame;
	private long openedAt;

	public TabListModule() {
		super("tab_list", Category.HUD, Items.PLAYER_HEAD, true);
	}

	public void render(PlayerTabOverlay overlay, Source source, GuiGraphicsExtractor g, int screenWidth, Scoreboard scoreboard,
					   @Nullable Objective objective) {
		long now = System.currentTimeMillis();
		if (now - lastFrame > 150) {
			openedAt = now;
		}
		lastFrame = now;
		float appear = Easing.OUT_CUBIC.clamped(Motion.progress(openedAt, 220));
		Font font = mc.font;
		List<PlayerInfo> infos = source.aurora$players();
		boolean showHeads = heads.on() || (mc.getConnection() != null && mc.getConnection().onlineMode());
		boolean hearts = objective != null && objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS;
		NumberFormat format = objective != null ? objective.numberFormatOrDefault(StyledFormat.PLAYER_LIST_DEFAULT) : null;

		int n = infos.size();
		List<Component> names = new ArrayList<>(n);
		List<@Nullable Component> scores = new ArrayList<>(n);
		int nameW = 0;
		int scoreW = 0;
		for (PlayerInfo info : infos) {
			Component name = overlay.getNameForDisplay(info);
			names.add(name);
			nameW = Math.max(nameW, font.width(name));
			Component score = null;
			if (objective != null && info.getGameMode() != GameType.SPECTATOR) {
				ReadOnlyScoreInfo scoreInfo = scoreboard.getPlayerScoreInfo(ScoreHolder.fromGameProfile(info.getProfile()), objective);
				if (scoreInfo != null) {
					score = hearts
						? Component.literal("❤ " + formatHearts(scoreInfo.value())).withColor(0xFF5555)
						: scoreInfo.formatValue(format);
				}
			}
			scores.add(score);
			if (score != null) {
				scoreW = Math.max(scoreW, font.width(score));
			}
		}
		int rows = n;
		int cols = 1;
		while (rows > 20) {
			cols++;
			rows = (n + cols - 1) / cols;
		}
		float pingW = pingNumbers.on() ? 30 : 14;
		float slotW = 4 + (showHeads ? 11 : 0) + nameW + (scoreW > 0 ? 8 + scoreW : 0) + 6 + pingW;
		float maxGrid = screenWidth - 50;
		if (cols * slotW + (cols - 1) * GAP > maxGrid) {
			slotW = (maxGrid - (cols - 1) * GAP) / cols;
		}
		float gridW = cols * slotW + (cols - 1) * GAP;

		List<FormattedCharSequence> header = source.aurora$header() != null ? font.split(source.aurora$header(), screenWidth - 60) : List.of();
		List<FormattedCharSequence> footer = source.aurora$footer() != null ? font.split(source.aurora$footer(), screenWidth - 60) : List.of();
		float contentW = gridW;
		for (FormattedCharSequence line : header) {
			contentW = Math.max(contentW, font.width(line));
		}
		for (FormattedCharSequence line : footer) {
			contentW = Math.max(contentW, font.width(line));
		}
		String countText = count.on() ? I18n.get("aurora.tab.players", n) : null;
		float headerH = header.isEmpty() ? 0 : header.size() * 10 + 5;
		float countH = countText != null ? 12 : 0;
		float gridH = rows * ROW;
		float footerH = footer.isEmpty() ? 0 : footer.size() * 10 + 5;
		float panelW = contentW + PAD * 2;
		float panelH = PAD + headerH + countH + gridH + footerH + PAD - 2;
		float x0 = screenWidth / 2f - panelW / 2f;
		float y0 = 6 - (1 - appear) * 6;
		float cx = screenWidth / 2f;
		int text = ColorUtil.fade(0xFFFFFFFF, appear);

		Ui.alpha = appear;
		Render2D.shadow(g, x0, y0, panelW, panelH, 10, 14, Ui.c(0x66000000));
		Render2D.roundRect(g, x0, y0, panelW, panelH, 10, Render2D.Paint.vertical(y0, panelH, Ui.c(0xE6151821), Ui.c(0xE60D0F14)));
		Render2D.roundOutline(g, x0, y0, panelW, panelH, 10, 0.7f, Ui.c(0x1FFFFFFF));
		float y = y0 + PAD;
		for (FormattedCharSequence line : header) {
			g.text(font, line, Math.round(cx - font.width(line) / 2f), Math.round(y), text, true);
			y += 10;
		}
		if (!header.isEmpty()) {
			y += 1;
			divider(g, x0, panelW, y);
			y += 4;
		}
		if (countText != null) {
			FontRenderer.MEDIUM.drawCentered(g, countText, cx, y + 5, 7, Ui.c(Theme.TEXT_MUTED));
			y += countH;
		}
		float gx = cx - gridW / 2f;
		for (int i = 0; i < n; i++) {
			PlayerInfo info = infos.get(i);
			int col = i / rows;
			int row = i % rows;
			float sx = gx + col * (slotW + GAP);
			float sy = y + row * ROW;
			if (row % 2 == 0) {
				Render2D.roundRect(g, sx, sy, slotW, ROW - 1, 3, Ui.c(0x0BFFFFFF));
			}
			float tx = sx + 4;
			if (showHeads) {
				PlayerFaceExtractor.extractRenderState(g, info.getSkin().body().texturePath(), Math.round(tx), Math.round(sy + 1.5f), 8,
					info.showHat(), false, text);
				tx += 11;
			}
			boolean spectator = info.getGameMode() == GameType.SPECTATOR;
			g.text(font, names.get(i), Math.round(tx), Math.round(sy + 2), spectator ? ColorUtil.fade(0x90FFFFFF, appear) : text, true);
			float right = sx + slotW - 4;
			int latency = info.getLatency();
			if (pingNumbers.on()) {
				String ping = latency < 0 ? "—" : latency + I18n.get("aurora.unit.ms");
				FontRenderer.SEMIBOLD.drawRight(g, ping, right, sy + ROW / 2f - 0.5f, 6.5f, Ui.c(PauseMenu.pingColor(latency)));
			} else {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, pingSprite(latency), Math.round(right - 10), Math.round(sy + 1.5f), 10, 8);
			}
			right -= pingW;
			Component score = scores.get(i);
			if (score != null) {
				g.text(font, score, Math.round(right - font.width(score)), Math.round(sy + 2), text, true);
			}
		}
		y += gridH;
		if (!footer.isEmpty()) {
			y += 2;
			divider(g, x0, panelW, y);
			y += 3;
			for (FormattedCharSequence line : footer) {
				g.text(font, line, Math.round(cx - font.width(line) / 2f), Math.round(y), text, true);
				y += 10;
			}
		}
		Ui.alpha = 1f;
	}

	private static void divider(GuiGraphicsExtractor g, float x0, float panelW, float y) {
		Render2D.roundRect(g, x0 + PAD, y, panelW - PAD * 2, 1, 0.5f, Render2D.Paint.horizontal(x0 + PAD, panelW - PAD * 2,
			Ui.c(ColorUtil.fade(Theme.accent(), 0.7f)), Ui.c(ColorUtil.fade(Theme.accent2(), 0.7f))));
	}

	private static Identifier pingSprite(int latency) {
		if (latency < 0) {
			return PING_UNKNOWN;
		}
		if (latency < 150) {
			return PING[0];
		}
		if (latency < 300) {
			return PING[1];
		}
		if (latency < 600) {
			return PING[2];
		}
		return latency < 1000 ? PING[3] : PING[4];
	}

	private static String formatHearts(int health) {
		return health % 2 == 0 ? String.valueOf(health / 2) : (health / 2) + ".5";
	}
}
