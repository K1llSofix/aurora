package dev.aurora.module.utility;

import dev.aurora.module.Category;
import dev.aurora.module.Module;
import dev.aurora.setting.BooleanSetting;
import dev.aurora.setting.NumberSetting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * After a kick or a lost connection, a "Reconnect" button on the disconnect screen takes you back
 * to the same server. It can also reconnect by itself after a few seconds, a limited number of
 * times in a row so a server that keeps refusing is not hammered.
 */
public final class ReconnectModule extends Module {
	/** Automatic attempts in a row before it waits for a click again. */
	private static final int MAX_AUTO_ATTEMPTS = 5;

	private final BooleanSetting auto = add(new BooleanSetting("auto", false));
	private final NumberSetting delay = add(new NumberSetting("delay", 5, 3, 60, 1)
		.format(v -> I18n.get("aurora.unit.seconds", (int) v))
		.visibleWhen(auto::on));

	private static ReconnectModule instance;
	private static @Nullable ServerAddress lastAddress;
	private static @Nullable ServerData lastServer;
	private int autoAttempts;

	public ReconnectModule() {
		super("reconnect", Category.UTILITY, Items.RECOVERY_COMPASS, true);
		instance = this;
		// Getting in resets the count of automatic attempts.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> autoAttempts = 0);
	}

	public static @Nullable ReconnectModule active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	/** Every connection attempt, successful or not, is the one to come back to. */
	public static void remember(ServerAddress address, @Nullable ServerData data) {
		if (data != null && !data.isRealm()) {
			lastAddress = address;
			lastServer = data;
		}
	}

	public boolean canReconnect() {
		return lastAddress != null && lastServer != null;
	}

	/** Ticks until an automatic reconnect, or -1 when it should wait for a click. */
	public int autoDelayTicks() {
		return auto.on() && autoAttempts < MAX_AUTO_ATTEMPTS ? delay.intValue() * 20 : -1;
	}

	/** The button was clicked: try again, and let automatic attempts start over. */
	public void reconnect() {
		autoAttempts = 0;
		connect();
	}

	public void reconnectAutomatically() {
		autoAttempts++;
		connect();
	}

	private void connect() {
		if (lastAddress != null && lastServer != null) {
			ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, lastAddress, lastServer, false, null);
		}
	}
}
