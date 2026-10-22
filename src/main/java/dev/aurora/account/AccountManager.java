package dev.aurora.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.services.FriendsService;
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
import com.mojang.authlib.services.ProfileResult;
import com.mojang.logging.LogUtils;
import dev.aurora.mixin.MinecraftAccountAccessor;
import dev.aurora.mixin.RealmsClientAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * The account switcher: the launcher's account plus saved Microsoft and offline accounts. Switching
 * swaps the game's session (and everything tied to it: chat signing keys, friends, telemetry,
 * reports, Realms) without a restart. Network work happens off the render thread.
 */
public final class AccountManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("aurora").resolve("accounts.json");
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	/** Refresh a Minecraft token that expires within this time. */
	private static final long REFRESH_MARGIN = 10 * 60 * 1000L;

	private static final List<Account> SAVED = new ArrayList<>();
	private static @Nullable Account launcher;
	private static @Nullable Account current;
	private static String clientId = "";
	/** "launcher" or "type:uuid" of the account chosen last, restored on the next start. */
	private static String selected = "launcher";
	private static volatile boolean busy;

	private AccountManager() {
	}

	/** Records the launcher's session and reads saved accounts; called once the client starts. */
	public static void init(Minecraft mc) {
		User user = mc.getUser();
		boolean online = !mc.isOfflineDeveloperMode() && user.getAccessToken().length() > 64;
		launcher = new Account(Account.Type.LAUNCHER, user.getName(), user.getProfileId(), user, online);
		current = launcher;
		load();
		// The Aurora launcher starts the game with the account picked there, which should win over
		// the one chosen in game last time.
		if (System.getProperty("aurora.launcher") != null) {
			selected = "launcher";
			return;
		}
		// Come back as the account that was chosen last time.
		for (Account account : SAVED) {
			if (key(account).equals(selected)) {
				switchTo(account).whenComplete((result, error) -> {
					if (error != null) {
						LOGGER.warn("Aurora: could not restore account {}: {}", account.name, error.toString());
					}
				});
				break;
			}
		}
	}

	private static String key(Account account) {
		return account.type == Account.Type.LAUNCHER ? "launcher" : account.type.name().toLowerCase(java.util.Locale.ROOT) + ":" + account.uuid;
	}

	public static List<Account> all() {
		List<Account> list = new ArrayList<>();
		if (launcher != null) {
			list.add(launcher);
		}
		list.addAll(SAVED);
		return Collections.unmodifiableList(list);
	}

	public static @Nullable Account current() {
		return current;
	}

	public static boolean busy() {
		return busy;
	}

	public static String clientId() {
		return clientId;
	}

	public static void setClientId(String id) {
		clientId = id.trim();
		save();
	}

	public static boolean validName(String name) {
		return NAME.matcher(name).matches();
	}

	// ======================================================================= editing

	public static Account addOffline(String name) {
		for (Account a : SAVED) {
			if (a.type == Account.Type.OFFLINE && a.name.equalsIgnoreCase(name)) {
				return a;
			}
		}
		Account account = new Account(Account.Type.OFFLINE, name, UUIDUtil.createOfflinePlayerUUID(name), null, false);
		SAVED.add(account);
		save();
		return account;
	}

	/** Adds (or updates) a Microsoft account after a successful sign-in. */
	public static Account addMicrosoft(MicrosoftAuth.Tokens tokens, MicrosoftAuth.Session session) {
		Account account = null;
		for (Account a : SAVED) {
			if (a.type == Account.Type.MICROSOFT && a.uuid.equals(session.uuid())) {
				account = a;
			}
		}
		if (account == null) {
			account = new Account(Account.Type.MICROSOFT, session.name(), session.uuid(), null, true);
			SAVED.add(account);
		}
		account.name = session.name();
		account.refreshToken = tokens.refreshToken();
		account.accessToken = session.accessToken();
		account.expiresAt = session.expiresAt();
		save();
		return account;
	}

	public static void remove(Account account) {
		if (account.type == Account.Type.LAUNCHER || account == current) {
			return;
		}
		SAVED.remove(account);
		save();
	}

	// ======================================================================= switching

	/** The prepared session; network work is done, applying it is instant. */
	private record Prepared(Account account, User user, boolean online, MinecraftServicesDiscoveryService discovery,
							UserApiService api, UserApiService.UserProperties properties, @Nullable ProfileResult profile) {
	}

	/**
	 * Switches to {@code account}. The future fails with a {@link MicrosoftAuth.AuthException} when
	 * a Microsoft session cannot be renewed.
	 */
	public static CompletableFuture<Account> switchTo(Account account) {
		Minecraft mc = Minecraft.getInstance();
		if (busy) {
			return CompletableFuture.failedFuture(new IllegalStateException("busy"));
		}
		busy = true;
		return CompletableFuture.supplyAsync(() -> prepare(mc, account), Util.nonCriticalIoPool())
			.thenApplyAsync(prepared -> {
				apply(mc, prepared);
				current = account;
				selected = key(account);
				save();
				return account;
			}, mc)
			.whenComplete((result, error) -> busy = false);
	}

	private static Prepared prepare(Minecraft mc, Account account) {
		User user;
		boolean online;
		switch (account.type) {
			case LAUNCHER -> {
				user = account.launcherUser;
				online = account.launcherOnline;
			}
			case OFFLINE -> {
				user = new User(account.name, account.uuid, "0", Optional.empty(), Optional.empty());
				online = false;
			}
			default -> {
				renewIfNeeded(account);
				user = new User(account.name, account.uuid, account.accessToken, Optional.empty(), Optional.empty());
				online = true;
			}
		}
		MinecraftServicesDiscoveryService discovery = online
			? MinecraftServicesDiscoveryService.create(mc.getProxy(), true)
			: MinecraftServicesDiscoveryService.createOffline(mc.getProxy());
		UserApiService api = online ? discovery.createUserApiService(user.getAccessToken()) : UserApiService.OFFLINE;
		UserApiService.UserProperties properties;
		try {
			properties = api.fetchProperties();
		} catch (AuthenticationException e) {
			LOGGER.warn("Aurora: could not fetch account properties: {}", e.toString());
			properties = UserApiService.OFFLINE_PROPERTIES;
		}
		ProfileResult profile = null;
		if (online) {
			try {
				profile = mc.services().sessionService().fetchProfile(user.getProfileId(), true);
			} catch (RuntimeException e) {
				LOGGER.warn("Aurora: could not fetch the profile of {}: {}", user.getName(), e.toString());
			}
		}
		return new Prepared(account, user, online, discovery, api, properties, profile);
	}

	/** Gets a fresh Minecraft token from the saved refresh token when the current one runs out. */
	private static void renewIfNeeded(Account account) {
		if (!account.accessToken.isEmpty() && account.expiresAt - System.currentTimeMillis() > REFRESH_MARGIN) {
			return;
		}
		try {
			if (clientId.isEmpty() || account.refreshToken.isEmpty()) {
				throw new MicrosoftAuth.AuthException("aurora.accounts.error.relogin", "no refresh token");
			}
			MicrosoftAuth.Tokens tokens = MicrosoftAuth.refresh(clientId, account.refreshToken);
			MicrosoftAuth.Session session = MicrosoftAuth.minecraft(tokens.accessToken());
			account.refreshToken = tokens.refreshToken();
			account.accessToken = session.accessToken();
			account.expiresAt = session.expiresAt();
			account.name = session.name();
			save();
		} catch (MicrosoftAuth.AuthException e) {
			throw new java.util.concurrent.CompletionException(e);
		}
	}

	/** Swaps the session and every service built from it. Must run on the render thread. */
	private static void apply(Minecraft mc, Prepared p) {
		MinecraftAccountAccessor game = (MinecraftAccountAccessor) mc;
		try {
			mc.getPlayerSocialManager().getPresenceHandler().sendOfflinePresence();
		} catch (RuntimeException ignored) {
			// Best effort: the old session may already be invalid.
		}
		try {
			game.aurora$friendHandler().close();
			game.aurora$telemetry().close();
		} catch (RuntimeException e) {
			LOGGER.warn("Aurora: closing the old session's services failed: {}", e.toString());
		}
		game.aurora$setUser(p.user());
		game.aurora$setUserApiService(p.api());
		game.aurora$setUserPropertiesFuture(CompletableFuture.completedFuture(p.properties()));
		game.aurora$setProfileFuture(CompletableFuture.completedFuture(p.profile()));
		FriendsService friends = p.discovery().createFriendsService(p.user().getAccessToken());
		RemoteFriendListUpdateHandler friendUpdates = new RemoteFriendListUpdateHandler(friends, mc);
		game.aurora$setFriendHandler(friendUpdates);
		PlayerSocialManager social = new PlayerSocialManager(mc, p.api(), friends, friendUpdates);
		game.aurora$setPlayerSocialManager(social);
		if (social.isFriendListEnabled()) {
			friendUpdates.start();
		}
		game.aurora$setTelemetry(new ClientTelemetryManager(mc, p.api(), p.user()));
		game.aurora$setProfileKeyPairManager(p.online()
			? ProfileKeyPairManager.create(p.api(), p.user(), mc.gameDirectory.toPath())
			: ProfileKeyPairManager.EMPTY_KEY_MANAGER);
		game.aurora$setReportingContext(ReportingContext.create(ReportEnvironment.local(), p.api()));
		RealmsClientAccessor.aurora$setInstance(null);
		LOGGER.info("Aurora: switched account to {}", p.user().getName());
	}

	// ======================================================================= storage

	private static void load() {
		SAVED.clear();
		if (!Files.isRegularFile(FILE)) {
			return;
		}
		try {
			JsonElement root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
			if (!root.isJsonObject()) {
				return;
			}
			JsonObject json = root.getAsJsonObject();
			if (json.has("client_id")) {
				clientId = json.get("client_id").getAsString();
			}
			if (json.has("selected")) {
				selected = json.get("selected").getAsString();
			}
			if (json.has("accounts") && json.get("accounts").isJsonArray()) {
				for (JsonElement e : json.getAsJsonArray("accounts")) {
					if (!e.isJsonObject()) {
						continue;
					}
					JsonObject o = e.getAsJsonObject();
					Account.Type type = "microsoft".equals(o.get("type").getAsString()) ? Account.Type.MICROSOFT : Account.Type.OFFLINE;
					String name = o.get("name").getAsString();
					UUID uuid = UUID.fromString(o.get("uuid").getAsString());
					Account account = new Account(type, name, uuid, null, type == Account.Type.MICROSOFT);
					if (type == Account.Type.MICROSOFT) {
						account.refreshToken = Secrets.reveal(o.has("refresh") ? o.get("refresh").getAsString() : "");
						account.accessToken = Secrets.reveal(o.has("token") ? o.get("token").getAsString() : "");
						account.expiresAt = o.has("expires") ? o.get("expires").getAsLong() : 0;
					}
					SAVED.add(account);
				}
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Aurora: could not read {}", FILE, e);
		}
	}

	private static void save() {
		JsonObject json = new JsonObject();
		json.addProperty("client_id", clientId);
		json.addProperty("selected", selected);
		json.addProperty("tokens", Secrets.encrypted() ? "encrypted with Windows DPAPI" : "encoded");
		JsonArray list = new JsonArray();
		for (Account account : SAVED) {
			JsonObject o = new JsonObject();
			o.addProperty("type", account.type == Account.Type.MICROSOFT ? "microsoft" : "offline");
			o.addProperty("name", account.name);
			o.addProperty("uuid", account.uuid.toString());
			if (account.type == Account.Type.MICROSOFT) {
				o.addProperty("refresh", Secrets.protect(account.refreshToken));
				o.addProperty("token", Secrets.protect(account.accessToken));
				o.addProperty("expires", account.expiresAt);
			}
			list.add(o);
		}
		json.add("accounts", list);
		try {
			Files.createDirectories(FILE.getParent());
			Path tmp = FILE.resolveSibling("accounts.json.tmp");
			Files.writeString(tmp, GSON.toJson(json), StandardCharsets.UTF_8);
			try {
				Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------"));
			} catch (UnsupportedOperationException | IOException ignored) {
				// Not a POSIX file system (Windows): the tokens are encrypted instead.
			}
			Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOGGER.error("Aurora: could not save {}", FILE, e);
		}
	}
}
