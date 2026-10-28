'use strict';

// Singleplayer worlds of an installation: saves/<folder>/level.dat holds the name, the last time
// it was played, the game mode and the version. Backups are zip files in <game>/backups named the
// way the game's own "Make Backup" names them, so both kinds show up together.

const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const zlib = require('zlib');
const { ZipWriter, walk } = require('./zipwrite');
const { ZipFile } = require('./zip');
const { promisify } = require('util');

const inflateRaw = promisify(zlib.inflateRaw);

const ICON_LIMIT = 256 * 1024;
const ENTRY_LIMIT = 1024 * 1024 * 1024;

// ---------------------------------------------------------------------------- NBT

/** Reads a (decompressed) NBT document: the root compound as a plain object. */
function readNbt(buf) {
  let p = 0;
  const u8 = () => buf.readUInt8(p++);
  const i16 = () => { const v = buf.readInt16BE(p); p += 2; return v; };
  const i32 = () => { const v = buf.readInt32BE(p); p += 4; return v; };
  const i64 = () => { const v = Number(buf.readBigInt64BE(p)); p += 8; return v; };
  const str = () => { const n = buf.readUInt16BE(p); p += 2; const s = buf.toString('utf8', p, p + n); p += n; return s; };
  const payload = (type) => {
    switch (type) {
      case 1: return buf.readInt8(p++);
      case 2: return i16();
      case 3: return i32();
      case 4: return i64();
      case 5: { const v = buf.readFloatBE(p); p += 4; return v; }
      case 6: { const v = buf.readDoubleBE(p); p += 8; return v; }
      case 7: { const n = i32(); p += n; return null; }
      case 8: return str();
      case 9: {
        const inner = u8();
        const n = i32();
        const list = [];
        for (let i = 0; i < n; i++) list.push(payload(inner));
        return list;
      }
      case 10: {
        const out = {};
        for (;;) {
          const t = u8();
          if (t === 0) return out;
          const name = str();
          out[name] = payload(t);
        }
      }
      case 11: { const n = i32(); p += n * 4; return null; }
      case 12: { const n = i32(); p += n * 8; return null; }
      default: throw new Error(`Bad NBT tag ${type}`);
    }
  };
  if (u8() !== 10) throw new Error('NBT does not start with a compound');
  str();
  return payload(10);
}

function readLevel(file) {
  let data = fs.readFileSync(file);
  if (data[0] === 0x1f && data[1] === 0x8b) data = zlib.gunzipSync(data);
  const root = readNbt(data);
  return root.Data || root;
}

const MODES = ['survival', 'creative', 'adventure', 'spectator'];

// ---------------------------------------------------------------------------- worlds

function savesDir(gameDir) {
  return path.join(gameDir, 'saves');
}

function backupsDir(gameDir) {
  return path.join(gameDir, 'backups');
}

