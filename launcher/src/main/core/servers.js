'use strict';

const netSocket = require('net');
const dns = require('dns').promises;

// Any recent protocol number works for a status request; servers answer with their own range.
const PROTOCOL = 773;
const CACHE_MS = 45 * 1000;

function varint(n) {
  const out = [];
  let v = n >>> 0;
  do {
    let b = v & 0x7f;
    v >>>= 7;
    if (v) b |= 0x80;
    out.push(b);
  } while (v);
  return Buffer.from(out);
}

function string(s) {
  const b = Buffer.from(s, 'utf8');
  return Buffer.concat([varint(b.length), b]);
}

function packet(id, ...parts) {
  const body = Buffer.concat([varint(id), ...parts]);
  return Buffer.concat([varint(body.length), body]);
}

function readVarint(buf, off) {
  let n = 0;
  let shift = 0;
  let b;
  do {
    if (off >= buf.length || shift > 28) return null;
    b = buf[off++];
    n |= (b & 0x7f) << shift;
    shift += 7;
  } while (b & 0x80);
  return [n, off];
}

/** "host", "host:port" or "[v6]:port" → {host, port|null}. */
function parseAddress(address) {
  const a = String(address || '').trim();
  const v6 = a.match(/^\[([^\]]+)\](?::(\d+))?$/);
  if (v6) return { host: v6[1], port: v6[2] ? +v6[2] : null };
  const i = a.lastIndexOf(':');
  if (i > 0 && a.indexOf(':') === i && /^\d+$/.test(a.slice(i + 1))) return { host: a.slice(0, i), port: +a.slice(i + 1) };
  return { host: a, port: null };
}

async function resolve(host, port) {
  if (port) return { host, port };
  try {
    const srv = await dns.resolveSrv(`_minecraft._tcp.${host}`);
    if (srv.length) return { host: srv[0].name, port: srv[0].port };
  } catch {
    // No SRV record: the default port.
  }
  return { host, port: 25565 };
}

/**
 * Server List Ping: the same status request the multiplayer screen sends. Resolves to the
 * player count, version, MOTD (a chat component or legacy string), favicon and latency.
 */
async function ping(address, { timeout = 6000 } = {}) {
  const parsed = parseAddress(address);
  if (!parsed.host) throw new Error('empty address');
  const target = await resolve(parsed.host, parsed.port);
  return new Promise((resolvePing, reject) => {
    const sock = netSocket.connect({ host: target.host, port: target.port });
    let data = Buffer.alloc(0);
    let status = null;
    let pingSent = 0;
    const timer = setTimeout(() => finish(new Error('timeout')), timeout);
    function finish(err, result) {
      clearTimeout(timer);
      sock.destroy();
      if (err) reject(err);
      else resolvePing(result);
    }
    sock.setNoDelay(true);
    sock.on('connect', () => {
      const port = Buffer.alloc(2);
      port.writeUInt16BE(target.port);
      sock.write(packet(0x00, varint(PROTOCOL), string(parsed.host), port, varint(1)));
      sock.write(packet(0x00));
    });
    sock.on('data', (chunk) => {
      data = Buffer.concat([data, chunk]);
      for (;;) {
        const len = readVarint(data, 0);
        if (!len || data.length < len[1] + len[0]) return;
        const body = data.subarray(len[1], len[1] + len[0]);
        data = data.subarray(len[1] + len[0]);
        const id = readVarint(body, 0);
        if (!id) return finish(new Error('bad packet'));
        if (id[0] === 0x00 && !status) {
          const sl = readVarint(body, id[1]);
          if (!sl) return finish(new Error('bad status'));
          try {
            status = JSON.parse(body.subarray(sl[1], sl[1] + sl[0]).toString('utf8'));
          } catch (e) {
            return finish(new Error('bad status json'));
          }
          // Measure the round trip with the ping packet, like the multiplayer screen.
          const payload = Buffer.alloc(8);
          payload.writeBigInt64BE(BigInt(Date.now()));
          pingSent = Date.now();
          sock.write(packet(0x01, payload));
        } else if (id[0] === 0x01 && status) {
          return finish(null, shape(status, Date.now() - pingSent));
        }
      }
    });
    sock.on('error', (e) => finish(e));
    sock.on('close', () => {
      // Some servers hang up after the status instead of answering the ping.
      if (status) finish(null, shape(status, pingSent ? Date.now() - pingSent : null));
      else finish(new Error('closed'));
    });
  });
}

function shape(status, latency) {
  return {
    online: true,
    latency,
    players: status.players ? { online: status.players.online | 0, max: status.players.max | 0 } : null,
    version: status.version ? { name: String(status.version.name || ''), protocol: status.version.protocol } : null,
    motd: status.description ?? '',
    favicon: typeof status.favicon === 'string' && status.favicon.startsWith('data:image/') ? status.favicon : null,
  };
}

/** Pings with a short cache so switching pages does not flood servers. */
class ServerPinger {
  constructor() {
    this.cache = new Map();
  }

  async status(address, force = false) {
    const key = address.toLowerCase();
    const hit = this.cache.get(key);
    if (!force && hit && Date.now() - hit.at < CACHE_MS) return hit.promise;
    const promise = ping(address).catch((e) => ({ online: false, error: e.code || e.message }));
    this.cache.set(key, { at: Date.now(), promise });
    return promise;
  }
}

module.exports = { ping, ServerPinger, parseAddress };
