package dev.aurora.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.aurora.setting.KeySetting;
import dev.aurora.setting.Setting;
import dev.aurora.util.ItemIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Something the player can switch on and off and configure from the mod menu. */
public abstract class Module {
	protected static final Minecraft mc = Minecraft.getInstance();

	private final String id;
	private final Category category;
	private final Item iconItem;
	private ItemStack icon;
	private final boolean enabledByDefault;
	private boolean enabled;
	private final List<Setting<?>> settings = new ArrayList<>();
	private final KeySetting keybind = new KeySetting("keybind", KeySetting.NONE);

	protected Module(String id, Category category, Item icon, boolean enabledByDefault) {
		this.id = id;
		this.category = category;
		this.iconItem = icon;
		this.enabledByDefault = enabledByDefault;
		this.enabled = enabledByDefault;
	}

	protected <S extends Setting<?>> S add(S setting) {
		setting.bindTranslationPrefix("aurora.setting." + id);
		settings.add(setting);
		return setting;
	}

	public String id() {
		return id;
	}

	public Category category() {
		return category;
	}

	public String name() {
		return I18n.get("aurora.module." + id);
	}

	public String description() {
		return I18n.get("aurora.module." + id + ".desc");
	}

	public ItemStack icon() {
		if (icon == null) {
			icon = ItemIcons.stack(iconItem);
		}
		return icon;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	/** Whether the mod menu shows an on/off switch; always-on helpers return false. */
	public boolean canToggle() {
		return true;
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Called at the end of every client tick while enabled. */
	public void tick() {
	}

	public List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	public KeySetting keybind() {
		return keybind;
	}

	public void resetToDefaults() {
		settings.forEach(Setting::reset);
		keybind.reset();
		setEnabled(enabledByDefault);
	}

	public JsonObject save() {
		JsonObject o = new JsonObject();
		o.addProperty("enabled", enabled);
		o.add("keybind", keybind.toJson());
		JsonObject values = new JsonObject();
		for (Setting<?> s : settings) {
			values.add(s.id(), s.toJson());
		}
		o.add("settings", values);
		return o;
	}

	public void load(JsonObject o) {
		if (o.has("settings") && o.get("settings").isJsonObject()) {
			JsonObject values = o.getAsJsonObject("settings");
			for (Setting<?> s : settings) {
				JsonElement e = values.get(s.id());
				if (e != null) {
					try {
						s.fromJson(e);
					} catch (RuntimeException ignored) {
						// A hand-edited or outdated value: keep the default.
					}
				}
			}
		}
		if (o.has("keybind")) {
			keybind.fromJson(o.get("keybind"));
		}
		if (o.has("enabled")) {
			setEnabled(o.get("enabled").getAsBoolean());
		}
	}
}
