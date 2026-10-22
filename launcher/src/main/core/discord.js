'use strict';

// Discord Rich Presence over Discord's local IPC pipe: no SDK, no native module. Connects when
// Discord is running (and again whenever it is restarted) and sends the activity only when it
// changed, never faster than Discord allows.

const net = require('net');
const path = require('path');
const { randomUUID } = require('crypto');

const OP = { HANDSHAKE: 0, FRAME: 1, CLOSE: 2, PING: 3, PONG: 4 };
const MIN_INTERVAL = 4500; // Discord takes 5 updates per 20 s
const RETRY = 15000;

function pipePaths(index) {
  const name = `discord-ipc-${index}`;
  if (process.platform === 'win32') return [`\\\\?\\pipe\\${name}`];
  const dirs = [process.env.XDG_RUNTIME_DIR, process.env.TMPDIR, process.env.TMP, process.env.TEMP].filter(Boolean);
  const out = [];
  for (const dir of dirs) out.push(path.join(dir, name), path.join(dir, 'app', 'com.discordapp.Discord', name), path.join(dir, 'snap.discord', name));
  out.push(path.join('/tmp', name));
  return out;
}

function encode(op, payload) {
  const body = Buffer.from(JSON.stringify(payload), 'utf8');
  const header = Buffer.alloc(8);
  header.writeInt32LE(op, 0);
  header.writeInt32LE(body.length, 4);
  return Buffer.concat([header, body]);
}

/** Opens the first pipe Discord listens on and finishes the handshake; resolves to the socket. */
function connect(clientId) {
  const candidates = [];
  for (let i = 0; i < 10; i++) candidates.push(...pipePaths(i));
  return new Promise((resolve, reject) => {
    const tryNext = () => {
      const target = candidates.shift();
      if (!target) return reject(new Error('Discord is not running'));
      const socket = net.createConnection(target);
      socket.once('error', () => {
        socket.destroy();
        tryNext();
      });
      socket.once('connect', () => {
        socket.removeAllListeners('error');
        resolve(socket);
        socket.write(encode(OP.HANDSHAKE, { v: 1, client_id: clientId }));
      });
    };
    tryNext();
  });
}

class DiscordPresence {
  constructor(clientId, log = () => {}) {
    this.clientId = clientId;
    this.log = log;
    this.enabled = false;
    this.socket = null;
    this.ready = false;
    this.buffer = Buffer.alloc(0);
    this.wanted = null;
    this.sent = undefined;
    this.lastSend = 0;
    this.timer = null;
    this.retryAt = 0;
  }

  /** Turns presence on or off; off clears it and closes the pipe. */
  setEnabled(on) {
    on = Boolean(on) && Boolean(this.clientId);
    if (on === this.enabled) return;
    this.enabled = on;
    if (on) {
      this.retryAt = 0;
      this.schedule(0);
    } else {
      if (this.ready && this.sent) this.send(null);
      this.close();
    }
  }

  /** What the profile shows; null clears it. */
  set(activity) {
    this.wanted = activity || null;
    if (this.enabled) this.schedule(0);
  }

  schedule(delay) {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.pump().catch(() => {}), Math.max(0, delay));
  }

  async pump() {
    if (!this.enabled) return;
    if (!this.socket) {
      const now = Date.now();
      if (now < this.retryAt) return this.schedule(this.retryAt - now);
      this.retryAt = now + RETRY;
      try {
        this.attach(await connect(this.clientId));
      } catch {
        // Not running yet: look again later.
        this.schedule(RETRY);
      }
      return;
    }
    if (!this.ready) return; // the READY event will call pump again
    if (JSON.stringify(this.wanted) === JSON.stringify(this.sent)) return;
    const wait = this.lastSend + MIN_INTERVAL - Date.now();
    if (wait > 0) return this.schedule(wait);
    this.send(this.wanted);
  }

  send(activity) {
    this.lastSend = Date.now();
    this.sent = activity;
    this.socket.write(encode(OP.FRAME, {
      cmd: 'SET_ACTIVITY',
      args: { pid: process.pid, activity },
      nonce: randomUUID(),
    }));
  }

  attach(socket) {
    this.socket = socket;
    this.ready = false;
    this.buffer = Buffer.alloc(0);
    this.sent = undefined;
    socket.on('data', (chunk) => this.receive(chunk));
    socket.on('close', () => this.lost());
    socket.on('error', () => this.lost());
  }

  receive(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    while (this.buffer.length >= 8) {
      const op = this.buffer.readInt32LE(0);
      const length = this.buffer.readInt32LE(4);
      if (this.buffer.length < 8 + length) return;
      let message = {};
      try {
        message = JSON.parse(this.buffer.subarray(8, 8 + length).toString('utf8'));
      } catch {
        // Ignore what cannot be read.
      }
      this.buffer = this.buffer.subarray(8 + length);
      if (op === OP.PING) {
        this.socket.write(encode(OP.PONG, message));
      } else if (op === OP.CLOSE) {
        this.log(`discord: closed by Discord: ${JSON.stringify(message)}`);
        this.lost();
        return;
      } else if (message.evt === 'READY') {
        this.ready = true;
        this.schedule(0);
      } else if (message.evt === 'ERROR') {
        this.log(`discord: ${JSON.stringify(message.data)}`);
      }
    }
  }

  lost() {
    if (!this.socket) return;
    this.close();
    if (this.enabled) this.schedule(RETRY);
  }

  close() {
    const socket = this.socket;
    this.socket = null;
    this.ready = false;
    this.sent = undefined;
    if (socket) {
      socket.removeAllListeners();
      socket.on('error', () => {});
      socket.destroy();
    }
  }
}

module.exports = { DiscordPresence };
