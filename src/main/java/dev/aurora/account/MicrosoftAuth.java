package dev.aurora.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.util.UndashedUuid;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Microsoft sign-in for Minecraft with the OAuth device code flow: the player opens
 * microsoft.com/link in their own browser and enters a short code, so Aurora never sees the
 * password. The Microsoft token is then exchanged through Xbox Live for a Minecraft session.
 *
 * <p>This needs the client ID of an Azure app registration that Mojang has approved for the
 * Minecraft login API.
 */
public final class MicrosoftAuth {
	private static final String AUTHORITY = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
	private static final String SCOPE = "XboxLive.signin offline_access";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	private MicrosoftAuth() {
	}

	public record DeviceCode(String deviceCode, String userCode, String verificationUri, int interval, long expiresAt) {
	}

	public record Tokens(String accessToken, String refreshToken) {
	}

	public record Session(String accessToken, long expiresAt, UUID uuid, String name) {
	}

	/** A failure with a translation key for the player and technical details for the log. */
	public static final class AuthException extends Exception {
		private final String key;

		AuthException(String key, String detail) {
			super(detail);
			this.key = key;
		}

		public String key() {
			return key;
		}
	}

	// ======================================================================= Microsoft

	public static DeviceCode deviceCode(String clientId) throws AuthException {
		JsonObject o = postForm(AUTHORITY + "devicecode", Map.of("client_id", clientId, "scope", SCOPE));
		if (!o.has("device_code")) {
			throw new AuthException("aurora.accounts.error.client_id", text(o, "error_description"));
		}
		int interval = o.has("interval") ? o.get("interval").getAsInt() : 5;
		long expires = System.currentTimeMillis() + (o.has("expires_in") ? o.get("expires_in").getAsLong() : 900) * 1000L;
		return new DeviceCode(text(o, "device_code"), text(o, "user_code"), text(o, "verification_uri"), interval, expires);
	}

	/** Waits until the player signs in, the code expires or {@code cancelled} becomes true. */
	public static Tokens awaitSignIn(String clientId, DeviceCode code, BooleanSupplier cancelled) throws AuthException {
		long interval = Math.max(1, code.interval()) * 1000L;
		while (System.currentTimeMillis() < code.expiresAt()) {
			for (long waited = 0; waited < interval; waited += 100) {
				if (cancelled.getAsBoolean()) {
					throw new AuthException("aurora.accounts.error.cancelled", "cancelled");
				}
				sleep(100);
			}
			Map<String, String> form = new LinkedHashMap<>();
			form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
			form.put("client_id", clientId);
			form.put("device_code", code.deviceCode());
			JsonObject o = postForm(AUTHORITY + "token", form);
			if (o.has("access_token")) {
				return new Tokens(text(o, "access_token"), text(o, "refresh_token"));
			}
			switch (text(o, "error")) {
				case "authorization_pending" -> {
				}
				case "slow_down" -> interval += 5000;
				case "authorization_declined" -> throw new AuthException("aurora.accounts.error.declined", text(o, "error_description"));
				case "expired_token" -> throw new AuthException("aurora.accounts.error.code_expired", text(o, "error_description"));
				default -> throw new AuthException("aurora.accounts.error.microsoft", text(o, "error_description"));
			}
		}
		throw new AuthException("aurora.accounts.error.code_expired", "device code expired");
	}

	public static Tokens refresh(String clientId, String refreshToken) throws AuthException {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("grant_type", "refresh_token");
		form.put("client_id", clientId);
		form.put("refresh_token", refreshToken);
		form.put("scope", SCOPE);
		JsonObject o = postForm(AUTHORITY + "token", form);
		if (!o.has("access_token")) {
			throw new AuthException("aurora.accounts.error.relogin", text(o, "error_description"));
		}
		String next = o.has("refresh_token") ? text(o, "refresh_token") : refreshToken;
		return new Tokens(text(o, "access_token"), next);
	}

	// ======================================================================= Xbox Live and Minecraft

