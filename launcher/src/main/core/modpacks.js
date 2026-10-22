'use strict';

// Modrinth modpacks. A .mrpack is a zip with modrinth.index.json, which lists the files to
// download (with their hashes and the sides that need them) and the Minecraft and loader
// versions, plus overrides/ and client-overrides/ folders copied into the game folder as they
// are. Installing a pack fills a new game folder; the launcher then adds it as an installation.

const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const net = require('./net');
const { ZipFile } = require('./zip');
const { LauncherError } = require('./errors');

const MODRINTH = 'https://api.modrinth.com/v2';
/** The only places a pack may download from: the list the format itself allows. */
const HOSTS = new Set(['cdn.modrinth.com', 'github.com', 'raw.githubusercontent.com', 'gitlab.com']);
const LOADERS = { 'fabric-loader': 'Fabric', 'quilt-loader': 'Quilt', forge: 'Forge', neoforge: 'NeoForge' };
const OVERRIDE_LIMIT = 512 * 1024 * 1024;
const ICON_LIMIT = 2 * 1024 * 1024;

/** A path from the pack as a file inside {@code root}, or null for anything that would leave it. */
function inside(root, relative) {
  const rel = String(relative || '').replace(/\\/g, '/');
  if (!rel || rel.startsWith('/') || rel.includes(':') || rel.split('/').some((part) => part === '..')) return null;
  const base = path.resolve(root);
  const target = path.resolve(base, rel);
  return target.startsWith(base + path.sep) ? target : null;
}

/** The first allowed https address a file can be downloaded from. */
function allowedUrl(urls) {
  for (const url of Array.isArray(urls) ? urls : []) {
    try {
      const u = new URL(url);
      if (u.protocol === 'https:' && HOSTS.has(u.hostname)) return u.toString();
    } catch {
      // Not a URL: try the next one.
    }
  }
  return null;
}

function readIndex(zip, name) {
  let index = null;
  try {
    index = JSON.parse((zip.text('modrinth.index.json') || 'null').replace(/^﻿/, ''));
  } catch {
    index = null;
  }
  if (!index || index.game !== 'minecraft' || !index.dependencies || typeof index.dependencies.minecraft !== 'string' || !Array.isArray(index.files)) {
    throw new LauncherError('pack_bad', name);
  }
  return index;
}

function openPack(file) {
  try {
    return new ZipFile(file);
  } catch {
    throw new LauncherError('pack_bad', path.basename(file));
  }
}

/** The files the client needs: everything not marked as unsupported on the client side. */
function clientFiles(index) {
  return index.files.filter((f) => f && !(f.env && f.env.client === 'unsupported'));
}

/**
 * What a .mrpack holds: its name and version, the Minecraft and Fabric versions, and how many
 * files it downloads. Packs for another loader are refused here, before anything is installed.
 */
function readPack(file) {
  const zip = openPack(file);
  try {
    const index = readIndex(zip, path.basename(file));
    const deps = index.dependencies;
    let loader = 'vanilla';
    let loaderVersion = null;
    for (const [key, label] of Object.entries(LOADERS)) {
      if (!deps[key]) continue;
      if (key !== 'fabric-loader') throw new LauncherError('pack_loader', key, { loader: label });
      loader = 'fabric';
      loaderVersion = String(deps[key]);
    }
    // Versions end up in folder names and download addresses: only the usual characters.
    if (!/^[\w.\-]+$/.test(deps.minecraft) || (loaderVersion && !/^[\w.\-+]+$/.test(loaderVersion))) {
      throw new LauncherError('pack_bad', path.basename(file));
    }
    const files = clientFiles(index);
    return {
      file,
      name: String(index.name || path.basename(file).replace(/\.mrpack$/i, '')).trim(),
      version: String(index.versionId || ''),
      summary: String(index.summary || ''),
      minecraft: deps.minecraft,
      loader,
      loaderVersion,
      files: files.length,
      size: files.reduce((sum, f) => sum + (Number.isInteger(f.fileSize) ? f.fileSize : 0), 0),
    };
  } finally {
    zip.close();
  }
}

/**
 * Fills {@code gameDir} from a .mrpack: downloads its files (each checked against its hash) and
 * copies its overrides, the client ones last so they win. Every path must stay inside the folder.
 */
