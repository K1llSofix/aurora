'use strict';

const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const zlib = require('zlib');
const { promisify } = require('util');

const deflateRaw = promisify(zlib.deflateRaw);
const LIMIT = 0xffffffff;

/** Date and time as the two 16-bit DOS fields a zip header keeps. */
function dosTime(date) {
  const d = date instanceof Date && !Number.isNaN(date.getTime()) ? date : new Date();
  const year = Math.max(1980, d.getFullYear());
  return {
    time: (d.getHours() << 11) | (d.getMinutes() << 5) | Math.floor(d.getSeconds() / 2),
    date: ((year - 1980) << 9) | ((d.getMonth() + 1) << 4) | d.getDate(),
  };
}

/**
 * Writes a zip archive entry by entry straight to disk (world backups, exported modpacks). Every
 * entry is compressed off the main thread; archives stay under the classic 4 GB / 65535-entry
 * limits, which a Minecraft world or a modpack does not reach.
 */
class ZipWriter {
  constructor(file) {
    this.file = file;
    this.handle = null;
    this.offset = 0;
    this.entries = [];
  }

  async open() {
    await fsp.mkdir(path.dirname(this.file), { recursive: true });
    this.handle = await fsp.open(this.file, 'w');
    return this;
  }

  async write(buf) {
    await this.handle.write(buf, 0, buf.length, this.offset);
    this.offset += buf.length;
  }

  /** Adds bytes under {@code name} ("folder/file.txt", forward slashes). */
  async addBuffer(name, data, { mtime } = {}) {
    if (this.entries.length >= 0xffff) throw new Error('Too many files for a zip archive');
    const nameBuf = Buffer.from(name.replace(/\\/g, '/'), 'utf8');
    const crc = zlib.crc32(data) >>> 0;
    const deflated = data.length > 64 ? await deflateRaw(data) : null;
    const stored = !deflated || deflated.length >= data.length;
    const body = stored ? data : deflated;
    if (this.offset + body.length + 30 + nameBuf.length > LIMIT || data.length > LIMIT) throw new Error('The archive would pass 4 GB');
    const { time, date } = dosTime(mtime);
    const header = Buffer.alloc(30);
    header.writeUInt32LE(0x04034b50, 0);
    header.writeUInt16LE(20, 4);
    header.writeUInt16LE(0x0800, 6); // file names in UTF-8
    header.writeUInt16LE(stored ? 0 : 8, 8);
    header.writeUInt16LE(time, 10);
    header.writeUInt16LE(date, 12);
    header.writeUInt32LE(crc, 14);
    header.writeUInt32LE(body.length, 18);
    header.writeUInt32LE(data.length, 22);
    header.writeUInt16LE(nameBuf.length, 26);
    header.writeUInt16LE(0, 28);
    const local = this.offset;
    await this.write(header);
    await this.write(nameBuf);
    await this.write(body);
    this.entries.push({ nameBuf, crc, method: stored ? 0 : 8, time, date, compressed: body.length, size: data.length, local });
  }

  async addFile(name, file) {
    const [data, stat] = await Promise.all([fsp.readFile(file), fsp.stat(file)]);
    await this.addBuffer(name, data, { mtime: stat.mtime });
  }

  async close() {
    const start = this.offset;
    for (const e of this.entries) {
      const c = Buffer.alloc(46);
      c.writeUInt32LE(0x02014b50, 0);
      c.writeUInt16LE(20, 4);
      c.writeUInt16LE(20, 6);
      c.writeUInt16LE(0x0800, 8);
      c.writeUInt16LE(e.method, 10);
      c.writeUInt16LE(e.time, 12);
      c.writeUInt16LE(e.date, 14);
      c.writeUInt32LE(e.crc, 16);
      c.writeUInt32LE(e.compressed, 20);
      c.writeUInt32LE(e.size, 24);
      c.writeUInt16LE(e.nameBuf.length, 28);
      c.writeUInt32LE(e.local, 42);
      await this.write(c);
      await this.write(e.nameBuf);
    }
    const end = Buffer.alloc(22);
    end.writeUInt32LE(0x06054b50, 0);
    end.writeUInt16LE(this.entries.length, 8);
    end.writeUInt16LE(this.entries.length, 10);
    end.writeUInt32LE(this.offset - start, 12);
    end.writeUInt32LE(start, 16);
    await this.write(end);
    await this.handle.close();
    this.handle = null;
  }

  /** Closes and deletes a half-written archive (a failed or cancelled backup). */
  async discard() {
    if (this.handle) {
      await this.handle.close().catch(() => {});
      this.handle = null;
    }
    await fsp.rm(this.file, { force: true });
  }
}

/** Every file under {@code dir}, as paths relative to it with forward slashes. */
async function walk(dir, skip = () => false, prefix = '') {
  const out = [];
  let entries = [];
  try {
    entries = await fsp.readdir(path.join(dir, prefix), { withFileTypes: true });
  } catch {
    return out;
  }
  for (const entry of entries) {
    const rel = prefix ? `${prefix}/${entry.name}` : entry.name;
    if (skip(rel, entry)) continue;
    if (entry.isDirectory()) out.push(...(await walk(dir, skip, rel)));
    else if (entry.isFile()) out.push(rel);
  }
  return out;
}

module.exports = { ZipWriter, walk };
