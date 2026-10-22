package dev.aurora.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Puts a picture on the Windows clipboard as a bitmap, so it can be pasted into Discord, Telegram
 * or Paint. The game runs with AWT switched off, so this talks to user32 directly through Java's
 * foreign function API: no helper process, no console window flashing up.
 */
public final class WinClipboard {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int CF_DIB = 8;
	private static final int GMEM_MOVEABLE = 0x0002;
	private static final int HEADER = 40;

	private static @Nullable MethodHandle openClipboard;
	private static @Nullable MethodHandle emptyClipboard;
	private static @Nullable MethodHandle setClipboardData;
	private static @Nullable MethodHandle closeClipboard;
	private static @Nullable MethodHandle globalAlloc;
	private static @Nullable MethodHandle globalLock;
	private static @Nullable MethodHandle globalUnlock;
	private static @Nullable MethodHandle globalFree;
	private static boolean tried;

	private WinClipboard() {
	}

	public static boolean supported() {
		return Util.getPlatform() == Util.OS.WINDOWS;
	}

	private static synchronized boolean init() {
		if (tried) {
			return openClipboard != null;
		}
		tried = true;
		if (!supported()) {
			return false;
		}
		try {
			Linker linker = Linker.nativeLinker();
			SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
			SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
			emptyClipboard = linker.downcallHandle(user32.findOrThrow("EmptyClipboard"), FunctionDescriptor.of(JAVA_INT));
			setClipboardData = linker.downcallHandle(user32.findOrThrow("SetClipboardData"), FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS));
			closeClipboard = linker.downcallHandle(user32.findOrThrow("CloseClipboard"), FunctionDescriptor.of(JAVA_INT));
			globalAlloc = linker.downcallHandle(kernel32.findOrThrow("GlobalAlloc"), FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_LONG));
			globalLock = linker.downcallHandle(kernel32.findOrThrow("GlobalLock"), FunctionDescriptor.of(ADDRESS, ADDRESS));
			globalUnlock = linker.downcallHandle(kernel32.findOrThrow("GlobalUnlock"), FunctionDescriptor.of(JAVA_INT, ADDRESS));
			globalFree = linker.downcallHandle(kernel32.findOrThrow("GlobalFree"), FunctionDescriptor.of(ADDRESS, ADDRESS));
			// Last, so a half-finished setup counts as unavailable.
			openClipboard = linker.downcallHandle(user32.findOrThrow("OpenClipboard"), FunctionDescriptor.of(JAVA_INT, ADDRESS));
			return true;
		} catch (Throwable t) {
			LOGGER.warn("Clipboard pictures are not available", t);
			return false;
		}
	}

	/**
	 * The picture as a device independent bitmap (24 bits, rows bottom up), the format every
	 * Windows program can paste. Slow for big pictures, so call it off the render thread.
	 */
	public static byte[] bitmap(NativeImage image) {
		int w = image.getWidth();
		int h = image.getHeight();
		int stride = (w * 3 + 3) & ~3;
		byte[] out = new byte[HEADER + stride * h];
		putInt(out, 0, HEADER);
		putInt(out, 4, w);
		putInt(out, 8, h);
		out[12] = 1;
		out[14] = 24;
		putInt(out, 20, stride * h);
		for (int y = 0; y < h; y++) {
			int row = HEADER + (h - 1 - y) * stride;
			for (int x = 0; x < w; x++) {
				int argb = image.getPixel(x, y);
				int i = row + x * 3;
				out[i] = (byte) argb;
				out[i + 1] = (byte) (argb >> 8);
				out[i + 2] = (byte) (argb >> 16);
			}
		}
		return out;
	}

	private static void putInt(byte[] b, int at, int v) {
		b[at] = (byte) v;
		b[at + 1] = (byte) (v >> 8);
		b[at + 2] = (byte) (v >> 16);
		b[at + 3] = (byte) (v >> 24);
	}

	/**
	 * Replaces the clipboard with the bitmap. {@code window} is the game's native window, which
	 * Windows needs as the clipboard's owner.
	 */
	public static boolean put(long window, byte[] bitmap) {
		if (!init() || window == 0) {
			return false;
		}
		try {
			MemorySegment memory = (MemorySegment) globalAlloc.invokeExact(GMEM_MOVEABLE, (long) bitmap.length);
			if (memory.equals(MemorySegment.NULL)) {
				return false;
			}
			MemorySegment target = (MemorySegment) globalLock.invokeExact(memory);
			if (target.equals(MemorySegment.NULL)) {
				MemorySegment ignored = (MemorySegment) globalFree.invokeExact(memory);
				return false;
			}
			MemorySegment.copy(MemorySegment.ofArray(bitmap), 0, target.reinterpret(bitmap.length), 0, bitmap.length);
			int unlocked = (int) globalUnlock.invokeExact(memory);
			// Another program may hold the clipboard for a moment.
			boolean open = false;
			for (int attempt = 0; attempt < 5 && !open; attempt++) {
				open = (int) openClipboard.invokeExact(MemorySegment.ofAddress(window)) != 0;
				if (!open) {
					Thread.sleep(8);
				}
			}
			if (!open) {
				MemorySegment ignored = (MemorySegment) globalFree.invokeExact(memory);
				return false;
			}
			try {
				int emptied = (int) emptyClipboard.invokeExact();
				MemorySegment handle = (MemorySegment) setClipboardData.invokeExact(CF_DIB, memory);
				if (handle.equals(MemorySegment.NULL)) {
					MemorySegment ignored = (MemorySegment) globalFree.invokeExact(memory);
					return false;
				}
				// From here on the memory belongs to the clipboard.
				return true;
			} finally {
				int closed = (int) closeClipboard.invokeExact();
			}
		} catch (Throwable t) {
			LOGGER.warn("Could not copy the picture", t);
			return false;
		}
	}
}
