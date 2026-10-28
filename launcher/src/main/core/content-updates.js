'use strict';

// Updates for what the player installed: mods, resource packs and shader packs that Modrinth
// knows by the SHA-1 of their file. Two requests per check: what each file is now, and the newest
// version of it for the installation's Minecraft version (and loader). An update downloads the new
// file next to the old one, checks it, and swaps it in, switched on or off as the old one was.

const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const net = require('./net');
const { readMeta, writeMeta } = require('./mods');

const MODRINTH = 'https://api.modrinth.com/v2';

const KINDS = {
  mod: { folder: 'mods', loaders: ['fabric'], files: /\.jar(\.disabled)?$/i },
  resourcepack: { folder: 'resourcepacks', loaders: ['minecraft'], files: /\.zip$/i },
  shader: { folder: 'shaderpacks', loaders: ['iris'], files: /\.zip$/i },
};

function kindOf(kind) {
  const k = KINDS[kind];
  if (!k) throw new Error(`Unknown content kind ${kind}`);
  return k;
}

function primaryFile(version) {
  return version.files.find((f) => f.primary) || version.files[0];
}

class ContentUpdates {
  /**
   * @param trash moves a file to the Recycle Bin (the old version of an updated file)
   * @param renamePack follows a renamed resource pack or shader in options.txt or Iris' settings
   */
  constructor({ trash, renamePack }) {
    this.trash = trash;
    this.renamePack = renamePack;
  }

  /**
   * Files of one kind that have a newer version on Modrinth. The launcher's own Aurora and
   * Fabric API are left to it ({@code skip}).
   */
  async check(gameDir, kind, mc, { skip = [] } = {}) {
    const k = kindOf(kind);
    const dir = path.join(gameDir, k.folder);
    let names = [];
    try {
      names = await fsp.readdir(dir);
    } catch {
      return [];
    }
    const skipped = new Set(skip.flatMap((n) => [n, `${n}.disabled`]));
    const hashes = {};
    for (const name of names) {
      if (!k.files.test(name) || skipped.has(name)) continue;
      const file = path.join(dir, name);
      try {
        if (!(await fsp.stat(file)).isFile()) continue;
        hashes[await net.sha1File(file)] = name;
      } catch {
        // A file that went away or cannot be read is simply not checked.
      }
    }
    const list = Object.keys(hashes);
    if (!list.length) return [];
    const [current, latest] = await Promise.all([
      net.postJson(`${MODRINTH}/version_files`, { hashes: list, algorithm: 'sha1' }, { timeout: 20000 }),
      net.postJson(`${MODRINTH}/version_files/update`, { hashes: list, algorithm: 'sha1', loaders: k.loaders, game_versions: [mc] }, { timeout: 20000 }),
    ]);
    if (!latest.ok) throw new net.HttpError(latest.status, `${MODRINTH}/version_files/update`, latest.raw);
    const out = [];
    for (const [hash, newest] of Object.entries(latest.json)) {
      const file = primaryFile(newest);
      if (!file || file.hashes.sha1 === hash) continue;
      const now = current.ok ? current.json[hash] : null;
      // The "newest" one can be older than what is there (a file from another branch).
      if (now && Date.parse(newest.date_published) <= Date.parse(now.date_published)) continue;
      out.push({
        file: hashes[hash],
        project: newest.project_id,
        from: now ? now.version_number : null,
        to: newest.version_number,
        versionId: newest.id,
        download: { url: file.url, filename: file.filename, sha1: file.hashes.sha1, size: file.size },
      });
    }
    out.sort((a, b) => a.file.localeCompare(b.file));
    return out;
  }

  /** Downloads one update and swaps it in for the old file. Returns the new file name. */
  async apply(gameDir, kind, update, { signal, onProgress } = {}) {
    const k = kindOf(kind);
    const dir = path.join(gameDir, k.folder);
    const old = path.basename(update.file);
    const disabled = /\.disabled$/i.test(old);
    const filename = path.basename(update.download.filename);
    if (!k.files.test(filename) || filename.startsWith('.')) throw new Error(`Unexpected file name ${filename}`);
    const name = disabled ? `${filename}.disabled` : filename;
    const temp = path.join(dir, `.${filename}.aurora-update`);
    await net.downloadAll([{ url: update.download.url, path: temp, sha1: update.download.sha1, size: update.download.size }], { signal, onProgress });
    const oldPath = path.join(dir, old);
    if (fs.existsSync(oldPath)) await this.trash(oldPath);
    await fsp.rename(temp, path.join(dir, name));
    if (kind !== 'mod' && old !== name) this.renamePack(gameDir, kind, old, name);

    // The launcher's records follow the file, so the catalog still shows it as installed.
    const meta = readMeta(gameDir);
    const record = { project: update.project, version: update.versionId, versionNumber: update.to };
    if (kind === 'mod') {
      delete meta.modrinth[old.replace(/\.disabled$/i, '')];
      meta.modrinth[filename] = record;
    } else {
      meta.packs = meta.packs || {};
      meta.packs[kind] = meta.packs[kind] || {};
      delete meta.packs[kind][old];
      meta.packs[kind][name] = record;
    }
    writeMeta(gameDir, meta);
    return name;
  }
}

module.exports = { ContentUpdates, KINDS };
