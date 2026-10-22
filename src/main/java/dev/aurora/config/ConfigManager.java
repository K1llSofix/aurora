package dev.aurora.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.aurora.module.Module;
import dev.aurora.module.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Persists every module's state to config/aurora.json. Writes are atomic (temp file + move). */
public final class ConfigManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int VERSION = 1;

	private final Path file;
	private final ModuleManager modules;
	private boolean dirty;

	public ConfigManager(ModuleManager modules) {
		this.modules = modules;
		this.file = FabricLoader.getInstance().getConfigDir().resolve("aurora.json");
	}

	public void load() {
		if (!Files.exists(file)) {
			return;
		}
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!root.isJsonObject()) {
				return;
			}
			JsonObject json = root.getAsJsonObject();
			if (json.has("modules") && json.get("modules").isJsonObject()) {
				JsonObject all = json.getAsJsonObject("modules");
				for (Module module : modules.all()) {
					JsonElement data = all.get(module.id());
					if (data != null && data.isJsonObject()) {
						module.load(data.getAsJsonObject());
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Aurora: could not read {}, keeping defaults", file, e);
			try {
				Files.copy(file, file.resolveSibling("aurora.json.broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
			}
		}
	}

	public void save() {
		JsonObject json = new JsonObject();
		json.addProperty("version", VERSION);
		JsonObject all = new JsonObject();
		for (Module module : modules.all()) {
			all.add(module.id(), module.save());
		}
		json.add("modules", all);
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("aurora.json.tmp");
			Files.writeString(tmp, GSON.toJson(json), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (IOException e) {
			LOGGER.error("Aurora: could not save {}", file, e);
		}
	}

	/** Marks the config as changed; it is written on the next {@link #saveIfDirty()}. */
	public void markDirty() {
		dirty = true;
	}

	public void saveIfDirty() {
		if (dirty) {
			save();
		}
	}
}