/** A folder name from the page: one plain name inside saves/, nothing that climbs out. */
function safeFolder(name) {
  const s = String(name || '');
  if (!s || s === '.' || s === '..' || /[\\/:*?"<>|]/.test(s)) throw new Error(`Bad world folder ${s}`);
  return s;
}

async function list(gameDir) {
  const dir = savesDir(gameDir);
  let names = [];
  try {
    names = await fsp.readdir(dir, { withFileTypes: true });
  } catch {
    return [];
  }
  const out = [];
  for (const entry of names) {
    if (!entry.isDirectory()) continue;
    const folder = path.join(dir, entry.name);
    const levelFile = path.join(folder, 'level.dat');
    if (!fs.existsSync(levelFile)) continue;
    let level = {};
    let broken = false;
    try {
      level = readLevel(levelFile);
    } catch {
      broken = true;
    }
    let icon = null;
    try {
      const iconFile = path.join(folder, 'icon.png');
      if ((await fsp.stat(iconFile)).size <= ICON_LIMIT) icon = `data:image/png;base64,${(await fsp.readFile(iconFile)).toString('base64')}`;
    } catch {
      icon = null;
    }
    const stat = await fsp.stat(levelFile);
    out.push({
      folder: entry.name,
      name: typeof level.LevelName === 'string' && level.LevelName ? level.LevelName : entry.name,
      lastPlayed: Number(level.LastPlayed) || stat.mtimeMs,
      mode: level.hardcore ? 'hardcore' : MODES[level.GameType] || 'survival',
      cheats: Boolean(level.allowCommands),
      version: level.Version && typeof level.Version.Name === 'string' ? level.Version.Name : null,
      icon,
      broken,
    });
  }
  out.sort((a, b) => b.lastPlayed - a.lastPlayed);
  return out;
}

/** Bytes a world takes on disk (counted on request: big worlds have many files). */
async function size(gameDir, folder) {
  const root = path.join(savesDir(gameDir), safeFolder(folder));
  let total = 0;
  for (const rel of await walk(root)) {
    try {
      total += (await fsp.stat(path.join(root, rel))).size;
    } catch {
      // Gone meanwhile.
    }
  }
  return total;
}

function stamp(date = new Date()) {
  const p = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}_${p(date.getHours())}-${p(date.getMinutes())}-${p(date.getSeconds())}`;
}

/** Packs a world into <game>/backups/<date>_<folder>.zip, like the game's "Make Backup". */
async function backup(gameDir, folder, { onProgress, signal } = {}) {
  const name = safeFolder(folder);
  const root = path.join(savesDir(gameDir), name);
  // session.lock is held open by a running game and means nothing in a copy.
  const files = await walk(root, (rel) => rel === 'session.lock');
  const target = path.join(backupsDir(gameDir), `${stamp()}_${name}.zip`);
  const zip = await new ZipWriter(target).open();
  try {
    let done = 0;
    for (const rel of files) {
      if (signal && signal.aborted) throw new Error('cancelled');
      await zip.addFile(`${name}/${rel}`, path.join(root, rel));
      done++;
      if (onProgress) onProgress({ done, total: files.length });
    }
    await zip.close();
  } catch (e) {
    await zip.discard();
    throw e;
  }
  return target;
}

/** The world backups of an installation, newest first. */
async function backups(gameDir) {
  const dir = backupsDir(gameDir);
  let names = [];
  try {
    names = await fsp.readdir(dir);
  } catch {
    return [];
  }
  const out = [];
  for (const file of names) {
    if (!/\.zip$/i.test(file)) continue;
    try {
      const stat = await fsp.stat(path.join(dir, file));
      const m = /^\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}_(.+)\.zip$/i.exec(file);
      out.push({ file, world: m ? m[1] : file.replace(/\.zip$/i, ''), created: stat.mtimeMs, size: stat.size });
    } catch {
      // Gone meanwhile.
    }
  }
  out.sort((a, b) => b.created - a.created);
  return out;
}

/** One entry's bytes, inflated off the main thread (worlds can be hundreds of megabytes). */
async function entryData(zip, entry) {
  if (entry.size > ENTRY_LIMIT) return null;
  const header = zip.read(entry.local, 30);
  if (header.readUInt32LE(0) !== 0x04034b50) return null;
  const start = entry.local + 30 + header.readUInt16LE(26) + header.readUInt16LE(28);
  const data = zip.read(start, entry.compressed);
  if (entry.method === 0) return data;
  if (entry.method === 8) return inflateRaw(data);
  return null;
}

/** A folder name in saves/ that is not taken yet: "World", "World (2)", ... */
function freeFolder(gameDir, base) {
  const dir = savesDir(gameDir);
  let name = base;
  for (let i = 2; fs.existsSync(path.join(dir, name)); i++) name = `${base} (${i})`;
  return name;
}

/**
 * Unpacks a backup as a new world next to the others; nothing that exists is overwritten. Only
 * the files of the archive's top folder are taken, and no path may leave the new folder.
 */
async function restore(gameDir, file, { label } = {}) {
  const source = path.join(backupsDir(gameDir), path.basename(String(file)));
  const zip = new ZipFile(source);
  try {
    const names = [...zip.entries.values()].filter((e) => !e.directory).map((e) => e.name);
    const tops = new Set(names.map((n) => n.split('/')[0]));
    const nested = tops.size === 1 && names.every((n) => n.includes('/'));
    const top = nested ? [...tops][0] : null;
    if (!names.some((n) => n === 'level.dat' || n.endsWith('/level.dat'))) throw new Error('No level.dat in the backup');
    const folder = freeFolder(gameDir, `${top || path.basename(source, '.zip')}${label ? ` ${label}` : ''}`);
    const root = path.join(savesDir(gameDir), folder);
    for (const name of names) {
      const rel = nested ? name.slice(top.length + 1) : name;
      if (!rel || rel.split('/').some((part) => part === '..' || part === '') || rel.includes(':')) continue;
      const target = path.resolve(root, rel);
      if (!target.startsWith(path.resolve(root) + path.sep)) continue;
      const data = await entryData(zip, zip.entries.get(name));
      if (!data) continue;
      await fsp.mkdir(path.dirname(target), { recursive: true });
      await fsp.writeFile(target, data);
    }
    return folder;
  } finally {
    zip.close();
  }
}

module.exports = { list, size, backup, backups, restore, safeFolder, savesDir, backupsDir, readNbt };
