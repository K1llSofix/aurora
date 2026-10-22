package dev.aurora.util;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Key labels that do not depend on the keyboard layout. SDL names a key after the character the
 * current layout produces, so on a Russian layout J shows up as "О"; gamers name keys by their
 * Latin letter, which is also what guides and servers use.
 */
public final class KeyNames {
	private KeyNames() {
	}

	public static String of(InputConstants.Key key) {
		if (key.getType() == InputConstants.Type.KEYBOARD) {
			int code = key.getValue();
			if (code >= InputConstants.KEY_A && code <= InputConstants.KEY_Z) {
				return String.valueOf((char) ('A' + code - InputConstants.KEY_A));
			}
			if (code >= InputConstants.KEY_1 && code <= InputConstants.KEY_9) {
				return String.valueOf((char) ('1' + code - InputConstants.KEY_1));
			}
			if (code == InputConstants.KEY_0) {
				return "0";
			}
		}
		return key.getDisplayName().getString();
	}

	public static String ofKeyboard(int scancode) {
		return of(InputConstants.Type.KEYBOARD.getOrCreate(scancode));
	}
}
