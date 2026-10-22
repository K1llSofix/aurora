package dev.aurora.module.hud;

import dev.aurora.module.TextHudModule;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.item.Items;

public final class PingHud extends TextHudModule {
	public PingHud() {
		super("ping", Items.ENDER_PEARL, false, 0, 0, 4, 108);
	}

	@Override
	protected String label() {
		return "PING";
	}

	@Override
	protected String value(boolean editing) {
		if (mc.player == null || mc.getConnection() == null) {
			return editing ? "24 ms" : "-";
		}
		if (mc.hasSingleplayerServer()) {
			return "0 ms";
		}
		PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		return info == null ? "-" : info.getLatency() + " ms";
	}
}
