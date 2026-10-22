package dev.aurora.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.aurora.Aurora;
import dev.aurora.gui.Notifications;
import dev.aurora.module.ClientSettings;
import dev.aurora.module.Module;
import dev.aurora.module.utility.CommandBindsModule;
import dev.aurora.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Config profiles: snapshots of which modules are on, their settings and where HUD elements sit.
 * The client's own settings (theme, menu, language), module keys and command binds are not part
 * of a profile, so switching never changes the controls. Built-in presets cover common setups;
 * players save their own, share them as a code and can switch automatically per server.
 */
public final class Profiles {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("aurora").resolve("profiles");
	private static final Path STATE = DIR.resolve("state.json");
	private static final Path BEFORE = DIR.resolve("_before.json");
	private static final String CODE_PREFIX = "AURORA1:";

	/** A profile: a built-in preset (no file) or one the player saved. */
	public record Profile(String id, String name, boolean builtin, @Nullable Preset preset, @Nullable JsonObject modules, long saved) {
		public String description() {
			return builtin ? I18n.get("aurora.profiles.preset." + id + ".desc") : "";
		}
	}

	/** A preset: these modules on, everything else off, a few settings changed from the defaults. */
	public record Preset(Set<String> enabled, Map<String, Map<String, Object>> settings) {
	}

	private static final Map<String, Preset> PRESETS = new LinkedHashMap<>();

	static {
		PRESETS.put("pvp", new Preset(
			Set.of("fps", "cps", "keystrokes", "armor", "potions", "target", "reach", "combo", "ping", "scoreboard", "boss_bar", "tab_list",
				"chat", "crosshair", "block_outline", "zoom", "freelook", "hit_color", "particles", "overlays", "hotbar", "toggle_sprint",
				"command_binds", "mouse_tweaks", "screenshots", "discord", "item_counter", "performance"),
			Map.of("crosshair", Map.of("style", "cross_dot", "dynamic", true, "target_color", true, "cooldown", true),
				"particles", Map.of("always_crit", true, "multiplier", 2),
				"overlays", Map.of("low_fire", true, "no_hurt_cam", true))));
		PRESETS.put("survival", new Preset(
			Set.of("fps", "coordinates", "compass", "clock", "armor", "potions", "ping", "scoreboard", "boss_bar", "tab_list", "chat",
				"crosshair", "block_outline", "zoom", "freelook", "overlays", "hotbar", "toggle_sprint", "command_binds", "item_counter",
				"mouse_tweaks", "screenshots", "discord", "performance"),
			Map.of("coordinates", Map.of("biome", true, "direction", true, "nether", true))));
		PRESETS.put("builder", new Preset(
			Set.of("fps", "coordinates", "compass", "memory", "scoreboard", "boss_bar", "tab_list", "chat", "crosshair", "block_outline",
				"zoom", "freelook", "time_changer", "weather", "hotbar", "command_binds", "mouse_tweaks", "screenshots", "discord", "item_counter", "performance"),
			Map.of("block_outline", Map.of("fill", true, "width", 2.5),
				"time_changer", Map.of("time", 12, "cycle", false))));
		PRESETS.put("minimal", new Preset(
			Set.of("fps", "chat", "tab_list", "crosshair", "zoom", "command_binds", "mouse_tweaks", "screenshots", "discord", "performance"),
			Map.of("crosshair", Map.of("style", "dot"))));
		PRESETS.put("streamer", new Preset(
			Set.of("keystrokes", "cps", "armor", "potions", "target", "scoreboard", "boss_bar", "tab_list", "chat", "crosshair",
				"block_outline", "zoom", "freelook", "overlays", "hotbar", "command_binds", "mouse_tweaks", "screenshots", "discord"),
			Map.of()));
	}

	private static @Nullable String active;
	private static final Map<String, String> SERVERS = new LinkedHashMap<>();
	/** The setup before a server's profile was switched in automatically, put back on leaving. */
	private static @Nullable JsonObject beforeAuto;

	private Profiles() {
	}

	// ======================================================================= listing

