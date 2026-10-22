package dev.aurora.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
	private final Map<String, Module> byId = new LinkedHashMap<>();
	private final Map<Class<?>, Module> byClass = new LinkedHashMap<>();
	private final List<HudModule> hud = new ArrayList<>();

	public <M extends Module> M register(M module) {
		if (byId.put(module.id(), module) != null) {
			throw new IllegalStateException("Duplicate module id " + module.id());
		}
		byClass.put(module.getClass(), module);
		if (module instanceof HudModule h) {
			hud.add(h);
		}
		return module;
	}

	public List<Module> all() {
		return List.copyOf(byId.values());
	}

	/** Modules shown as cards in the menu, in registration order. */
	public List<Module> listed() {
		List<Module> list = new ArrayList<>();
		for (Module m : byId.values()) {
			if (m.category() != Category.CLIENT) {
				list.add(m);
			}
		}
		return list;
	}

	public List<HudModule> hud() {
		return Collections.unmodifiableList(hud);
	}

	public Module byId(String id) {
		return byId.get(id);
	}

	@SuppressWarnings("unchecked")
	public <M extends Module> M get(Class<M> type) {
		return (M) byClass.get(type);
	}
}
