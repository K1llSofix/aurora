package dev.aurora.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A small Discord Rich Presence client. It talks to the Discord app on this computer over its
 * local IPC pipe, so no SDK or native library is needed, runs on its own thread, reconnects when
 * Discord is started later or restarted, and sends an activity only when it changed.
 */
public final class DiscordIpc {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int HANDSHAKE = 0, FRAME = 1, CLOSE = 2, PING = 3, PONG = 4;
	/** Discord takes at most 5 activity updates per 20 seconds. */
	private static final long MIN_INTERVAL_MS = 4500;
	private static final long RETRY_MS = 15_000;
	/** Sent again now and then, which also notices a Discord that was restarted meanwhile. */
	private static final long REFRESH_MS = 60_000;

	private final String clientId;
	private final Object lock = new Object();
	// Guarded by lock.
	private @Nullable JsonObject wanted;
	private boolean running;
	private @Nullable Thread thread;

	public DiscordIpc(String clientId) {
		this.clientId = clientId;
	}

	/** Connects (now or whenever Discord shows up) and keeps the activity up to date. */
	public void start() {
		synchronized (lock) {
			if (running) {
				return;
			}
			running = true;
			if (thread == null) {
				thread = new Thread(this::run, "Aurora Discord");
				thread.setDaemon(true);
				thread.start();
			}
			lock.notifyAll();
		}
	}

	/** Clears the activity and lets go of Discord. */
	public void stop() {
		synchronized (lock) {
			running = false;
			lock.notifyAll();
		}
	}

	/** What the profile should show; null clears it. Cheap enough to call every second. */
	public void update(@Nullable JsonObject activity) {
		synchronized (lock) {
			if (Objects.equals(activity, wanted)) {
				return;
			}
			wanted = activity == null ? null : activity.deepCopy();
			lock.notifyAll();
		}
	}

	// ======================================================================= the thread

	private void run() {
		@Nullable Pipe pipe = null;
		@Nullable JsonObject sent = null;
		boolean anythingSent = false;
		long lastSend = 0;
		long nextAttempt = 0;
		while (true) {
			try {
				boolean want;
				JsonObject target;
				synchronized (lock) {
					want = running;
					target = wanted;
				}
				long now = System.currentTimeMillis();
				if (!want) {
					if (pipe != null) {
						if (anythingSent && sent != null) {
							setActivity(pipe, null);
						}
						close(pipe);
						pipe = null;
					}
					anythingSent = false;
					sent = null;
					pause(0);
					continue;
				}
				if (pipe == null) {
					if (now < nextAttempt) {
						pause(nextAttempt - now);
						continue;
					}
					pipe = connect();
					anythingSent = false;
					if (pipe == null) {
						nextAttempt = now + RETRY_MS;
						continue;
					}
				}
				boolean changed = !anythingSent || !Objects.equals(target, sent);
				if (changed || now - lastSend >= REFRESH_MS) {
					long wait = lastSend + MIN_INTERVAL_MS - now;
					if (wait > 0) {
						pause(wait);
						continue;
					}
					setActivity(pipe, target);
					sent = target;
					anythingSent = true;
					lastSend = System.currentTimeMillis();
					continue;
				}
				pause(lastSend + REFRESH_MS - now);
			} catch (IOException e) {
				// Discord was closed or restarted: try again in a while.
				close(pipe);
				pipe = null;
				nextAttempt = System.currentTimeMillis() + RETRY_MS;
			} catch (InterruptedException e) {
				close(pipe);
				return;
			}
		}
	}

	/** Waits until something changes, at most {@code millis} (0: no limit). */
	private void pause(long millis) throws InterruptedException {
		synchronized (lock) {
			lock.wait(Math.max(0, millis));
		}
	}

	private @Nullable Pipe connect() throws InterruptedException {
		for (int i = 0; i < 10; i++) {
			Pipe pipe = Pipe.open("discord-ipc-" + i);
			if (pipe == null) {
				continue;
			}
			try {
				JsonObject hello = new JsonObject();
				hello.addProperty("v", 1);
				hello.addProperty("client_id", clientId);
				pipe.write(HANDSHAKE, hello);
				Frame frame = pipe.read();
				if (frame.op == FRAME && "READY".equals(text(frame.json, "evt"))) {
					return pipe;
				}
				// Usually an unknown application id: other pipes would answer the same.
				LOGGER.warn("Discord refused Rich Presence: {}", frame.json);
				close(pipe);
				return null;
			} catch (IOException e) {
				close(pipe);
			}
		}
		return null;
	}

