'use strict';

const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const net = require('./net');
const { withZip } = require('./zip');
const { readMeta, writeMeta } = require('./mods');
const { LauncherError } = require('./errors');

const MODRINTH = 'https://api.modrinth.com/v2';
const ICON_LIMIT = 512 * 1024;

/** Resource packs live in resourcepacks/ and are switched on in options.txt; shader packs live in
 * shaderpacks/ and Iris picks the active one from config/iris.properties. */
const KINDS = {
  resourcepack: { folder: 'resourcepacks', loaders: ['minecraft'], facets: [] },
  shader: { folder: 'shaderpacks', loaders: ['iris'], facets: [['categories:iris']] },
};

function kindOf(kind) {
  const k = KINDS[kind];
  if (!k) throw new LauncherError('bad_file', String(kind));
  return k;
}

function parseJsonLoose(text) {
  if (!text) return null;
  const clean = text.replace(/^﻿/, '');
  try {
    return JSON.parse(clean);
  } catch {
    try {
      return JSON.parse(clean.replace(/[\u0000-\u001f]+/g, ' '));
    } catch {
      return null;
    }
  }
}

/** Name, description (a chat component or text) and icon of a resource pack. */
function packMeta(readText, readBytes) {
  const meta = parseJsonLoose(readText('pack.mcmeta'));
  const png = readBytes('pack.png');
  return {
    description: meta && meta.pack ? meta.pack.description ?? '' : '',
    format: meta && meta.pack ? meta.pack.pack_format : null,
    icon: png ? `data:image/png;base64,${png.toString('base64')}` : null,
  };
}

function zipMeta(file) {
  return withZip(file, (zip) => packMeta((n) => zip.text(n), (n) => zip.get(n, ICON_LIMIT)));
}

function dirMeta(dir) {
  const read = (n) => {
    try {
      const p = path.join(dir, n);
      return fs.statSync(p).size <= ICON_LIMIT ? fs.readFileSync(p) : null;
    } catch {
      return null;
    }
  };
  return packMeta((n) => {
    const b = read(n);
    return b ? b.toString('utf8') : null;
  }, read);
}

// ------------------------------------------------------------------ options.txt / iris.properties

function optionsFile(gameDir) {
  return path.join(gameDir, 'options.txt');
}

function readLines(file) {
  try {
    return fs.readFileSync(file, 'utf8').split(/\r?\n/);
  } catch {
    return [];
  }
}

/** The resource packs switched on in options.txt, lowest priority first. */
function enabledPacks(gameDir) {
  const line = readLines(optionsFile(gameDir)).find((l) => l.startsWith('resourcePacks:'));
  if (!line) return ['vanilla'];
  try {
    const list = JSON.parse(line.slice('resourcePacks:'.length));
    return Array.isArray(list) ? list.map(String) : ['vanilla'];
  } catch {
    return ['vanilla'];
  }
}

function writeEnabledPacks(gameDir, list) {
  const file = optionsFile(gameDir);
  const lines = readLines(file).filter((l, i, all) => l !== '' || i < all.length - 1);
  const value = `resourcePacks:${JSON.stringify(list)}`;
  const i = lines.findIndex((l) => l.startsWith('resourcePacks:'));
  if (i >= 0) lines[i] = value;
  else lines.push(value);
  fs.mkdirSync(gameDir, { recursive: true });
  fs.writeFileSync(file, `${lines.join('\n')}\n`);
}

function irisFile(gameDir) {
  return path.join(gameDir, 'config', 'iris.properties');
}

function readProperties(file) {
  const out = {};
  for (const line of readLines(file)) {
    if (!line || line.startsWith('#') || line.startsWith('!')) continue;
    const m = /^\s*([^=:\s]+)\s*[=:]\s*(.*)$/.exec(line);
    if (m) out[m[1]] = m[2].replace(/\\(.)/g, '$1');
  }
  return out;
}

function writeProperty(file, updates) {
  const lines = readLines(file);
  const done = new Set();
  const out = lines.map((line) => {
    const m = /^\s*([^=:\s]+)\s*[=:]/.exec(line);
    if (m && Object.prototype.hasOwnProperty.call(updates, m[1])) {
      done.add(m[1]);
      return `${m[1]}=${updates[m[1]]}`;
    }
    return line;
  }).filter((l, i, all) => l !== '' || i < all.length - 1);
  for (const [key, value] of Object.entries(updates)) if (!done.has(key)) out.push(`${key}=${value}`);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, `${out.join('\n')}\n`);
}

class Packs {
  constructor() {
    this.infoCache = new Map();
  }

  folder(gameDir, kind) {
    return path.join(gameDir, kindOf(kind).folder);
  }

  info(file, stat) {
    const key = `${file}|${stat.size}|${stat.mtimeMs}`;
    if (!this.infoCache.has(key)) {
      let info;
      try {
        info = stat.isDirectory() ? dirMeta(file) : zipMeta(file);
      } catch {
        info = { description: '', icon: null, broken: true };
      }
      this.infoCache.set(key, info);
    }
    return this.infoCache.get(key);
  }

