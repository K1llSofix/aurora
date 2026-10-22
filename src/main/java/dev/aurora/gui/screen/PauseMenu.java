package dev.aurora.gui.screen;

import dev.aurora.Aurora;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.gui.widget.Ui;
import dev.aurora.util.ColorUtil;
import dev.aurora.util.Easing;
import dev.aurora.util.Motion;
import dev.aurora.util.Session;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Aurora's pause menu. Vanilla's buttons (and all their logic: friends, server links, reports)
 * stay; they move into a column on the left like the main menu, with an Aurora header and an
 * "Aurora Mods" button. The right side shows the world or server, the session and the player.
 */
public final class PauseMenu {
	private static final int COLUMN_W = 204;

	/** Where the column ended up, for the decorations. */
	public record Layout(int left, int top, int bottom, long openedAt) {
	}

	private PauseMenu() {
	}

	public static boolean enabled() {
		return Aurora.client() != null && Aurora.client().pauseMenu.on();
	}

	public static Layout layout(Screen screen, List<? extends GuiEventListener> children, @Nullable Button disconnect,
								Consumer<AbstractWidget> add) {
		Minecraft mc = Minecraft.getInstance();
		List<AbstractWidget> widgets = new ArrayList<>();
		for (GuiEventListener child : children) {
			if (child instanceof AbstractWidget widget) {
				if (widget instanceof StringWidget) {
					// The "Game Menu" title: the header draws it instead.
					widget.visible = false;
				} else {
					widgets.add(widget);
				}
			}
		}
		long now = System.currentTimeMillis();
		if (widgets.isEmpty()) {
			return new Layout(0, 0, 0, now);
		}
		Button mods = Button.builder(Component.translatable("aurora.title.mods"), button -> mc.gui.setScreen(new ModMenuScreen(screen)))
			.width(COLUMN_W)
			.build();
		if (disconnect != null) {
			// The mods button takes the disconnect button's place; disconnect moves one row down.
			int at = disconnect.getY();
			for (AbstractWidget widget : widgets) {
				if (widget.getY() >= at) {
					widget.setY(widget.getY() + 24);
				}
			}
			mods.setPosition(disconnect.getX(), at);
		} else {
			int bottom = widgets.stream().mapToInt(w -> w.getY() + w.getHeight()).max().orElse(0);
			mods.setPosition(widgets.get(0).getX(), bottom + 4);
		}
		add.accept(mods);
		widgets.add(mods);

		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (AbstractWidget widget : widgets) {
			minX = Math.min(minX, widget.getX());
			minY = Math.min(minY, widget.getY());
			maxY = Math.max(maxY, widget.getY() + widget.getHeight());
		}
		int height = maxY - minY;
		int left = Math.max(20, Math.round(screen.width * 0.07f));
		int top = Math.max(58, (screen.height - height) / 2 + 14);
		if (top + height > screen.height - 8) {
			top = Math.max(50, screen.height - 8 - height);
		}
		int dx = left - minX;
		int dy = top - minY;
		for (AbstractWidget widget : widgets) {
			widget.setPosition(widget.getX() + dx, widget.getY() + dy);
		}
		return new Layout(left, top, top + height, now);
	}

	// ======================================================================= drawing

