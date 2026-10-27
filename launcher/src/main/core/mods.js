'use strict';

const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const net = require('./net');
const { withZip } = require('./zip');
const { readJson, writeJson } = require('./store');
const { compareVersions } = require('./rules');
const { LauncherError } = require('./errors');

const MODRINTH = 'https://api.modrinth.com/v2';
const FABRIC_API_TTL = 6 * 60 * 60 * 1000;
const AURORA_TTL = 60 * 60 * 1000;
const ICON_LIMIT = 256 * 1024;

function metaFile(gameDir) {
  return path.join(gameDir, '.aurora', 'instance.json');
}

/** What the launcher manages inside one game folder (bundled and auto-updated mods). */
function readMeta(gameDir) {
  const meta = readJson(metaFile(gameDir), {});
  meta.managed = meta.managed || {};
  meta.modrinth = meta.modrinth || {};
  return meta;
}

function writeMeta(gameDir, meta) {
  writeJson(metaFile(gameDir), meta);
}

/** "~26.3", ">=1.21", "26.3.x" … reduced to the version prefix a mod was built for. */
function versionPrefix(range) {
  const v = String(range || '').replace(/^[~^>=<\s]+/, '').replace(/\.x$/, '');
  return v.split(/\s|\|/)[0];
}

/** The mod's own version without the Minecraft part: "1.2.0+26.3" -> "1.2.0". */
function modVersion(version) {
  return String(version || '').split('+')[0];
}

function supports(range, mc) {
  if (!range || range === '*') return true;
  const prefix = versionPrefix(range);
  return mc === prefix || mc.startsWith(prefix + '.');
}

function parseModJson(raw) {
  const clean = raw.replace(/^﻿/, '');
  try {
    return JSON.parse(clean);
  } catch {
    // Some mods ship raw line breaks inside strings, which JSON does not allow.
    return JSON.parse(clean.replace(/[\u0000-\u001f]+/g, ' '));
  }
}