	private void setActivity(Pipe pipe, @Nullable JsonObject activity) throws IOException {
		JsonObject args = new JsonObject();
		args.addProperty("pid", ProcessHandle.current().pid());
		args.add("activity", activity == null ? JsonNull.INSTANCE : activity);
		JsonObject command = new JsonObject();
		command.addProperty("cmd", "SET_ACTIVITY");
		command.add("args", args);
		String nonce = UUID.randomUUID().toString();
		command.addProperty("nonce", nonce);
		pipe.write(FRAME, command);
		// Read up to the answer to this command, answering pings on the way.
		while (true) {
			Frame frame = pipe.read();
			if (frame.op == PING) {
				pipe.write(PONG, frame.json);
			} else if (frame.op == CLOSE) {
				throw new EOFException("Discord closed the connection: " + frame.json);
			} else if (frame.op == FRAME && nonce.equals(text(frame.json, "nonce"))) {
				if ("ERROR".equals(text(frame.json, "evt"))) {
					LOGGER.warn("Discord did not take the activity: {}", frame.json);
				}
				return;
			}
		}
	}

	private static @Nullable String text(JsonObject json, String key) {
		JsonElement e = json.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	private static void close(@Nullable Pipe pipe) {
		if (pipe != null) {
			try {
				pipe.close();
			} catch (IOException ignored) {
				// Already gone.
			}
		}
	}

	// ======================================================================= the pipe

	private record Frame(int op, JsonObject json) {
	}

	/** Discord's IPC: a named pipe on Windows, a Unix socket elsewhere. */
	private abstract static class Pipe implements Closeable {
		abstract void writeBytes(byte[] bytes) throws IOException;

		abstract void readFully(byte[] bytes) throws IOException;

		void write(int op, JsonObject json) throws IOException {
			byte[] payload = json.toString().getBytes(StandardCharsets.UTF_8);
			ByteBuffer frame = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
			frame.putInt(op).putInt(payload.length).put(payload);
			writeBytes(frame.array());
		}

		Frame read() throws IOException {
			byte[] header = new byte[8];
			readFully(header);
			ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
			int op = h.getInt();
			int length = h.getInt();
			if (length < 0 || length > 1 << 20) {
				throw new IOException("Bad Discord frame length " + length);
			}
			byte[] payload = new byte[length];
			readFully(payload);
			JsonElement json = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8));
			return new Frame(op, json.isJsonObject() ? json.getAsJsonObject() : new JsonObject());
		}

		static @Nullable Pipe open(String name) {
			if (Util.getPlatform() == Util.OS.WINDOWS) {
				try {
					RandomAccessFile file = new RandomAccessFile("\\\\.\\pipe\\" + name, "rw");
					return new Pipe() {
						@Override
						void writeBytes(byte[] bytes) throws IOException {
							file.write(bytes);
						}

						@Override
						void readFully(byte[] bytes) throws IOException {
							file.readFully(bytes);
						}

						@Override
						public void close() throws IOException {
							file.close();
						}
					};
				} catch (IOException e) {
					// No Discord listening on this pipe.
					return null;
				}
			}
			for (Path path : unixCandidates(name)) {
				if (!Files.exists(path)) {
					continue;
				}
				try {
					SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(path));
					return new Pipe() {
						@Override
						void writeBytes(byte[] bytes) throws IOException {
							ByteBuffer buffer = ByteBuffer.wrap(bytes);
							while (buffer.hasRemaining()) {
								channel.write(buffer);
							}
						}

						@Override
						void readFully(byte[] bytes) throws IOException {
							ByteBuffer buffer = ByteBuffer.wrap(bytes);
							while (buffer.hasRemaining()) {
								if (channel.read(buffer) < 0) {
									throw new EOFException();
								}
							}
						}

						@Override
						public void close() throws IOException {
							channel.close();
						}
					};
				} catch (IOException ignored) {
					// Try the next place.
				}
			}
			return null;
		}

		/** Where Discord (also from Flatpak or Snap) puts its socket on Linux and macOS. */
		private static List<Path> unixCandidates(String name) {
			List<Path> out = new ArrayList<>();
			for (String variable : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
				String dir = System.getenv(variable);
				if (dir != null && !dir.isEmpty()) {
					out.add(Path.of(dir, name));
					out.add(Path.of(dir, "app", "com.discordapp.Discord", name));
					out.add(Path.of(dir, "snap.discord", name));
				}
			}
			out.add(Path.of("/tmp", name));
			return out;
		}
	}
}
