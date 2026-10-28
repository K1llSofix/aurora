package dev.aurora.module.hud;

import com.mojang.authlib.GameProfile;
import dev.aurora.Aurora;
import dev.aurora.gui.Notifications;
import dev.aurora.gui.Theme;
import dev.aurora.gui.render.FontRenderer;
import dev.aurora.gui.render.Render2D;
import dev.aurora.gui.widget.Ui;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Chat in Aurora's style: message lines share one rounded panel, new messages slide in, the input
 * bar is glass. Messages get a time stamp and the sender's head, repeats collapse into "×3", and a
 * right click copies a message. Heads, time and counters are part of the message text, so links,
 * hover text and click actions keep working exactly as before.
 */
public final class ChatModule extends Module {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter TIME_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final long SLIDE_MS = 170;

	public final BooleanSetting timestamps = add(new BooleanSetting("timestamps", true));
	public final BooleanSetting seconds = add(new BooleanSetting("seconds", false)).visibleWhen(timestamps::on);
	public final BooleanSetting heads = add(new BooleanSetting("heads", true));
	public final BooleanSetting stack = add(new BooleanSetting("stack", true));
	public final BooleanSetting animation = add(new BooleanSetting("animation", true));
	public final BooleanSetting copy = add(new BooleanSetting("copy", true));
	public final BooleanSetting mentions = add(new BooleanSetting("mentions", true));
	public final BooleanSetting mentionSound = add(new BooleanSetting("mention_sound", true)).visibleWhen(mentions::on);
	public final BooleanSetting search = add(new BooleanSetting("search", true));

	/** The vanilla chat's private lists, exposed by a mixin. */
	public interface Access {
		List<GuiMessage> aurora$messages();

		List<GuiMessage.Line> aurora$lines();

		int aurora$scroll();
	}

	/** Set while vanilla shows a signed player message, so its head can be added. */
	public static @Nullable GameProfile sender;

	private static @Nullable String lastPlain;
	private static @Nullable GuiMessage lastMessage;
	private static int lastRepeat = 1;
	private static @Nullable String pendingPlain;
	private static boolean pendingSlide;
	private static final Map<GuiMessage, String> ORIGINALS = new IdentityHashMap<>();
	/** Messages that name the player, drawn with an accent bar. */
	private static final Set<GuiMessage> MENTIONS = Collections.newSetFromMap(new IdentityHashMap<>());
	private static boolean pendingMention;

	private static long slideStart;
	private static float slideDistance;

	private static boolean recording;
	private static int recordScreenHeight;
	private static @Nullable GuiGraphicsExtractor recordGraphics;
	private static final List<float[]> LINES = new ArrayList<>();

	private static boolean searching;
	private static String query = "";
	private static @Nullable List<GuiMessage.Line> found;
	private static @Nullable List<GuiMessage.Line> foundFrom;
	private static int foundFromSize = -1;
	private static GuiMessage.@Nullable Line foundFromFirst;

	public ChatModule() {
		super("chat", Category.HUD, Items.WRITABLE_BOOK, true);
	}

	private static @Nullable ChatModule active() {
		ChatModule chat = Aurora.modules() == null ? null : Aurora.modules().get(ChatModule.class);
		return chat != null && chat.isEnabled() ? chat : null;
	}

	// ======================================================================= messages

