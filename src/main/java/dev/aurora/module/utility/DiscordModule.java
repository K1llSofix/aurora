package dev.aurora.module.utility;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.util.DiscordIpc;
import dev.aurora.util.Session;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Shows what you are doing in Minecraft on your Discord profile (Rich Presence): main menu,
 * singleplayer, LAN, Realms or the server you play on, the dimension, how long you have been
 * playing, your skin's head for a licensed account, and a button for others to get Aurora.
 * Each part can be hidden.
 */
public final class DiscordModule extends Module {
	/**
	 * The Discord application whose name and logo the profile shows ("Playing Aurora Client").
	 * Its art assets need the logo under the key {@link #LOGO}.
	 */
	public static final String APP_ID = "1562074726245998623";
	private static final String LOGO = "aurora";
	/** Where the profile's button leads: the homepage in fabric.mod.json, the mod's Modrinth page. */
	private static final String PAGE = FabricLoader.getInstance().getModContainer("aurora")
		.flatMap(c -> c.getMetadata().getContact().get("homepage"))
		.orElse("");

	private final BooleanSetting server = add(new BooleanSetting("show_server", true));
	private final BooleanSetting world = add(new BooleanSetting("show_world", false));
	private final BooleanSetting dimension = add(new BooleanSetting("show_dimension", true));
	private final BooleanSetting head = add(new BooleanSetting("show_head", true));
	private final BooleanSetting time = add(new BooleanSetting("show_time", true));
	private final BooleanSetting button = add(new BooleanSetting("show_button", true));

	private final long startedAt = System.currentTimeMillis();
	private @Nullable DiscordIpc ipc;
	private int ticks;

	public DiscordModule() {
		super("discord", Category.UTILITY, Items.ECHO_SHARD, true);
	}

	@Override
	protected void onDisable() {
		if (ipc != null) {
			ipc.stop();
		}
	}

	@Override
	public void tick() {
		// Once a second is plenty: the activity only changes with the screen, world or server.
		if (APP_ID.isEmpty() || ticks++ % 20 != 0) {
			return;
		}
		if (ipc == null) {
			ipc = new DiscordIpc(APP_ID);
		}
		ipc.start();
		ipc.update(activity());
	}

	private JsonObject activity() {
		String details;
		String state = null;
		long since = startedAt;
		if (mc.level == null || mc.player == null) {
			Screen screen = mc.gui.screen();
			details = I18n.get(screen instanceof JoinMultiplayerScreen ? "aurora.discord.servers"
				: screen instanceof SelectWorldScreen ? "aurora.discord.worlds"
				: screen instanceof ConnectScreen ? "aurora.discord.connecting"
				: "aurora.discord.menu");
		} else {
			since = Session.joinedAt();
			details = place();
			if (dimension.on()) {
				state = dimensionName(mc.level.dimension());
			}
		}

		JsonObject activity = new JsonObject();
		activity.addProperty("type", 0);
		activity.addProperty("details", fit(details));
		if (state != null) {
			activity.addProperty("state", fit(state));
		}
		if (time.on() && since > 0) {
			JsonObject timestamps = new JsonObject();
			timestamps.addProperty("start", since);
			activity.add("timestamps", timestamps);
		}
		JsonObject assets = new JsonObject();
		assets.addProperty("large_image", LOGO);
		assets.addProperty("large_text", fit("Aurora Client " + version() + " · Minecraft " + SharedConstants.getCurrentVersion().name()));
		UUID id = mc.getUser().getProfileId();
		// Only a licensed account (random UUID) has a skin the head service knows.
		if (head.on() && id != null && id.version() == 4) {
			assets.addProperty("small_image", "https://mc-heads.net/avatar/" + id.toString().replace("-", "") + "/64");
			assets.addProperty("small_text", fit(mc.getUser().getName()));
		}
		activity.add("assets", assets);
		// A link under the status for whoever looks at your profile.
		if (button.on() && !PAGE.isEmpty()) {
			JsonObject link = new JsonObject();
			link.addProperty("label", label(I18n.get("aurora.discord.button")));
			link.addProperty("url", PAGE);
			JsonArray buttons = new JsonArray();
			buttons.add(link);
			activity.add("buttons", buttons);
		}
		return activity;
	}

	/** Where the player is: singleplayer (with the world name if wanted), LAN, Realms or a server. */
	private String place() {
		IntegratedServer local = mc.getSingleplayerServer();
		if (local != null) {
			if (world.on()) {
				return I18n.get("aurora.discord.singleplayer_world", local.getWorldData().getLevelName());
			}
			return I18n.get("aurora.discord.singleplayer");
		}
		ServerData data = mc.getCurrentServer();
		if (data != null && data.isRealm()) {
			return I18n.get("aurora.discord.realms");
		}
		if (data != null && data.isLan()) {
			return I18n.get("aurora.discord.lan");
		}
		if (data != null && server.on() && data.ip != null && !data.ip.isBlank()) {
			return I18n.get("aurora.discord.server", data.ip.toLowerCase(Locale.ROOT));
		}
		return I18n.get("aurora.discord.multiplayer");
	}

	private static String dimensionName(ResourceKey<Level> key) {
		if (key == Level.OVERWORLD) {
			return I18n.get("aurora.discord.overworld");
		}
		if (key == Level.NETHER) {
			return I18n.get("aurora.discord.nether");
		}
		if (key == Level.END) {
			return I18n.get("aurora.discord.end");
		}
		// A modded dimension: its name from the id, "the_aether" -> "The aether".
		String path = key.identifier().getPath().replace('_', ' ');
		return path.isEmpty() ? "?" : Character.toUpperCase(path.charAt(0)) + path.substring(1);
	}

	private static String version() {
		return FabricLoader.getInstance().getModContainer("aurora")
			.map(c -> c.getMetadata().getVersion().getFriendlyString().replaceFirst("\\+.*", ""))
			.orElse("");
	}

	/** Discord takes 2 to 128 characters per line. */
	private static String fit(String text) {
		String t = text.strip();
		if (t.length() > 128) {
			t = t.substring(0, 127) + "…";
		}
		return t.length() < 2 ? t + "  " : t;
	}

	/** A button label is 1 to 32 characters. */
	private static String label(String text) {
		String t = text.strip();
		return t.length() > 32 ? t.substring(0, 31) + "…" : t.isEmpty() ? "Aurora" : t;
	}
}