	public static Session minecraft(String microsoftToken) throws AuthException {
		JsonObject properties = new JsonObject();
		properties.addProperty("AuthMethod", "RPS");
		properties.addProperty("SiteName", "user.auth.xboxlive.com");
		properties.addProperty("RpsTicket", "d=" + microsoftToken);
		JsonObject body = new JsonObject();
		body.add("Properties", properties);
		body.addProperty("RelyingParty", "http://auth.xboxlive.com");
		body.addProperty("TokenType", "JWT");
		Response xbl = postJson("https://user.auth.xboxlive.com/user/authenticate", body, null);
		if (xbl.status() / 100 != 2) {
			throw new AuthException("aurora.accounts.error.xbox", "xbox live " + xbl.status());
		}
		String xblToken = text(xbl.json(), "Token");
		String userHash = userHash(xbl.json());

		JsonObject xstsProperties = new JsonObject();
		xstsProperties.addProperty("SandboxId", "RETAIL");
		JsonArray tokens = new JsonArray();
		tokens.add(xblToken);
		xstsProperties.add("UserTokens", tokens);
		JsonObject xstsBody = new JsonObject();
		xstsBody.add("Properties", xstsProperties);
		xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
		xstsBody.addProperty("TokenType", "JWT");
		Response xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody, null);
		if (xsts.status() == 401) {
			long code = xsts.json().has("XErr") ? xsts.json().get("XErr").getAsLong() : 0;
			throw new AuthException(xboxErrorKey(code), "xsts " + code);
		}
		if (xsts.status() / 100 != 2) {
			throw new AuthException("aurora.accounts.error.xbox", "xsts " + xsts.status());
		}
		String xstsToken = text(xsts.json(), "Token");

		JsonObject login = new JsonObject();
		login.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
		Response mc = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", login, null);
		if (mc.status() == 403) {
			throw new AuthException("aurora.accounts.error.app_not_approved", mc.raw());
		}
		if (mc.status() / 100 != 2) {
			throw new AuthException("aurora.accounts.error.minecraft", "login_with_xbox " + mc.status());
		}
		String token = text(mc.json(), "access_token");
		long expires = System.currentTimeMillis() + (mc.json().has("expires_in") ? mc.json().get("expires_in").getAsLong() : 86400) * 1000L;

		Response profile = get("https://api.minecraftservices.com/minecraft/profile", token);
		if (profile.status() == 404) {
			throw new AuthException("aurora.accounts.error.no_game", "no profile");
		}
		if (profile.status() / 100 != 2) {
			throw new AuthException("aurora.accounts.error.minecraft", "profile " + profile.status());
		}
		UUID uuid = UndashedUuid.fromStringLenient(text(profile.json(), "id"));
		return new Session(token, expires, uuid, text(profile.json(), "name"));
	}

	private static String userHash(JsonObject xbl) throws AuthException {
		try {
			return xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
		} catch (RuntimeException e) {
			throw new AuthException("aurora.accounts.error.xbox", "no user hash");
		}
	}

	private static String xboxErrorKey(long code) {
		if (code == 2148916233L) {
			return "aurora.accounts.error.no_xbox";
		}
		if (code == 2148916235L) {
			return "aurora.accounts.error.region";
		}
		if (code == 2148916236L || code == 2148916237L) {
			return "aurora.accounts.error.adult";
		}
		if (code == 2148916238L) {
			return "aurora.accounts.error.child";
		}
		return "aurora.accounts.error.xbox";
	}

	// ======================================================================= HTTP

	private record Response(int status, JsonObject json, String raw) {
	}

	private static JsonObject postForm(String url, Map<String, String> form) throws AuthException {
		StringBuilder body = new StringBuilder();
		for (Map.Entry<String, String> e : form.entrySet()) {
			if (!body.isEmpty()) {
				body.append('&');
			}
			body.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=')
				.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(20))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
			.build();
		return send(request).json();
	}

	private static Response postJson(String url, JsonObject body, String bearer) throws AuthException {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(20))
			.header("Content-Type", "application/json")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
		if (bearer != null) {
			request.header("Authorization", "Bearer " + bearer);
		}
		return send(request.build());
	}

	private static Response get(String url, String bearer) throws AuthException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(20))
			.header("Accept", "application/json")
			.header("Authorization", "Bearer " + bearer)
			.GET()
			.build();
		return send(request);
	}

	private static Response send(HttpRequest request) throws AuthException {
		try {
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			String raw = response.body() == null ? "" : response.body();
			JsonObject json;
			try {
				JsonElement parsed = raw.isBlank() ? new JsonObject() : JsonParser.parseString(raw);
				json = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
			} catch (RuntimeException e) {
				json = new JsonObject();
			}
			return new Response(response.statusCode(), json, raw.length() > 300 ? raw.substring(0, 300) : raw);
		} catch (IOException e) {
			throw new AuthException("aurora.accounts.error.network", e.toString());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AuthException("aurora.accounts.error.cancelled", "interrupted");
		}
	}

	private static String text(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