  async list(gameDir, kind) {
    const dir = this.folder(gameDir, kind);
    let names = [];
    try {
      names = await fsp.readdir(dir);
    } catch {
      return [];
    }
    const meta = readMeta(gameDir);
    const records = (meta.packs && meta.packs[kind]) || {};
    const enabled = kind === 'resourcepack' ? enabledPacks(gameDir) : [];
    const iris = kind === 'shader' ? readProperties(irisFile(gameDir)) : {};
    const out = [];
    for (const name of names) {
      const file = path.join(dir, name);
      let stat;
      try {
        stat = await fsp.stat(file);
      } catch {
        continue;
      }
      if (!stat.isDirectory() && !/\.zip$/i.test(name)) continue;
      const info = kind === 'resourcepack' ? this.info(file, stat) : { description: '', icon: null };
      const id = `file/${name}`;
      out.push({
        file: name,
        name: name.replace(/\.zip$/i, ''),
        folder: stat.isDirectory(),
        size: stat.isDirectory() ? null : stat.size,
        modified: stat.mtimeMs,
        description: info.description,
        icon: info.icon,
        broken: Boolean(info.broken),
        enabled: kind === 'resourcepack' ? enabled.includes(id) : iris.shaderPack === name && iris.enableShaders !== 'false',
        order: kind === 'resourcepack' ? enabled.indexOf(id) : -1,
        modrinth: records[name] || null,
      });
    }
    out.sort((a, b) => (b.enabled - a.enabled) || (b.order - a.order) || a.name.localeCompare(b.name));
    return out;
  }

  /** Switches a resource pack on (on top of the others) or off in options.txt. */
  setResourcePack(gameDir, file, on) {
    const id = `file/${file}`;
    const list = enabledPacks(gameDir).filter((p) => p !== id);
    if (on) list.push(id);
    if (!list.includes('vanilla')) list.unshift('vanilla');
    writeEnabledPacks(gameDir, list);
  }

  /** Makes a shader pack the active one for Iris, or turns shaders off with null. */
  setShader(gameDir, file) {
    writeProperty(irisFile(gameDir), file ? { shaderPack: file, enableShaders: 'true' } : { enableShaders: 'false' });
  }

  async addFiles(gameDir, kind, files) {
    const dir = this.folder(gameDir, kind);
    await fsp.mkdir(dir, { recursive: true });
    const added = [];
    for (const src of files) {
      if (!/\.zip$/i.test(src)) continue;
      const name = path.basename(src);
      await fsp.copyFile(src, path.join(dir, name));
      added.push(name);
    }
    return added;
  }

  async search(kind, { query = '', mc, offset = 0, limit = 20, index = 'relevance' }) {
    const k = kindOf(kind);
    const facets = [[`project_type:${kind}`], ...k.facets];
    if (mc) facets.push([`versions:${mc}`]);
    const q = new URLSearchParams({ query, offset: String(offset), limit: String(limit), index, facets: JSON.stringify(facets) });
    const res = await net.getJson(`${MODRINTH}/search?${q}`, { timeout: 15000 });
    const hide = new Set(['minecraft', 'iris', 'optifine', 'canvas', 'vanilla']);
    return {
      total: res.total_hits,
      offset: res.offset,
      hits: res.hits.map((h) => ({
        id: h.project_id,
        slug: h.slug,
        title: h.title,
        author: h.author,
        description: h.description,
        icon: h.icon_url || null,
        gallery: h.featured_gallery || (h.gallery && h.gallery[0]) || null,
        downloads: h.downloads,
        follows: h.follows,
        categories: (h.display_categories || h.categories || []).filter((c) => !hide.has(c)),
        updated: h.date_modified,
      })),
    };
  }

  async install(kind, project, gameDir, mc, { signal, onProgress } = {}) {
    const k = kindOf(kind);
    const q = new URLSearchParams({ game_versions: JSON.stringify([mc]), loaders: JSON.stringify(k.loaders) });
    let versions = await net.getJson(`${MODRINTH}/project/${encodeURIComponent(project)}/version?${q}`, { timeout: 15000 });
    if (!versions.length && kind === 'resourcepack') {
      // Many packs only list older versions; Minecraft still loads them with a warning.
      versions = await net.getJson(`${MODRINTH}/project/${encodeURIComponent(project)}/version?${new URLSearchParams({ loaders: JSON.stringify(k.loaders) })}`, { timeout: 15000 });
    }
    const pick = versions.find((v) => v.version_type === 'release') || versions[0];
    if (!pick) throw new LauncherError('mod_incompatible', project, { version: mc });
    const file = pick.files.find((f) => f.primary) || pick.files[0];
    const dir = this.folder(gameDir, kind);
    await net.downloadAll([{ url: file.url, path: path.join(dir, file.filename), sha1: file.hashes.sha1, size: file.size }], { signal, onProgress });
    const meta = readMeta(gameDir);
    meta.packs = meta.packs || {};
    meta.packs[kind] = meta.packs[kind] || {};
    meta.packs[kind][file.filename] = { project: pick.project_id, version: pick.id, versionNumber: pick.version_number };
    writeMeta(gameDir, meta);
    return file.filename;
  }

  async installedProjects(gameDir, kind) {
    const meta = readMeta(gameDir);
    const records = (meta.packs && meta.packs[kind]) || {};
    const projects = new Set(Object.values(records).map((r) => r.project));
    const dir = this.folder(gameDir, kind);
    let names = [];
    try {
      names = (await fsp.readdir(dir)).filter((n) => /\.zip$/i.test(n) && !records[n]);
    } catch {
      return [...projects];
    }
    if (names.length) {
      const hashes = {};
      for (const n of names) hashes[await net.sha1File(path.join(dir, n))] = n;
      try {
        const res = await net.postJson(`${MODRINTH}/version_files`, { hashes: Object.keys(hashes), algorithm: 'sha1' }, { timeout: 15000 });
        if (res.ok) {
          meta.packs = meta.packs || {};
          meta.packs[kind] = meta.packs[kind] || {};
          for (const [hash, version] of Object.entries(res.json)) {
            meta.packs[kind][hashes[hash]] = { project: version.project_id, version: version.id, versionNumber: version.version_number };
            projects.add(version.project_id);
          }
          writeMeta(gameDir, meta);
        }
      } catch {
        // Offline: what the launcher installed itself is still known.
      }
    }
    return [...projects];
  }
}

module.exports = { Packs, KINDS };
