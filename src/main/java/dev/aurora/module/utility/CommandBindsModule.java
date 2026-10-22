package dev.aurora.module.utility;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.aurora.gui.Notifications;
import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Keys that send a chat command or message, e.g. H for /home. Commands start with "/"; anything
 * else is sent as a chat message. Each press sends once, with a short cooldown against spam.
 */
public final class CommandBindsModule extends Module {
	private static final long COOLDOWN_MS = 300;

	public final BooleanSetting notify = add(new BooleanSetting("notify", true));

	/** One key and what it sends. */
	public static final class CommandBind {
		private InputConstants.Key key;
		private String command;
		private long lastSent;

		public CommandBind(InputConstants.Key key, String command) {
			this.key = key;
			this.command = command;
		}

		public InputConstants.Key key() {
			return key;
		}

		public void setKey(InputConstants.Key key) {
			this.key = key;
		}

		public String command() {
			return command;
		}

		public void setCommand(String command) {
			this.command = command;
		}
	}

	private final List<CommandBind> binds = new ArrayList<>();

	public CommandBindsModule() {
		super("command_binds", Category.UTILITY, Items.COMMAND_BLOCK, true);
	}

	public List<CommandBind> binds() {
		return binds;
	}

	public CommandBind add() {
		CommandBind bind = new CommandBind(InputConstants.UNKNOWN, "");
		binds.add(bind);
		return bind;
	}

	public void remove(CommandBind bind) {
		binds.remove(bind);
	}

	/** A key or mouse button was pressed while playing (no screen open). */
	public void onKey(InputConstants.Key key) {
		if (!isEnabled() || key.equals(InputConstants.UNKNOWN)) {
			return;
		}
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		long now = System.currentTimeMillis();
		for (CommandBind bind : binds) {
			String text = bind.command.trim();
			if (!bind.key.equals(key) || text.isEmpty() || now - bind.lastSent < COOLDOWN_MS) {
				continue;
			}
			bind.lastSent = now;
			if (text.startsWith("/")) {
				player.connection.sendCommand(text.substring(1));
			} else {
				player.connection.sendChat(text);
			}
			if (notify.on()) {
				Notifications.push(I18n.get("aurora.notify.command_sent"), text, Notifications.Kind.INFO);
			}
		}
	}

	@Override
	public JsonObject save() {
		JsonObject o = super.save();
		JsonArray list = new JsonArray();
		for (CommandBind bind : binds) {
			JsonObject b = new JsonObject();
			b.addProperty("key", bind.key.getName());
			b.addProperty("command", bind.command);
			list.add(b);
		}
		o.add("binds", list);
		return o;
	}

	@Override
	public void load(JsonObject o) {
		super.load(o);
		binds.clear();
		if (o.has("binds") && o.get("binds").isJsonArray()) {
			for (JsonElement e : o.getAsJsonArray("binds")) {
				if (!e.isJsonObject()) {
					continue;
				}
				JsonObject b = e.getAsJsonObject();
				InputConstants.Key key = InputConstants.UNKNOWN;
				try {
					if (b.has("key")) {
						key = InputConstants.getKey(b.get("key").getAsString());
					}
				} catch (RuntimeException ignored) {
					// Unknown key name from another version: keep the command, unbound.
				}
				String command = b.has("command") ? b.get("command").getAsString() : "";
				binds.add(new CommandBind(key, command));
			}
		}
	}
}
