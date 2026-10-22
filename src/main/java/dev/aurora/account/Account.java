package dev.aurora.account;

import net.minecraft.client.User;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** A Minecraft account Aurora can switch to. */
public final class Account {
	public enum Type {
		/** The account the game was started with; its token comes from the launcher each time. */
		LAUNCHER,
		MICROSOFT,
		/** A name without a licence: singleplayer, LAN and servers in offline mode. */
		OFFLINE
	}

	final Type type;
	String name;
	UUID uuid;
	/** Microsoft: refresh token for new sessions, and the current Minecraft token. */
	String refreshToken = "";
	String accessToken = "";
	long expiresAt;
	/** Launcher: the original session. */
	final @Nullable User launcherUser;
	final boolean launcherOnline;

	Account(Type type, String name, UUID uuid, @Nullable User launcherUser, boolean launcherOnline) {
		this.type = type;
		this.name = name;
		this.uuid = uuid;
		this.launcherUser = launcherUser;
		this.launcherOnline = launcherOnline;
	}

	public Type type() {
		return type;
	}

	public String name() {
		return name;
	}

	public UUID uuid() {
		return uuid;
	}

	/** Whether this account can join online-mode servers. */
	public boolean premium() {
		return type == Type.MICROSOFT || (type == Type.LAUNCHER && launcherOnline);
	}
}