	/** Adds the time, the sender's head and a repeat counter; removes the repeated message. */
	public static Component decorate(Component contents, Access chat) {
		ChatModule module = active();
		pendingPlain = null;
		if (module == null) {
			return contents;
		}
		String plain = contents.getString();
		int repeat = 1;
		List<GuiMessage> messages = chat.aurora$messages();
		if (module.stack.on() && lastMessage != null && plain.equals(lastPlain) && !messages.isEmpty() && messages.get(0) == lastMessage) {
			repeat = lastRepeat + 1;
			GuiMessage old = lastMessage;
			messages.remove(0);
			chat.aurora$lines().removeIf(line -> line.parent() == old);
			ORIGINALS.remove(old);
		}
		lastPlain = plain;
		lastRepeat = repeat;

		MutableComponent out = Component.empty();
		if (module.timestamps.on()) {
			String time = LocalTime.now().format(module.seconds.on() ? TIME_SECONDS : TIME);
			out.append(Component.literal(time + " ").withColor(0x767C8A));
		}
		GameProfile profile = sender != null ? sender : guessSender(plain);
		pendingMention = module.mentions.on() && mentionsPlayer(plain, profile);
		if (pendingMention && module.mentionSound.on()) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.6f));
		}
		if (module.heads.on() && profile != null) {
			out.append(Component.object(new PlayerSprite(ResolvableProfile.createResolved(profile), true), Component.empty()));
			out.append(Component.literal(" "));
		}
		out.append(contents);
		if (repeat > 1) {
			out.append(Component.literal(" ×" + repeat).withColor(Theme.accent() & 0xFFFFFF));
		}
		pendingPlain = plain;
		// A repeat replaces its line in place, so nothing needs to slide.
		pendingSlide = module.animation.on() && repeat == 1;
		return out;
	}

	/** The message vanilla just created from {@link #decorate}. */
	public static void onAdded(GuiMessage message) {
		if (pendingPlain == null) {
			return;
		}
		ORIGINALS.put(message, pendingPlain);
		if (pendingMention) {
			MENTIONS.add(message);
		}
		lastMessage = message;
		pendingPlain = null;
		pendingMention = false;
		if (ORIGINALS.size() > 160) {
			List<GuiMessage> live = ((Access) Minecraft.getInstance().gui.hud.getChat()).aurora$messages();
			ORIGINALS.keySet().removeIf(m -> !live.contains(m));
			MENTIONS.removeIf(m -> !live.contains(m));
		}
	}

	/** A new message is being laid out: slide the chat up by its height. */
	public static void onDisplayed(GuiMessage message, int scroll) {
		if (!pendingSlide) {
			return;
		}
		pendingSlide = false;
		if (scroll > 0) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		int width = Mth.floor(ChatComponent.getWidth(mc.options.chatWidth().get()) / mc.options.chatScale().get());
		int lines = message.splitLines(mc.font, width).size();
		float entry = (float) (9.0 * (mc.options.chatLineSpacing().get() + 1.0));
		float remaining = slideOffset();
		slideDistance = remaining + lines * entry;
		slideStart = System.currentTimeMillis();
	}

	/** How far below its place the chat is drawn right now (in chat units). */
	public static float slideOffset() {
		if (slideDistance <= 0) {
			return 0;
		}
		float t = (System.currentTimeMillis() - slideStart) / (float) SLIDE_MS;
		if (t >= 1) {
			slideDistance = 0;
			return 0;
		}
		float eased = 1 - (1 - t) * (1 - t) * (1 - t);
		return slideDistance * (1 - eased);
	}

	/** A system message that starts with a known player's name ("Steve joined", "[VIP] Steve: hi"). */
	private static @Nullable GameProfile guessSender(String plain) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) {
			return null;
		}
		String head = plain.length() > 48 ? plain.substring(0, 48) : plain;
		GameProfile best = null;
		int bestIndex = Integer.MAX_VALUE;
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			String name = info.getProfile().name();
			if (name.length() < 3) {
				continue;
			}
			int index = indexOfWord(head, name);
			if (index >= 0 && index < bestIndex) {
				best = info.getProfile();
				bestIndex = index;
			}
		}
		return best;
	}

	/** Someone else's message with the player's name in it as a word, in any letter case. */
	private static boolean mentionsPlayer(String plain, @Nullable GameProfile from) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		GameProfile self = mc.player.getGameProfile();
		if (from != null && from.id().equals(self.id())) {
			return false;
		}
		String name = self.name();
		return name.length() >= 3 && indexOfWord(plain.toLowerCase(Locale.ROOT), name.toLowerCase(Locale.ROOT)) >= 0;
	}

	private static int indexOfWord(String text, String word) {
		int from = 0;
		while (true) {
			int i = text.indexOf(word, from);
			if (i < 0) {
				return -1;
			}
			int end = i + word.length();
			boolean before = i == 0 || !isNameChar(text.charAt(i - 1));
			boolean after = end >= text.length() || !isNameChar(text.charAt(end));
			if (before && after) {
				return i;
			}
			from = i + 1;
		}
	}

	private static boolean isNameChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	/** Right click on a line copies its message without the time and head. */
	public static boolean copyAt(double mouseX, double mouseY, int button, int screenHeight) {
		ChatModule module = active();
		if (module == null || !module.copy.on() || button != 3) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		ChatComponent component = mc.gui.hud.getChat();
		Access chat = (Access) component;
		double scale = mc.options.chatScale().get();
		int entry = (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0));
		int chatBottom = Mth.floor((screenHeight - 40) / scale);
		int maxWidth = Mth.ceil(ChatComponent.getWidth(mc.options.chatWidth().get()) / scale);
		double localX = mouseX / scale - 4;
		double localY = mouseY / scale;
		if (localX < -4 || localX > maxWidth + 8 || entry <= 0) {
			return false;
		}
		int index = (int) Math.floor((chatBottom - localY) / entry);
		if (index < 0 || index >= component.getLinesPerPage()) {
			return false;
		}
		List<GuiMessage.Line> lines = chat.aurora$lines();
		int line = index + chat.aurora$scroll();
		if (line >= lines.size()) {
			return false;
		}
		GuiMessage message = lines.get(line).parent();
		String text = ORIGINALS.getOrDefault(message, message.content().getString());
		mc.keyboardHandler.setClipboard(text);
		Notifications.push(I18n.get("aurora.notify.copied"), text.length() > 40 ? text.substring(0, 40) + "…" : text, Notifications.Kind.INFO);
		return true;
	}

	// ======================================================================= background

	public static void beginBackground(int screenHeight) {
		recording = active() != null;
		recordScreenHeight = screenHeight;
		recordGraphics = null;
		LINES.clear();
	}

	/** Collects the per-line background fills (they start at x = -4) instead of drawing them. */
	public static boolean recordBackground(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		if (!recording || x0 != -4) {
			return false;
		}
		recordGraphics = g;
		float alpha = (color >>> 24) / 255f;
		LINES.add(new float[]{y0, y1, x0, x1, alpha, mentionAlpha(y1, alpha)});
		return true;
	}

	/** How strongly the line ending at {@code bottom} is marked as a mention: 0 when it is not one. */
	private static float mentionAlpha(int bottom, float alpha) {
		if (MENTIONS.isEmpty()) {
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		Access chat = (Access) mc.gui.hud.getChat();
		int chatBottom = Mth.floor((recordScreenHeight - 40) / mc.options.chatScale().get());
		int entry = (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0));
		if (entry <= 0) {
			return 0;
		}
		int index = (chatBottom - bottom) / entry + chat.aurora$scroll();
		List<GuiMessage.Line> lines = chat.aurora$lines();
		if (index < 0 || index >= lines.size() || !MENTIONS.contains(lines.get(index).parent())) {
			return 0;
		}
		// The fill is the line's own fade times the background opacity; the mark follows the fade only.
		float background = mc.options.textBackgroundOpacity().get().floatValue();
		return background > 0.01f ? Math.min(1f, alpha / background) : 1f;
	}

	/** Draws the collected lines as rounded panels, one per run of touching lines. */
	public static void flushBackground() {
		if (!recording) {
			return;
		}
		recording = false;
		GuiGraphicsExtractor g = recordGraphics;
		if (g == null || LINES.isEmpty()) {
			return;
		}
		LINES.sort((a, b) -> Float.compare(a[0], b[0]));
		int start = 0;
		for (int i = 1; i <= LINES.size(); i++) {
			if (i == LINES.size() || LINES.get(i)[0] != LINES.get(i - 1)[1]) {
				panel(g, start, i - 1);
				start = i;
			}
		}
		LINES.clear();
	}

	private static void panel(GuiGraphicsExtractor g, int first, int last) {
		float[] top = LINES.get(first);
		float[] bottom = LINES.get(last);
		float x0 = top[2];
		float x1 = top[3];
		float y0 = top[0];
		float y1 = bottom[1];
		float maxAlpha = 0;
		for (int i = first; i <= last; i++) {
			maxAlpha = Math.max(maxAlpha, LINES.get(i)[4]);
		}
		int cTop = ColorUtil.withAlpha(0x0B0D12, Math.round(255 * top[4]));
		int cBottom = ColorUtil.withAlpha(0x0B0D12, Math.round(255 * bottom[4]));
		float r = Math.min(4, (y1 - y0) / 2f);
		Render2D.roundRect(g, x0, y0, x1 - x0, y1 - y0, r, Render2D.Paint.vertical(y0, y1 - y0, cTop, cBottom));
		Render2D.roundOutline(g, x0, y0, x1 - x0, y1 - y0, r, 0.5f, ColorUtil.withAlpha(0xFFFFFF, Math.round(24 * maxAlpha)));
		for (int i = first; i <= last; i++) {
			float[] line = LINES.get(i);
			float mark = line[5];
			if (mark <= 0) {
				continue;
			}
			float h = line[1] - line[0];
			Render2D.roundRect(g, x0, line[0], x1 - x0, h, Math.min(r, h / 2f), ColorUtil.fade(Theme.accent(), 0.16f * mark));
			Render2D.roundRect(g, x0 + 1, line[0] + 1, 1.5f, h - 2, 0.75f,
				Render2D.Paint.vertical(line[0], h, ColorUtil.fade(Theme.accent(), mark), ColorUtil.fade(Theme.accent2(), mark)));
		}
	}

	// ======================================================================= search

	/** Whether Ctrl+F in the open chat searches its messages. */
	public static boolean searchAvailable() {
		ChatModule chat = active();
		return chat != null && chat.search.on();
	}

	public static boolean searching() {
		return searching && searchAvailable();
	}

	public static void setSearching(boolean on) {
		searching = on;
		setQuery("");
	}

	/** The text typed while searching; scrolls back to the newest match when it changes. */
	public static void setQuery(String text) {
		String q = text.trim().toLowerCase(Locale.ROOT);
		if (!q.equals(query)) {
			query = q;
			found = null;
			Minecraft.getInstance().gui.hud.getChat().resetChatScroll();
		}
	}

	/** The chat lines to show: all of them, or while searching only those of matching messages. */
	public static List<GuiMessage.Line> visibleLines(List<GuiMessage.Line> all) {
		if (!searching() || query.isEmpty()) {
			return all;
		}
		GuiMessage.Line first = all.isEmpty() ? null : all.getFirst();
		if (found == null || foundFrom != all || foundFromSize != all.size() || foundFromFirst != first) {
			List<GuiMessage.Line> lines = new ArrayList<>();
			for (GuiMessage.Line line : all) {
				String text = ORIGINALS.getOrDefault(line.parent(), line.parent().content().getString());
				if (text.toLowerCase(Locale.ROOT).contains(query)) {
					lines.add(line);
				}
			}
			found = lines;
			foundFrom = all;
			foundFromSize = all.size();
			foundFromFirst = first;
		}
		return found;
	}

	/** How many messages match, for the badge in the input bar. */
	private static int matchCount() {
		if (found == null) {
			return 0;
		}
		Set<GuiMessage> messages = Collections.newSetFromMap(new IdentityHashMap<>());
		for (GuiMessage.Line line : found) {
			messages.add(line.parent());
		}
		return messages.size();
	}


	/** The chat input bar as a glass field; returns false to let vanilla draw it. */
	public static boolean drawInputBar(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		if (active() == null) {
			return false;
		}
		float w = x1 - x0;
		float h = y1 - y0;
		Render2D.roundRect(g, x0, y0, w, h, 4, Render2D.Paint.vertical(y0, h, 0xEB171A23, 0xEB0E1016));
		Render2D.roundOutline(g, x0, y0, w, h, 4, searching() ? 1f : 0.6f, Render2D.Paint.horizontal(x0, w,
			ColorUtil.fade(Theme.accent(), searching() ? 1f : 0.6f), ColorUtil.fade(Theme.accent2(), searching() ? 0.7f : 0.25f)));
		if (searching()) {
			// A magnifier and how many messages match, at the right end of the bar.
			String count = query.isEmpty() ? I18n.get("aurora.chat.search_label") : I18n.get("aurora.chat.search_found", matchCount());
			float tw = FontRenderer.MEDIUM.width(count, 7.5f);
			float cy = y0 + h / 2f;
			FontRenderer.MEDIUM.drawMiddle(g, count, x1 - 6 - tw, cy, 7.5f, Theme.TEXT_DIM);
			Ui.iconSearch(g, x1 - 14 - tw, cy, 8, Theme.accent());
		}
		return true;
	}
}
