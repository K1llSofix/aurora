'use strict';

const fs = require('fs');
const zlib = require('zlib');

/**
 * A small reader for zip archives (mod jars, native jars): it lists entries from the central
 * directory and inflates single files on demand, without unpacking the whole archive.
 */
class ZipFile {
  constructor(file) {
    this.file = file;
    this.fd = fs.openSync(file, 'r');
    this.size = fs.fstatSync(this.fd).size;
    this.entries = new Map();
    try {
      this.readDirectory();
    } catch (e) {
      this.close();
      throw e;
    }
  }

  read(position, length) {
    const buf = Buffer.alloc(length);
    let off = 0;
    while (off < length) {
      const n = fs.readSync(this.fd, buf, off, length - off, position + off);
      if (n <= 0) break;
      off += n;
    }
    return buf.subarray(0, off);
  }

  readDirectory() {
    // The end-of-central-directory record sits in the last 22 bytes plus an optional comment.
    const tailSize = Math.min(this.size, 22 + 0xffff);
    const tail = this.read(this.size - tailSize, tailSize);
    let eocd = -1;
    for (let i = tail.length - 22; i >= 0; i--) {
      if (tail.readUInt32LE(i) === 0x06054b50) {
        eocd = i;
        break;
      }
    }
    if (eocd < 0) throw new Error(`Not a zip archive: ${this.file}`);
    let count = tail.readUInt16LE(eocd + 10);
    let dirSize = tail.readUInt32LE(eocd + 12);
    let dirOffset = tail.readUInt32LE(eocd + 16);
    if (dirOffset === 0xffffffff || count === 0xffff) {
      // ZIP64: the real numbers live in the ZIP64 end record pointed to by its locator.
      const locator = eocd - 20;
      if (locator >= 0 && tail.readUInt32LE(locator) === 0x07064b50) {
        const recordPos = Number(tail.readBigUInt64LE(locator + 8));
        const record = this.read(recordPos, 56);
        count = Number(record.readBigUInt64LE(32));
        dirSize = Number(record.readBigUInt64LE(40));
        dirOffset = Number(record.readBigUInt64LE(48));
      }
    }
    const dir = this.read(dirOffset, dirSize);
    let p = 0;
    for (let i = 0; i < count && p + 46 <= dir.length; i++) {
      if (dir.readUInt32LE(p) !== 0x02014b50) break;
      const method = dir.readUInt16LE(p + 10);
      let compressed = dir.readUInt32LE(p + 20);
      let size = dir.readUInt32LE(p + 24);
      const nameLen = dir.readUInt16LE(p + 28);
      const extraLen = dir.readUInt16LE(p + 30);
      const commentLen = dir.readUInt16LE(p + 32);
      let local = dir.readUInt32LE(p + 42);
      const name = dir.toString('utf8', p + 46, p + 46 + nameLen);
      if (compressed === 0xffffffff || size === 0xffffffff || local === 0xffffffff) {
        let q = p + 46 + nameLen;
        const end = q + extraLen;
        while (q + 4 <= end) {
          const id = dir.readUInt16LE(q);
          const len = dir.readUInt16LE(q + 2);
          if (id === 0x0001) {
            let r = q + 4;
            if (size === 0xffffffff) { size = Number(dir.readBigUInt64LE(r)); r += 8; }
            if (compressed === 0xffffffff) { compressed = Number(dir.readBigUInt64LE(r)); r += 8; }
            if (local === 0xffffffff) { local = Number(dir.readBigUInt64LE(r)); }
          }
          q += 4 + len;
        }
      }
      this.entries.set(name, { name, method, compressed, size, local, directory: name.endsWith('/') });
      p += 46 + nameLen + extraLen + commentLen;
    }
  }

  has(name) {
    return this.entries.has(name);
  }

  /** The uncompressed bytes of one entry, or null if it is missing or too big. */
  get(name, maxSize = 64 * 1024 * 1024) {
    const entry = this.entries.get(name);
    if (!entry || entry.directory || entry.size > maxSize) return null;
    const header = this.read(entry.local, 30);
    if (header.readUInt32LE(0) !== 0x04034b50) return null;
    const start = entry.local + 30 + header.readUInt16LE(26) + header.readUInt16LE(28);
    const data = this.read(start, entry.compressed);
    if (entry.method === 0) return data;
    if (entry.method === 8) return zlib.inflateRawSync(data);
    return null;
  }

  text(name) {
    const data = this.get(name);
    return data ? data.toString('utf8') : null;
  }

  close() {
    if (this.fd != null) {
      fs.closeSync(this.fd);
      this.fd = null;
    }
  }
}

/** Opens an archive for the duration of {@code fn}. */
function withZip(file, fn) {
  const zip = new ZipFile(file);
  try {
    return fn(zip);
  } finally {
    zip.close();
  }
}

module.exports = { ZipFile, withZip };