	public static List<Profile> builtins() {
		List<Profile> list = new ArrayList<>();
		for (Map.Entry<String, Preset> e : PRESETS.entrySet()) {
			list.add(new Profile(e.getKey(), I18n.get("aurora.profiles.preset." + e.getKey()), true, e.getValue(), null, 0));
		}
		return list;
	}

	public static List<Profile> saved() {
		List<Profile> list = new ArrayList<>();
		if (!Files.isDirectory(DIR)) {
			return list;
		}
		try (Stream<Path> files = Files.list(DIR)) {
			for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
				String name = file.getFileName().toString();
				if (name.startsWith("_") || name.equals("state.json")) {
					continue;
				}
				try {
					JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
					String id = name.substring(0, name.length() - 5);
					list.add(new Profile(id, o.has("name") ? o.get("name").getAsString() : id, false, null,
						o.has("modules") ? o.getAsJsonObject("modules") : new JsonObject(), o.has("saved") ? o.get("saved").getAsLong() : 0));
				} catch (IOException | RuntimeException e) {
					LOGGER.warn("Aurora: skipping the broken profile {}: {}", file, e.toString());
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Aurora: could not list profiles: {}", e.toString());
		}
		list.sort((a, b) -> Long.compare(a.saved(), b.saved()));
		return list;
	}

	public static @Nullable String active() {
		loadState();
		return active;
	}

	public static boolean canUndo() {
		return Files.isRegularFile(BEFORE);
	}

	// ======================================================================= applying

	/** The current setup as profile data: every module except the client page, without binds. */
	public static JsonObject snapshot() {
		JsonObject all = new JsonObject();
		for (Module module : Aurora.modules().all()) {
			if (module instanceof ClientSettings) {
				continue;
			}
			JsonObject data = module.save();
			data.remove("keybind");
			data.remove("binds");
			all.add(module.id(), data);
		}
		return all;
	}

	/** Everything profiles touch plus keys and binds: what "undo" puts back exactly. */
	private static JsonObject fullSnapshot() {
		JsonObject all = new JsonObject();
		for (Module module : Aurora.modules().all()) {
			if (!(module instanceof ClientSettings)) {
				all.add(module.id(), module.save());
			}
		}
		return all;
	}

	public static void apply(Profile profile) {
		writeJson(BEFORE, wrap("before", fullSnapshot()));
		if (profile.builtin() && profile.preset() != null) {
			applyPreset(profile.preset());
		} else if (profile.modules() != null) {
			applyModules(profile.modules(), false);
		}
		active = profile.id();
		saveState();
		Aurora.config().save();
	}

	/** Puts back the setup from before the last profile was loaded. */
	public static boolean undo() {
		try {
			JsonObject o = JsonParser.parseString(Files.readString(BEFORE, StandardCharsets.UTF_8)).getAsJsonObject();
			applyModules(o.getAsJsonObject("modules"), true);
			Files.deleteIfExists(BEFORE);
			active = null;
			saveState();
			Aurora.config().save();
			return true;
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Aurora: nothing to undo: {}", e.toString());
			return false;
		}
	}

	private static void applyPreset(Preset preset) {
		for (Module module : Aurora.modules().all()) {
			if (module instanceof ClientSettings) {
				continue;
			}
			JsonObject kept = keepControls(module);
			module.resetToDefaults();
			Map<String, Object> changes = preset.settings().get(module.id());
			if (changes != null) {
				for (Setting<?> s : module.settings()) {
					Object value = changes.get(s.id());
					if (value != null) {
						try {
							s.fromJson(GSON.toJsonTree(value));
						} catch (RuntimeException ignored) {
							// A preset value that no longer fits the setting: keep its default.
						}
					}
				}
			}
			if (module.canToggle()) {
				module.setEnabled(preset.enabled().contains(module.id()));
			}
			module.load(kept);
		}
	}

	/**
	 * Loads saved module data. With {@code exact} (undo) keys and binds come from the data too;
	 * otherwise the current ones stay.
	 */
	private static void applyModules(JsonObject modules, boolean exact) {
		for (Module module : Aurora.modules().all()) {
			if (module instanceof ClientSettings) {
				continue;
			}
			JsonObject kept = exact ? new JsonObject() : keepControls(module);
			module.resetToDefaults();
			JsonElement data = modules.get(module.id());
			if (data != null && data.isJsonObject()) {
				module.load(data.getAsJsonObject().deepCopy());
			}
			module.load(kept);
		}
	}

	/** A module's key and (for command binds) the bind list, to load back after a profile. */
	private static JsonObject keepControls(Module module) {
		JsonObject saved = module.save();
		JsonObject kept = new JsonObject();
		if (saved.has("keybind")) {
			kept.add("keybind", saved.get("keybind"));
		}
		if (module instanceof CommandBindsModule && saved.has("binds")) {
			kept.add("binds", saved.get("binds"));
		}
		return kept;
	}

	// ======================================================================= saving

	public static Profile saveCurrent(String name) {
		String id = "p" + Long.toString(System.currentTimeMillis(), 36);
		Profile profile = new Profile(id, name, false, null, snapshot(), System.currentTimeMillis());
		write(profile);
		active = id;
		saveState();
		return profile;
	}

	public static void overwrite(Profile profile) {
		write(new Profile(profile.id(), profile.name(), false, null, snapshot(), System.currentTimeMillis()));
		active = profile.id();
		saveState();
	}

	public static void rename(Profile profile, String name) {
		if (profile.builtin() || name.isBlank()) {
			return;
		}
		write(new Profile(profile.id(), name.trim(), false, null, profile.modules(), profile.saved()));
	}

	public static void delete(Profile profile) {
		if (profile.builtin()) {
			return;
		}
		try {
			Files.deleteIfExists(DIR.resolve(profile.id() + ".json"));
		} catch (IOException e) {
			LOGGER.warn("Aurora: could not delete profile {}: {}", profile.id(), e.toString());
		}
		SERVERS.values().removeIf(profile.id()::equals);
		if (profile.id().equals(active)) {
			active = null;
		}
		saveState();
	}

	private static void write(Profile profile) {
		JsonObject o = wrap("modules", profile.modules());
		o.addProperty("name", profile.name());
		o.addProperty("saved", profile.saved());
		writeJson(DIR.resolve(profile.id() + ".json"), o);
	}

	// ======================================================================= sharing

	/** A profile as a short text code: gzip + base64 of its data, prefixed so it is recognisable. */
	public static String exportCode(Profile profile) {
		JsonObject o = new JsonObject();
		o.addProperty("name", profile.name());
		o.add("modules", profile.builtin() ? null : profile.modules());
		if (profile.builtin()) {
			o.addProperty("preset", profile.id());
		}
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
				gzip.write(GSON.toJson(o).getBytes(StandardCharsets.UTF_8));
			}
			return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Reads a code made by {@link #exportCode}; returns the new profile or null if it is not one. */
	public static @Nullable Profile importCode(String text) {
		String code = text == null ? "" : text.trim();
		if (!code.startsWith(CODE_PREFIX)) {
			return null;
		}
		try {
			byte[] raw = Base64.getUrlDecoder().decode(code.substring(CODE_PREFIX.length()));
			String json;
			try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(raw))) {
				json = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
			}
			JsonObject o = JsonParser.parseString(json).getAsJsonObject();
			String name = o.has("name") ? o.get("name").getAsString() : I18n.get("aurora.profiles.imported");
			if (o.has("preset")) {
				// A shared preset: everyone has it already.
				return builtins().stream().filter(p -> p.id().equals(o.get("preset").getAsString())).findFirst().orElse(null);
			}
			Profile profile = new Profile("p" + Long.toString(System.currentTimeMillis(), 36), name, false, null,
				o.getAsJsonObject("modules"), System.currentTimeMillis());
			write(profile);
			return profile;
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Aurora: not a profile code: {}", e.toString());
			return null;
		}
	}

