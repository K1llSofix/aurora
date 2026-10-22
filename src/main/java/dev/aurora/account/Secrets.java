package dev.aurora.account;

import com.mojang.logging.LogUtils;
import com.sun.jna.platform.win32.Crypt32Util;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Protects saved account tokens. On Windows they are encrypted with DPAPI, so only the same Windows
 * user on the same PC can read them back; elsewhere they are only encoded, and the accounts file is
 * made readable by its owner alone.
 */
final class Secrets {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

	private Secrets() {
	}

	static boolean encrypted() {
		return WINDOWS;
	}

	static String protect(String plain) {
		if (plain == null || plain.isEmpty()) {
			return "";
		}
		byte[] bytes = plain.getBytes(StandardCharsets.UTF_8);
		if (WINDOWS) {
			try {
				return "dpapi:" + Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(bytes));
			} catch (Throwable e) {
				LOGGER.warn("Aurora could not encrypt an account token: {}", e.toString());
			}
		}
		return "plain:" + Base64.getEncoder().encodeToString(bytes);
	}

	static String reveal(String stored) {
		if (stored == null || stored.isEmpty()) {
			return "";
		}
		try {
			if (stored.startsWith("dpapi:")) {
				return new String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(stored.substring(6))), StandardCharsets.UTF_8);
			}
			if (stored.startsWith("plain:")) {
				return new String(Base64.getDecoder().decode(stored.substring(6)), StandardCharsets.UTF_8);
			}
		} catch (Throwable e) {
			LOGGER.warn("Aurora could not read a saved account token: {}", e.toString());
		}
		return "";
	}
}