async function installPack(file, gameDir, { signal, onProgress } = {}) {
  const progress = (p) => onProgress && onProgress(p);
  const zip = openPack(file);
  try {
    const index = readIndex(zip, path.basename(file));
    const items = [];
    for (const f of clientFiles(index)) {
      const target = inside(gameDir, f.path);
      const url = allowedUrl(f.downloads);
      const sha1 = f.hashes && String(f.hashes.sha1 || '');
      if (!target || !url || !/^[0-9a-f]{40}$/i.test(sha1)) throw new LauncherError('pack_file', String(f.path), { file: String(f.path) });
      items.push({ url, path: target, sha1, size: Number.isInteger(f.fileSize) ? f.fileSize : undefined });
    }
    progress({ stage: 'files', doneFiles: 0, totalFiles: items.length, doneBytes: 0, totalBytes: 0 });
    await net.downloadAll(items, { concurrency: 8, signal, onProgress: (p) => progress({ stage: 'files', ...p }) });

    progress({ stage: 'extract' });
    for (const prefix of ['overrides/', 'client-overrides/']) {
      for (const [name, entry] of zip.entries) {
        signal?.throwIfAborted();
        const clean = name.replace(/\\/g, '/');
        if (entry.directory || clean.endsWith('/') || !clean.startsWith(prefix)) continue;
        const target = inside(gameDir, clean.slice(prefix.length));
        if (!target) throw new LauncherError('pack_file', clean, { file: clean });
        const data = zip.get(name, OVERRIDE_LIMIT);
        if (!data) throw new LauncherError('pack_bad', clean);
        await fsp.mkdir(path.dirname(target), { recursive: true });
        await fsp.writeFile(target, data);
      }
    }
  } finally {
    zip.close();
  }
}

/** The kind of picture in {@code data}, from its first bytes. */
function pictureType(data) {
  if (data.length > 8 && data.readUInt32BE(0) === 0x89504e47) return 'image/png';
  if (data.length > 3 && data[0] === 0xff && data[1] === 0xd8 && data[2] === 0xff) return 'image/jpeg';
  if (data.length > 6 && data.toString('ascii', 0, 4) === 'GIF8') return 'image/gif';
  if (data.length > 12 && data.toString('ascii', 0, 4) === 'RIFF' && data.toString('ascii', 8, 12) === 'WEBP') return 'image/webp';
  if (/^\s*(<\?xml[^>]*>\s*)?<svg[\s>]/i.test(data.toString('utf8', 0, 256))) return 'image/svg+xml';
  return null;
}

class Modpacks {
  constructor({ cacheDir }) {
    this.dir = path.join(cacheDir, 'modpacks');
  }

  /** Fabric modpacks on Modrinth, with the newest Minecraft version each one runs on. */
  async search({ query = '', offset = 0, limit = 20, index = 'relevance' }) {
    const facets = [['project_type:modpack'], ['categories:fabric']];
    const q = new URLSearchParams({ query, offset: String(offset), limit: String(limit), index, facets: JSON.stringify(facets) });
    const res = await net.getJson(`${MODRINTH}/search?${q}`, { timeout: 15000 });
    const hide = new Set(['fabric', 'quilt', 'forge', 'neoforge']);
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
        downloads: h.downloads,
        follows: h.follows,
        categories: (h.display_categories || h.categories || []).filter((c) => !hide.has(c)),
        updated: h.date_modified,
        minecraft: Array.isArray(h.versions) && h.versions.length ? h.versions[h.versions.length - 1] : null,
      })),
    };
  }

  /** A pack's versions for Fabric, newest first, each with its .mrpack file. */
  async versions(project) {
    const q = new URLSearchParams({ loaders: JSON.stringify(['fabric']) });
    const list = await net.getJson(`${MODRINTH}/project/${encodeURIComponent(project)}/version?${q}`, { timeout: 15000 });
    return list.map((v) => this.describe(v)).filter(Boolean);
  }

  /** One version by its id, as {@link versions} lists them. */
  async version(id) {
    const v = this.describe(await net.getJson(`${MODRINTH}/version/${encodeURIComponent(id)}`, { timeout: 15000 }));
    if (!v) throw new LauncherError('pack_bad', id);
    return v;
  }

  describe(v) {
    const files = (v && v.files) || [];
    const file = files.find((f) => f.primary && /\.mrpack$/i.test(f.filename)) || files.find((f) => /\.mrpack$/i.test(f.filename));
    if (!file) return null;
    return {
      id: v.id,
      project: v.project_id,
      name: v.name,
      number: v.version_number,
      type: v.version_type,
      minecraft: v.game_versions || [],
      date: v.date_published,
      file: { url: file.url, sha1: file.hashes.sha1, size: file.size, name: file.filename },
    };
  }

  /** Downloads a version's .mrpack into the cache and returns where it is. */
  async download(version, { signal, onProgress } = {}) {
    if (!allowedUrl([version.file.url])) throw new LauncherError('pack_file', version.file.name, { file: version.file.name });
    const file = path.join(this.dir, `${version.id.replace(/[^\w-]/g, '')}.mrpack`);
    await net.downloadAll([{ url: version.file.url, path: file, sha1: version.file.sha1, size: version.file.size }], { signal, onProgress });
    return file;
  }

  /** A pack's icon from Modrinth as a data URL, for the interface to turn into an installation icon. */
  async icon(url) {
    let u;
    try {
      u = new URL(url);
    } catch {
      return null;
    }
    if (u.protocol !== 'https:' || u.hostname !== 'cdn.modrinth.com') return null;
    const data = await net.getBuffer(u.toString(), { timeout: 15000, retries: 1 });
    const type = data.length <= ICON_LIMIT ? pictureType(data) : null;
    return type ? `data:${type};base64,${data.toString('base64')}` : null;
  }
}

module.exports = { Modpacks, readPack, installPack, inside };
