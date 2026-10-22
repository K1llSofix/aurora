package dev.aurora.module.hud;

import dev.aurora.module.TextHudModule;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.Items;

public final class ServerHud extends TextHudModule {
	public ServerHud() {
		super("server", Items.NAME_TAG, false, 0, 0, 4, 216);
	}

	@Override
	protected String label() {
		return "IP";
	}

	@Override
	protected String value(boolean editing) {
		ServerData server = mc.getCurrentServer();
		if (server != null) {
			return server.ip;
		}
		return I18n.get("aurora.hud.singleplayer");
	}
}
