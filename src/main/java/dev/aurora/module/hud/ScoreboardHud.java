package dev.aurora.module.hud;

import dev.aurora.Aurora;
import dev.aurora.gui.HudDraw;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.Render2D;
import dev.aurora.module.HudModule;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The scoreboard sidebar as a HUD element: move and scale it in the HUD editor, draw it in the
 * HUD style, and hide the score numbers that many servers use only for ordering.
 */
public final class ScoreboardHud extends HudModule {
	private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparing(PlayerScoreEntry::value)
		.reversed()
		.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
	private static final float LINE = 10;
	private static final float PAD = 5;
	private static final float TITLE = 14;

	private final BooleanSetting numbers = add(new BooleanSetting("numbers", true));
	private final BooleanSetting divider = add(new BooleanSetting("divider", true));

	private record Line(Component name, Component score, int scoreWidth) {
	}

	private Component title = Component.empty();
	private final List<Line> lines = new ArrayList<>();

	public ScoreboardHud() {
		super("scoreboard", Items.OAK_SIGN, true, 1f, 0.5f, -3, 0);
	}

	/** The objective vanilla would show: the team colour slot first, then the sidebar. */
	private @Nullable Objective objective() {
		if (mc.level == null || mc.player == null) {
			return null;
		}
		Scoreboard scoreboard = mc.level.getScoreboard();
		PlayerTeam team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
		if (team != null) {
			Optional<TeamColor> color = team.getColor();
			if (color.isPresent()) {
				Objective teamObjective = scoreboard.getDisplayObjective(color.get().displaySlot());
				if (teamObjective != null) {
					return teamObjective;
				}
			}
		}
		return scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
	}

	@Override
	public boolean hasContent() {
		return objective() != null;
	}

	@Override
	public void layout(boolean editing) {
		lines.clear();
		Font font = mc.font;
		Objective objective = objective();
		if (objective == null) {
			title = Component.literal("Aurora");
			if (editing) {
				lines.add(new Line(Component.literal("Kills"), Component.literal("12").withStyle(s -> s.withColor(0xFF5555)), font.width("12")));
				lines.add(new Line(Component.literal("Deaths"), Component.literal("3").withStyle(s -> s.withColor(0xFF5555)), font.width("3")));
				lines.add(new Line(Component.literal("play.server.net").withStyle(s -> s.withColor(0xFFFF55)), Component.empty(), 0));
			}
		} else {
			title = objective.getDisplayName();
			Scoreboard scoreboard = objective.getScoreboard();
			NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
			scoreboard.listPlayerScores(objective).stream()
				.filter(entry -> !entry.isHidden())
				.sorted(ORDER)
				.limit(15)
				.forEach(entry -> {
					Component name = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName());
					Component score = entry.formatValue(format);
					lines.add(new Line(name, score, font.width(score)));
				});
		}
		int widest = font.width(title);
		int spacer = font.width(": ");
		for (Line line : lines) {
			int scoreWidth = numbers.on() && line.scoreWidth() > 0 ? spacer + line.scoreWidth() : 0;
			widest = Math.max(widest, font.width(line.name()) + scoreWidth);
		}
		width = widest + PAD * 2 + (HudDraw.modern() ? 2 : 0);
		height = TITLE + lines.size() * LINE + PAD - 1;
	}

	@Override
	public void render(GuiGraphicsExtractor g, float partialTick, boolean editing) {
		Font font = mc.font;
		boolean shadow = Aurora.client().hudShadow.on();
		HudDraw.box(g, 0, 0, width, height);
		if (HudDraw.classic()) {
			Render2D.rect(g, 0, 0, width, TITLE, 0x40000000);
		} else if (HudDraw.modern() && divider.on()) {
			Render2D.roundRect(g, PAD, TITLE - 0.5f, width - PAD * 2, 1, 0.5f, Render2D.Paint.horizontal(PAD, width - PAD * 2,
				ColorUtil.fade(Theme.accent(), 0.85f), ColorUtil.fade(Theme.accent2(), 0.85f)));
		}
		g.text(font, title, Math.round(width / 2f - font.width(title) / 2f), Math.round((TITLE - 8) / 2f), -1, shadow);
		float y = TITLE + 2;
		float left = PAD + (HudDraw.modern() ? 1 : 0);
		for (Line line : lines) {
			g.text(font, line.name(), Math.round(left), Math.round(y), -1, shadow);
			if (numbers.on() && line.scoreWidth() > 0) {
				g.text(font, line.score(), Math.round(width - PAD - line.scoreWidth()), Math.round(y), -1, shadow);
			}
			y += LINE;
		}
	}
}