function iconData(zip, meta) {
  let icon = meta.icon;
  if (icon && typeof icon === 'object') {
    const sizes = Object.keys(icon).map(Number).filter((n) => !Number.isNaN(n)).sort((a, b) => a - b);
    const best = sizes.find((s) => s >= 64) || sizes[sizes.length - 1];
    icon = icon[String(best)];
  }
  if (typeof icon !== 'string') return null;
  const data = zip.get(icon.replace(/^\//, ''), ICON_LIMIT);
  if (!data) return null;
  const type = icon.toLowerCase().endsWith('.jpg') || icon.toLowerCase().endsWith('.jpeg') ? 'image/jpeg' : 'image/png';
  return `data:${type};base64,${data.toString('base64')}`;
}

/** Name, version, authors and icon of a mod jar, from its fabric.mod.json. */
function readModInfo(file) {
  return withZip(file, (zip) => {
    const raw = zip.text('fabric.mod.json');
    if (!raw) {
      return { id: null, name: null, loader: zip.has('quilt.mod.json') ? 'quilt' : zip.has('META-INF/mods.toml') || zip.has('META-INF/neoforge.mods.toml') ? 'forge' : null };
    }
    const meta = parseModJson(raw);
    const authors = (meta.authors || []).map((a) => (typeof a === 'string' ? a : a && a.name)).filter(Boolean);
    return {
      id: meta.id || null,
      name: meta.name || meta.id || null,
      version: meta.version || '',
      description: typeof meta.description === 'string' ? meta.description : '',
      authors,
      icon: iconData(zip, meta),
      homepage: meta.contact && (meta.contact.homepage || meta.contact.sources) || null,
      minecraft: meta.depends && meta.depends.minecraft,
      loader: 'fabric',
    };
  });
}

class Mods {
  constructor({ bundledDir, auroraProject = null, cacheDir = null }) {
    this.bundledDir = bundledDir;
    this.infoCache = new Map();
    this.hashCache = new Map();
    this.fabricApiCache = new Map();
    // Aurora builds published on Modrinth, remembered between starts so offline launches and the
    // version lists still know about them.
    this.auroraProject = auroraProject;
    this.auroraCacheFile = cacheDir ? path.join(cacheDir, 'aurora-modrinth.json') : null;
    this.auroraRemote = this.auroraCacheFile ? readJson(this.auroraCacheFile, null) : null;
    this.auroraRefreshing = null;
  }

  info(file, stat) {
    const key = `${file}|${stat.size}|${stat.mtimeMs}`;
    if (!this.infoCache.has(key)) {
      let info;
      try {
        info = readModInfo(file);
      } catch (e) {
        info = { id: null, name: null, broken: true };
      }
      this.infoCache.set(key, info);
    }
    return this.infoCache.get(key);
  }

  /** The Aurora jar shipped with the launcher (or built next to it in development). */
  bundled() {
    let names = [];
    try {
      names = fs.readdirSync(this.bundledDir);
    } catch {
      return null;
    }
    const jars = names.filter((n) => /^aurora-.*\.jar$/i.test(n) && !/-sources\.jar$/i.test(n));
    let best = null;
    for (const name of jars) {
      const file = path.join(this.bundledDir, name);
      const stat = fs.statSync(file);
      const info = this.info(file, stat);
      if (info.id !== 'aurora') continue;
      if (!best || compareVersions(info.version, best.version) > 0) {
        best = { path: file, file: name, size: stat.size, mtime: stat.mtimeMs, version: info.version, minecraft: info.minecraft };
      }
    }
    return best;
  }

  /** SHA-1 of a jar, remembered until the file changes. */
  async sha1(file, stat) {
    const key = `${file}|${stat.size}|${stat.mtimeMs}`;
    if (!this.hashCache.has(key)) this.hashCache.set(key, await net.sha1File(file));
    return this.hashCache.get(key);
  }

  bundledSha1(bundled) {
    return this.sha1(bundled.path, { size: bundled.size, mtimeMs: bundled.mtime });
  }

  /**
   * Looks up the Aurora releases on Modrinth (for every Minecraft version), at most once an hour
   * unless forced. A project that is not published yet simply offers nothing; when offline, the
   * last known list stays.
   */
  async refreshAurora(force = false) {
    if (!this.auroraProject) return;
    const known = this.auroraRemote;
    if (!force && known && Date.now() - known.checkedAt < AURORA_TTL) return;
    if (!this.auroraRefreshing) {
      this.auroraRefreshing = (async () => {
        let versions = [];
        try {
          const q = new URLSearchParams({ loaders: JSON.stringify(['fabric']) });
          const list = await net.getJson(`${MODRINTH}/project/${encodeURIComponent(this.auroraProject)}/version?${q}`, { timeout: 15000, retries: 2 });
          versions = list
            .filter((v) => v.version_type !== 'alpha')
            .map((v) => {
              const file = v.files.find((f) => f.primary) || v.files[0];
              return file && { version: v.version_number, games: v.game_versions, file: file.filename, url: file.url, sha1: file.hashes.sha1, size: file.size };
            })
            .filter(Boolean);
        } catch (e) {
          if (!(e && e.status === 404)) return;
        }
        // Builds that turned out not to be Aurora stay refused after a new lookup too.
        this.auroraRemote = { checkedAt: Date.now(), versions, rejected: (known && known.rejected) || [] };
        if (this.auroraCacheFile) writeJson(this.auroraCacheFile, this.auroraRemote);
      })().finally(() => {
        this.auroraRefreshing = null;
      });
    }
    await this.auroraRefreshing;
  }

  /**
   * The newest Aurora for a Minecraft version: the build shipped with the launcher, or a newer one
   * published on Modrinth. Null when neither runs on that version.
   */
  auroraFor(mc) {
    const bundled = this.bundled();
    let best = bundled && supports(bundled.minecraft, mc) ? { source: 'bundled', ...bundled } : null;
    const rejected = new Set((this.auroraRemote && this.auroraRemote.rejected) || []);
    for (const v of (this.auroraRemote && this.auroraRemote.versions) || []) {
      if (!Array.isArray(v.games) || !v.games.includes(mc) || rejected.has(v.sha1)) continue;
      if (!best || compareVersions(modVersion(v.version), modVersion(best.version)) > 0) best = { source: 'modrinth', ...v };
    }
    return best;
  }

  /**
   * The newest Aurora jar in an installation's mods folder that the launcher did not put there
   * itself (it remembers the SHA-1 of its own), e.g. a build copied in by hand to try it out.
   */
  async localAurora(gameDir, mc, meta) {
    const dir = path.join(gameDir, 'mods');
    let names = [];
    try {
      names = await fsp.readdir(dir);
    } catch {
      return null;
    }
    let best = null;
    for (const name of names) {
      if (!/\.jar$/i.test(name)) continue;
      const file = path.join(dir, name);
      let stat;
      try {
        stat = await fsp.stat(file);
      } catch {
        continue;
      }
      const info = this.info(file, stat);
      if (info.id !== 'aurora' || !supports(info.minecraft, mc)) continue;
      if (meta.auroraSha1 && (await this.sha1(file, stat)) === meta.auroraSha1) continue;
      if (!best || compareVersions(modVersion(info.version), modVersion(best.version)) > 0) {
        best = { source: 'local', file: name, version: info.version };
      }
    }
    return best;
  }

  /**
   * The Aurora an installation runs: the launcher's own build (or a newer one from Modrinth), unless
   * its mods folder holds one put there by hand. Development builds keep their version number, so
   * that one stays until the launcher has a newer version.
   */
  async auroraIn(gameDir, mc, meta = readMeta(gameDir)) {
    const known = this.auroraFor(mc);
    const local = await this.localAurora(gameDir, mc, meta);
    if (local && (!known || compareVersions(modVersion(local.version), modVersion(known.version)) >= 0)) return local;
    return known;
  }

  /** Whether a jar really is Aurora (by the id in its fabric.mod.json, not by its name). */
  isAurora(file) {
    return fs.existsSync(file) && this.info(file, fs.statSync(file)).id === 'aurora';
  }

  /** Never offers this Modrinth build again. */
  rejectAurora(version) {
    if (!this.auroraRemote) return;
    const rejected = new Set(this.auroraRemote.rejected || []);
    rejected.add(version.sha1);
    this.auroraRemote = { ...this.auroraRemote, rejected: [...rejected] };
    if (this.auroraCacheFile) writeJson(this.auroraCacheFile, this.auroraRemote);
  }

  /** Whether some Aurora build (shipped or on Modrinth) runs on this Minecraft version. */
  auroraSupports(mc) {
    return Boolean(this.auroraFor(mc));
  }

  async list(gameDir) {
    const dir = path.join(gameDir, 'mods');
    let names = [];
    try {
      names = await fsp.readdir(dir);
    } catch {
      return [];
    }
    const meta = readMeta(gameDir);
    const managed = new Set(Object.values(meta.managed));
    const mods = [];
    for (const name of names) {
      const enabled = /\.jar$/i.test(name);
      if (!enabled && !/\.jar\.disabled$/i.test(name)) continue;
      const file = path.join(dir, name);
      let stat;
      try {
        stat = await fsp.stat(file);
      } catch {
        continue;
      }
      if (!stat.isFile()) continue;
      const jar = enabled ? name : name.replace(/\.disabled$/i, '');
      const info = this.info(file, stat);
      mods.push({
        file: name,
        jar,
        enabled,
        size: stat.size,
        modified: stat.mtimeMs,
        managed: managed.has(jar),
        modrinth: meta.modrinth[jar] || null,
        ...info,
        name: info.name || jar.replace(/\.jar$/i, ''),
      });
    }
    mods.sort((a, b) => (b.id === 'aurora') - (a.id === 'aurora') || a.name.localeCompare(b.name));
    return mods;
  }

  async countEnabled(gameDir) {
    try {
      return (await fsp.readdir(path.join(gameDir, 'mods'))).filter((n) => /\.jar$/i.test(n)).length;
    } catch {
      return 0;
    }
  }

  async setEnabled(gameDir, file, enabled) {
    const dir = path.join(gameDir, 'mods');
    const jar = file.replace(/\.disabled$/i, '');
    const from = path.join(dir, enabled ? `${jar}.disabled` : jar);
    const to = path.join(dir, enabled ? jar : `${jar}.disabled`);
    if (from === to || !fs.existsSync(from)) return;
    await fsp.rename(from, to);
  }

  /** Copies jars into the mods folder; returns the names that were added. */
  async addFiles(gameDir, files) {
    const dir = path.join(gameDir, 'mods');
    await fsp.mkdir(dir, { recursive: true });
    const added = [];
    for (const src of files) {
      if (!/\.jar$/i.test(src)) continue;
      const name = path.basename(src);
      await fsp.copyFile(src, path.join(dir, name));
      added.push(name);
    }
    return added;
  }

  // ---------------------------------------------------------------- managed content

  async latestFabricApi(mc, meta) {
    const remembered = meta.fabricApi;
    if (remembered && remembered.mc === mc && Date.now() - remembered.checkedAt < FABRIC_API_TTL) return remembered;
    const cached = this.fabricApiCache.get(mc);
    if (cached && Date.now() - cached.checkedAt < FABRIC_API_TTL) return cached;
    try {
      const versions = await this.projectVersions('fabric-api', mc);
      const pick = versions.find((v) => v.version_type === 'release') || versions[0];
      if (!pick) return remembered && remembered.mc === mc ? remembered : null;
      const file = pick.files.find((f) => f.primary) || pick.files[0];
      const result = { mc, checkedAt: Date.now(), version: pick.version_number, file: file.filename, url: file.url, sha1: file.hashes.sha1, size: file.size };
      this.fabricApiCache.set(mc, result);
      return result;
    } catch (e) {
      // Offline: keep whatever was installed last time.
      if (remembered && remembered.mc === mc) return remembered;
      throw e;
    }
  }

  /**
   * Plans the mods the launcher looks after for an installation: the bundled Aurora build and an
   * up-to-date Fabric API. Returns downloads plus a finish step, like the other planners.
   */
  async plan(inst, gameDir, mc) {
    const meta = readMeta(gameDir);
    const dir = path.join(gameDir, 'mods');
    const files = [];
    const steps = [];
    if (inst.loader !== 'fabric') {
      return { files, finish: async () => {} };
    }
    if (inst.aurora) await this.refreshAurora().catch(() => {});
    const aurora = inst.aurora ? await this.auroraIn(gameDir, mc, meta) : null;
    if (aurora) {
      const target = path.join(dir, aurora.file);
      let fresh;
      // The launcher's own build, remembered to tell it apart from one put there by hand.
      let sha1 = null;
      if (aurora.source === 'modrinth') {
        // A newer build from Modrinth: downloaded and checked like every other file.
        sha1 = aurora.sha1;
        fresh = await net.fileMatches(target, { size: aurora.size, sha1 }, true);
        if (!fresh) files.push({ url: aurora.url, path: target, sha1, size: aurora.size });
      } else if (aurora.source === 'bundled') {
        // Development builds keep their version number, so compare the contents, not just the size.
        sha1 = await this.bundledSha1(aurora);
        fresh = await net.fileMatches(target, { size: aurora.size, sha1 }, true);
      } else {
        // A build put there by hand: it stays as it is.
        fresh = true;
      }
      steps.push(async () => {
        await fsp.mkdir(dir, { recursive: true });
        let file = aurora.file;
        if (aurora.source === 'modrinth') {
          // Only a jar that really is Aurora is kept: a project page can change hands.
          if (!this.isAurora(target)) {
            await fsp.rm(target, { force: true });
            this.rejectAurora(aurora);
            const bundled = this.bundled();
            if (!bundled || !supports(bundled.minecraft, mc)) return;
            file = bundled.file;
            sha1 = await this.bundledSha1(bundled);
            await fsp.copyFile(bundled.path, path.join(dir, file));
          }
        } else if (!fresh) {
          await fsp.copyFile(aurora.path, target);
        }
        // Drop other Aurora builds (whatever their file is called) so two copies never load at once.
        for (const name of await fsp.readdir(dir)) {
          const jar = name.replace(/\.disabled$/i, '');
          if (jar === file || !/\.jar$/i.test(jar)) continue;
          const info = this.info(path.join(dir, name), fs.statSync(path.join(dir, name)));
          if (info.id === 'aurora') await fsp.rm(path.join(dir, name), { force: true });
        }
        const kept = path.join(dir, file);
        if (fs.existsSync(`${kept}.disabled`)) await fsp.rm(`${kept}.disabled`, { force: true });
        meta.managed.aurora = file;
        if (sha1) meta.auroraSha1 = sha1;
      });
    } else if (meta.managed.aurora && !inst.aurora) {
      const old = meta.managed.aurora;
      steps.push(async () => {
        await fsp.rm(path.join(dir, old), { force: true });
        await fsp.rm(path.join(dir, `${old}.disabled`), { force: true });
        delete meta.managed.aurora;
        delete meta.auroraSha1;
      });
    }

    if (inst.aurora || inst.fabricApi !== false) {
      let own = [];
      try {
        own = (await fsp.readdir(dir)).filter((n) => /^fabric-api-.*\.jar(\.disabled)?$/i.test(n) && n.replace(/\.disabled$/i, '') !== meta.managed['fabric-api']);
      } catch {
        own = [];
      }
      // A Fabric API the player put there themselves is left alone.
      if (own.length === 0) {
        const latest = await this.latestFabricApi(mc, meta);
        if (latest) {
          const target = path.join(dir, latest.file);
          if (!(await net.fileMatches(target, latest))) {
            files.push({ url: latest.url, path: target, sha1: latest.sha1, size: latest.size });
          }
          steps.push(async () => {
            const old = meta.managed['fabric-api'];
            if (old && old !== latest.file) {
              await fsp.rm(path.join(dir, old), { force: true });
              await fsp.rm(path.join(dir, `${old}.disabled`), { force: true });
            }
            meta.managed['fabric-api'] = latest.file;
            meta.fabricApi = latest;
          });
        }
      }
    }
    steps.push(async () => writeMeta(gameDir, meta));
    return {
      files,
      finish: async () => {
        for (const step of steps) await step();
      },
    };
  }

  // ---------------------------------------------------------------- Modrinth

  async projectVersions(project, mc, loader = 'fabric') {
    const q = new URLSearchParams({ game_versions: JSON.stringify([mc]), loaders: JSON.stringify([loader]) });
    return net.getJson(`${MODRINTH}/project/${encodeURIComponent(project)}/version?${q}`, { timeout: 15000 });
  }

  async search({ query = '', mc, offset = 0, limit = 20, index = 'relevance' }) {
    const facets = [['project_type:mod'], ['categories:fabric']];
    if (mc) facets.push([`versions:${mc}`]);
    const q = new URLSearchParams({ query, offset: String(offset), limit: String(limit), index, facets: JSON.stringify(facets) });
    const res = await net.getJson(`${MODRINTH}/search?${q}`, { timeout: 15000 });
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
        categories: (h.display_categories || h.categories || []).filter((c) => c !== 'fabric'),
        updated: h.date_modified,
        color: h.color,
      })),
    };
  }

  /**
   * Installs a Modrinth project and its required dependencies into the game folder. Projects
   * that are already installed (by the launcher or found by hash) are skipped.
   */
  async install(project, gameDir, mc, { signal, onProgress } = {}) {
    const meta = readMeta(gameDir);
    const dir = path.join(gameDir, 'mods');
    const installed = new Set(Object.values(meta.modrinth).map((m) => m.project));
    const queue = [project];
    const files = [];
    const records = [];
    const seen = new Set();
    while (queue.length) {
      const id = queue.shift();
      if (seen.has(id) || (id !== project && installed.has(id))) continue;
      seen.add(id);
      const versions = await this.projectVersions(id, mc);
      const pick = versions.find((v) => v.version_type === 'release') || versions[0];
      if (!pick) {
        if (id === project) throw new LauncherError('mod_incompatible', id, { version: mc });
        continue;
      }
      const file = pick.files.find((f) => f.primary) || pick.files[0];
      files.push({ url: file.url, path: path.join(dir, file.filename), sha1: file.hashes.sha1, size: file.size });
      records.push({ file: file.filename, project: pick.project_id, version: pick.id, versionNumber: pick.version_number });
      for (const dep of pick.dependencies || []) {
        if (dep.dependency_type === 'required' && dep.project_id && dep.project_id !== 'P7dR8mSH') queue.push(dep.project_id);
      }
    }
    await net.downloadAll(files, { signal, onProgress, concurrency: 4 });
    const fresh = readMeta(gameDir);
    for (const r of records) fresh.modrinth[r.file] = { project: r.project, version: r.version, versionNumber: r.versionNumber };
    writeMeta(gameDir, fresh);
    return records.map((r) => r.file);
  }

  /** Projects installed in a game folder, matched through Modrinth by file hash. */
  async installedProjects(gameDir) {
    const meta = readMeta(gameDir);
    const projects = new Set(Object.values(meta.modrinth).map((m) => m.project));
    const dir = path.join(gameDir, 'mods');
    let names = [];
    try {
      names = (await fsp.readdir(dir)).filter((n) => /\.jar(\.disabled)?$/i.test(n));
    } catch {
      return [...projects];
    }
    const unknown = names.filter((n) => !meta.modrinth[n.replace(/\.disabled$/i, '')]);
    if (unknown.length) {
      const hashes = {};
      for (const n of unknown) hashes[await net.sha1File(path.join(dir, n))] = n;
      try {
        const res = await net.postJson(`${MODRINTH}/version_files`, { hashes: Object.keys(hashes), algorithm: 'sha1' }, { timeout: 15000 });
        if (res.ok) {
          for (const [hash, version] of Object.entries(res.json)) {
            const jar = hashes[hash].replace(/\.disabled$/i, '');
            meta.modrinth[jar] = { project: version.project_id, version: version.id, versionNumber: version.version_number };
            projects.add(version.project_id);
          }
          writeMeta(gameDir, meta);
        }
      } catch {
        // Offline: the launcher's own records still mark what it installed.
      }
    }
    return [...projects];
  }
}

module.exports = { Mods, readModInfo, readMeta, writeMeta, supports };