	// ======================================================================= per-server profiles

	public static @Nullable String serverOf(Profile profile) {
		loadState();
		for (Map.Entry<String, String> e : SERVERS.entrySet()) {
			if (e.getValue().equals(profile.id())) {
				return e.getKey();
			}
		}
		return null;
	}

	/** Binds a profile to server addresses (comma separated; "hypixel.net" also covers mc.hypixel.net). */
	public static void setServer(Profile profile, String patterns) {
		loadState();
		SERVERS.values().removeIf(profile.id()::equals);
		for (String p : patterns.split(",")) {
			String pattern = p.trim().toLowerCase(Locale.ROOT);
			// "*.example.net" means the same as "example.net": subdomains always match.
			if (pattern.startsWith("*.")) {
				pattern = pattern.substring(2);
			}
			if (!pattern.isEmpty()) {
				SERVERS.put(pattern, profile.id());
			}
		}
		saveState();
	}

	static boolean matches(String pattern, String host) {
		if (pattern.startsWith("*.")) {
			pattern = pattern.substring(2);
		}
		return host.equals(pattern) || host.endsWith("." + pattern);
	}

	/** Called on joining a server: switches to the profile bound to it, if any. */
	public static void onJoin(Minecraft mc) {
		ServerData server = mc.getCurrentServer();
		if (server == null || server.ip == null) {
			return;
		}
		loadState();
		String host = server.ip.toLowerCase(Locale.ROOT).replaceFirst(":\\d+$", "");
		for (Map.Entry<String, String> e : SERVERS.entrySet()) {
			if (!matches(e.getKey(), host)) {
				continue;
			}
			Profile profile = find(e.getValue());
			if (profile == null || profile.id().equals(active)) {
				return;
			}
			beforeAuto = fullSnapshot();
			String previous = active;
			apply(profile);
			// The undo file should point at the player's own setup, not the server's.
			writeJson(BEFORE, wrap("before", beforeAuto));
			beforeActive = previous;
			Notifications.push(I18n.get("aurora.profiles.auto_title", profile.name()), host, Notifications.Kind.INFO);
			return;
		}
	}