	public static void decorate(GuiGraphicsExtractor g, Screen screen, Layout layout, int mx, int my) {
		Minecraft mc = Minecraft.getInstance();
		if (layout.bottom() == 0 || mc.player == null) {
			return;
		}
		float appear = Easing.OUT_CUBIC.clamped(Motion.progress(layout.openedAt(), 420));
		int w = screen.width;
		int h = screen.height;
		int left = layout.left();
		Render2D.horizontalGradient(g, 0, 0, Math.min(w, left + COLUMN_W + 90), h, ColorUtil.fade(0x8C070910, appear), 0x00070910);

		// Header: logo, "Game menu" and where we are.
		Ui.alpha = appear;
		float hy = layout.top() - 28;
		Ui.logo(g, left + 11, hy, 22);
		FontRenderer.SEMIBOLD.drawMiddle(g, I18n.get("menu.game"), left + 28, hy - 5, 11, Ui.c(Theme.TEXT));
		FontRenderer.MEDIUM.drawMiddle(g, FontRenderer.MEDIUM.ellipsize(placeName(mc), 7.5f, COLUMN_W - 30), left + 28, hy + 7, 7.5f,
			Ui.c(Theme.TEXT_DIM));

		float cardW = Math.min(196, w * 0.3f);
		float cardsX = w - 16 - cardW;
		boolean cards = cardsX > left + COLUMN_W + 40;
		if (cards) {
			float y = layout.top() - 2 + (1 - appear) * 10;
			y += card(g, cardsX, y, cardW, placeTitle(mc), placeRows(mc)) + 8;
			card(g, cardsX, y, cardW, I18n.get("aurora.pause.session"), sessionRows(mc));
		}
		// The player, turning to follow the mouse, between the column and the cards.
		float gapLeft = left + COLUMN_W + 24;
		float gapRight = cards ? cardsX - 16 : w - 24;
		if (gapRight - gapLeft > 56 && appear > 0.3f) {
			int size = Math.round(Math.min(h * 0.24f, (gapRight - gapLeft) * 0.5f));
			int cx = Math.round((gapLeft + gapRight) / 2f);
			int bottom = Math.round(h * 0.86f);
			// A soft elliptical shadow with a hint of the accent under the feet.
			g.pose().pushMatrix();
			g.pose().translate(cx, bottom - 1);
			g.pose().scale(1f, 0.22f);
			Render2D.Batch shadow = Render2D.batch();
			shadow.disc(0, 0, size * 0.95f, ColorUtil.fade(Theme.accent(), 0.16f * appear), Theme.accent() & 0xFFFFFF, 48);
			shadow.disc(0, 0, size * 0.62f, ColorUtil.fade(0xFF000000, 0.55f * appear), 0x00000000, 48);
			shadow.submit(g);
			g.pose().popMatrix();
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, cx - size, bottom - Math.round(size * 2.3f), cx + size, bottom,
				size, 0.0625f, mx, my, mc.player);
		}
		Ui.alpha = 1f;
	}

	private static String placeName(Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) {
			return server.getWorldData().getLevelName();
		}
		ServerData data = mc.getCurrentServer();
		return data != null ? data.name : I18n.get("menu.multiplayer");
	}

	private static String placeTitle(Minecraft mc) {
		return I18n.get(mc.getSingleplayerServer() != null ? "aurora.pause.world" : "aurora.pause.server");
	}

	private record Row(String label, String value, int color) {
	}

	private static List<Row> placeRows(Minecraft mc) {
		List<Row> rows = new ArrayList<>();
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) {
			rows.add(new Row(I18n.get("aurora.pause.world_name"), server.getWorldData().getLevelName(), Theme.TEXT));
			if (mc.gameMode != null) {
				rows.add(new Row(I18n.get("aurora.pause.mode"), mc.gameMode.getPlayerMode().getShortDisplayName().getString(), Theme.TEXT));
			}
			if (mc.level != null) {
				rows.add(new Row(I18n.get("aurora.pause.difficulty"), mc.level.getDifficulty().getDisplayName().getString(), Theme.TEXT));
			}
			rows.add(new Row(I18n.get("aurora.pause.lan"), I18n.get(server.isPublished() ? "aurora.pause.lan_open" : "aurora.pause.lan_closed"),
				server.isPublished() ? Theme.SUCCESS : Theme.TEXT_DIM));
			return rows;
		}
		ServerData data = mc.getCurrentServer();
		if (data != null) {
			rows.add(new Row(I18n.get("aurora.pause.server_name"), data.name, Theme.TEXT));
			rows.add(new Row(I18n.get("aurora.pause.address"), data.ip, Theme.TEXT_DIM));
		}
		if (mc.getConnection() != null && mc.player != null) {
			PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			if (info != null) {
				int ping = info.getLatency();
				rows.add(new Row(I18n.get("aurora.pause.ping"), ping + " " + I18n.get("aurora.unit.ms"), pingColor(ping)));
			}
			rows.add(new Row(I18n.get("aurora.pause.players"), String.valueOf(mc.getConnection().getOnlinePlayers().size()), Theme.TEXT));
		}
		return rows;
	}

	private static List<Row> sessionRows(Minecraft mc) {
		List<Row> rows = new ArrayList<>();
		rows.add(new Row(I18n.get("aurora.pause.time"), Session.format(Session.millis()), Theme.TEXT));
		if (mc.player != null && mc.level != null) {
			BlockPos pos = mc.player.blockPosition();
			rows.add(new Row(I18n.get("aurora.pause.position"), pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), Theme.TEXT));
			String dimension = mc.level.dimension().identifier().getPath();
			String key = "aurora.pause.dimension." + dimension;
			rows.add(new Row(I18n.get("aurora.pause.dimension"), Language.getInstance().has(key) ? I18n.get(key) : dimension, Theme.TEXT));
		}
		rows.add(new Row("FPS", String.valueOf(mc.getFps()), Theme.TEXT));
		return rows;
	}

	public static int pingColor(int ping) {
		if (ping < 0) {
			return Theme.TEXT_MUTED;
		}
		if (ping < 80) {
			return Theme.SUCCESS;
		}
		if (ping < 150) {
			return 0xFFA3E635;
		}
		if (ping < 300) {
			return Theme.WARNING;
		}
		return Theme.DANGER;
	}

	/** A glass card with a title and label/value rows; returns its height. */
	private static float card(GuiGraphicsExtractor g, float x, float y, float w, String title, List<Row> rows) {
		float rowH = 13;
		float h = 28 + rows.size() * rowH + 6;
		Render2D.shadow(g, x, y, w, h, 10, 12, Ui.c(0x59000000));
		Render2D.roundRect(g, x, y, w, h, 10, Render2D.Paint.vertical(y, h, Ui.c(0xD9161922), Ui.c(0xD90F1117)));
		Render2D.roundOutline(g, x, y, w, h, 10, 0.7f, Ui.c(0x1FFFFFFF));
		Render2D.circle(g, x + 13, y + 14, 2.2f, Render2D.Paint.diagonal(x + 11, y + 12, 4, 4, Ui.c(Theme.accent()), Ui.c(Theme.accent2())));
		FontRenderer.SEMIBOLD.drawMiddle(g, title, x + 20, y + 14, 8.5f, Ui.c(Theme.TEXT));
		Render2D.rect(g, x + 10, y + 25, w - 20, 0.6f, Ui.c(0x14FFFFFF));
		float ry = y + 28 + rowH / 2f;
		for (Row row : rows) {
			FontRenderer.MEDIUM.drawMiddle(g, row.label(), x + 12, ry, 7.5f, Ui.c(Theme.TEXT_MUTED));
			float labelW = FontRenderer.MEDIUM.width(row.label(), 7.5f);
			String value = FontRenderer.SEMIBOLD.ellipsize(row.value(), 7.5f, w - 30 - labelW);
			FontRenderer.SEMIBOLD.drawRight(g, value, x + w - 12, ry, 7.5f, Ui.c(row.color()));
			ry += rowH;
		}
		return h;
	}
}