	private static @Nullable String beforeActive;

	/** Called on leaving a server: puts back the setup from before an automatic switch. */
	public static void onLeave() {
		if (beforeAuto == null) {
			return;
		}
		applyModules(beforeAuto, true);
		beforeAuto = null;
		active = beforeActive;
		saveState();
		Aurora.config().save();
	}

	public static @Nullable Profile find(String id) {
		for (Profile p : builtins()) {
			if (p.id().equals(id)) {
				return p;
			}
		}
		for (Profile p : saved()) {
			if (p.id().equals(id)) {
				return p;
			}
		}
		return null;
	}

	// ======================================================================= files

	private static boolean stateLoaded;

	private static void loadState() {
		if (stateLoaded) {
			return;
		}
		stateLoaded = true;
		try {
			if (!Files.isRegularFile(STATE)) {
				return;
			}
			JsonObject o = JsonParser.parseString(Files.readString(STATE, StandardCharsets.UTF_8)).getAsJsonObject();
			active = o.has("active") && !o.get("active").isJsonNull() ? o.get("active").getAsString() : null;
			if (o.has("servers") && o.get("servers").isJsonObject()) {
				for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("servers").entrySet()) {
					SERVERS.put(e.getKey(), e.getValue().getAsString());
				}
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Aurora: could not read profile state: {}", e.toString());
		}
	}

	private static void saveState() {
		loadState();
		JsonObject o = new JsonObject();
		o.addProperty("active", active);
		JsonObject servers = new JsonObject();
		SERVERS.forEach(servers::addProperty);
		o.add("servers", servers);
		writeJson(STATE, o);
	}

	private static JsonObject wrap(String key, @Nullable JsonObject modules) {
		JsonObject o = new JsonObject();
		o.add("modules", modules == null ? new JsonObject() : modules);
		o.addProperty("kind", key);
		return o;
	}

	private static void writeJson(Path file, JsonObject json) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(json), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOGGER.warn("Aurora: could not write {}: {}", file, e.toString());
		}
	}
}
