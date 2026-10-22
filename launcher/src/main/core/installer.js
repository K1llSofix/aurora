'use strict';

const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const net = require('./net');
const { allowed, mavenPath, libraryKey, OS_NAME } = require('./rules');
const { readJson } = require('./store');
const { withZip } = require('./zip');
const { LauncherError } = require('./errors');

const ASSETS_URL = 'https://resources.download.minecraft.net';
const LIBRARIES_URL = 'https://libraries.minecraft.net/';

/** Runs {@code fn} over {@code items} with at most {@code limit} calls in flight. */
async function eachLimit(items, limit, fn) {
  let next = 0;
  const worker = async () => {
    while (next < items.length) {
      const i = next++;
      await fn(items[i], i);
    }
  };
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
}

/**
 * Gets an installation ready to start: version files, the Java runtime, libraries, assets and
 * the mods the launcher manages. Everything missing goes into one download batch so the player
 * sees a single progress bar.
 */
class Installer {
  constructor({ paths, versions, java, mods }) {
    this.paths = paths;
    this.versions = versions;
    this.java = java;
    this.mods = mods;
  }

  libraryFile(relative) {
    return path.join(this.paths.libraries, ...relative.split('/'));
  }

  /** Classpath libraries and (for old versions) native archives that apply to this system. */
  libraries(version) {
    const classpath = [];
    const natives = [];
    const seen = new Set();
    const arch = process.arch === 'ia32' ? '32' : '64';
    for (const lib of version.libraries || []) {
      if (!allowed(lib.rules)) continue;
      if (lib.natives) {
        const classifier = lib.natives[OS_NAME] && lib.natives[OS_NAME].replace('${arch}', arch);
        const art = classifier && lib.downloads?.classifiers?.[classifier];
        if (art) {
          natives.push({ url: art.url, path: this.libraryFile(art.path), sha1: art.sha1, size: art.size, exclude: lib.extract?.exclude || [] });
        } else if (classifier && !lib.downloads) {
          const rel = mavenPath(`${lib.name}:${classifier}`);
          natives.push({ url: (lib.url || LIBRARIES_URL).replace(/\/?$/, '/') + rel, path: this.libraryFile(rel), exclude: lib.extract?.exclude || [] });
        }
        if (!lib.downloads?.artifact) continue;
      }
      // The child profile's copy of a library comes first and wins.
      const key = libraryKey(lib.name);
      if (seen.has(key)) continue;
      seen.add(key);
      const art = lib.downloads?.artifact;
      if (art) {
        const rel = art.path || mavenPath(lib.name);
        classpath.push({ url: art.url || null, path: this.libraryFile(rel), sha1: art.sha1, size: art.size });
      } else if (!lib.downloads) {
        const rel = mavenPath(lib.name);
        classpath.push({ url: (lib.url || LIBRARIES_URL).replace(/\/?$/, '/') + rel, path: this.libraryFile(rel), sha1: lib.sha1, size: lib.size });
      }
    }
    return { classpath, natives };
  }

  async assets(version) {
    const idx = version.assetIndex;
    if (!idx) return { items: [], indexName: version.assets || 'legacy', index: { objects: {} } };
    const file = path.join(this.paths.assets, 'indexes', `${idx.id}.json`);
    if (!(await net.fileMatches(file, idx, true))) {
      await net.download(idx.url, file, { sha1: idx.sha1, size: idx.size });
    }
    const index = readJson(file, null);
    if (!index || !index.objects) throw new LauncherError('version_broken', `asset index ${idx.id}`, { version: version.vanillaId });
    const items = [];
    const seen = new Set();
    for (const obj of Object.values(index.objects)) {
      if (seen.has(obj.hash)) continue;
      seen.add(obj.hash);
      const sub = obj.hash.slice(0, 2);
      items.push({ url: `${ASSETS_URL}/${sub}/${obj.hash}`, path: path.join(this.paths.assets, 'objects', sub, obj.hash), sha1: obj.hash, size: obj.size });
    }
    return { items, indexName: idx.id, index };
  }

  /**
   * @param inst installation settings
   * @param opts {gameDir, javaPath (custom, optional), verify (hash every file), onProgress, signal}
   */
  async prepare(inst, { gameDir, javaPath = null, verify = false, onProgress, signal } = {}) {
    const progress = (p) => onProgress && onProgress(p);
    progress({ stage: 'resolve' });
    const version = await this.versions.resolve(inst);
    signal?.throwIfAborted();
    const vanillaId = version.vanillaId;
    const downloads = [];
    const checks = [];

    const client = version.downloads && version.downloads.client;
    const clientJar = path.join(this.paths.versions, vanillaId, `${vanillaId}.jar`);
    if (client) checks.push({ url: client.url, path: clientJar, sha1: client.sha1, size: client.size });
    else if (!fs.existsSync(clientJar)) throw new LauncherError('version_broken', `${vanillaId} has no client`, { version: vanillaId });

    const { classpath, natives } = this.libraries(version);
    for (const lib of [...classpath, ...natives]) {
      if (lib.url) checks.push(lib);
    }

    const logging = version.logging && version.logging.client;
    let loggingFile = null;
    if (logging && logging.file) {
      loggingFile = path.join(this.paths.assets, 'log_configs', logging.file.id);
      checks.push({ url: logging.file.url, path: loggingFile, sha1: logging.file.sha1, size: logging.file.size });
    }

    progress({ stage: 'assets' });
    const assets = await this.assets(version);
    checks.push(...assets.items);

    progress({ stage: 'check', total: checks.length });
    await eachLimit(checks, verify ? 8 : 64, async (item) => {
      if (!(await net.fileMatches(item.path, item, verify))) downloads.push(item);
    });
    signal?.throwIfAborted();

    let runtime = null;
    if (!javaPath) {
      progress({ stage: 'java' });
      runtime = await this.java.plan((version.javaVersion && version.javaVersion.component) || 'jre-legacy', { verify });
      downloads.push(...runtime.files);
      javaPath = runtime.javaPath;
    }

    progress({ stage: 'mods' });
    const content = await this.mods.plan(inst, gameDir, vanillaId);
    downloads.push(...content.files);

    if (downloads.length) {
      await net.downloadAll(downloads, {
        concurrency: 16,
        signal,
        onProgress: (p) => progress({ stage: 'download', ...p }),
      });
    }

    progress({ stage: 'finish' });
    if (runtime) await runtime.finish();
    await fsp.mkdir(gameDir, { recursive: true });
    await content.finish();
    const nativesDir = path.join(this.paths.natives, version.id);
    await this.extractNatives(natives, nativesDir);
    await this.legacyAssets(assets, gameDir);

    return {
      version,
      javaPath,
      gameDir,
      classpath: [...classpath.map((l) => l.path), clientJar],
      nativesDir,
      loggingFile,
      assetsRoot: this.paths.assets,
      librariesRoot: this.paths.libraries,
      assetsIndex: assets.indexName,
      virtualAssets: assets.index.virtual ? path.join(this.paths.assets, 'virtual', assets.indexName) : null,
    };
  }

  /** Unpacks native archives of pre-1.19 versions; newer ones extract natives themselves. */
  async extractNatives(natives, dir) {
    await fsp.mkdir(dir, { recursive: true });
    const root = path.resolve(dir) + path.sep;
    for (const n of natives) {
      withZip(n.path, (zip) => {
        for (const [name, entry] of zip.entries) {
          if (entry.directory || name.startsWith('META-INF/')) continue;
          if (n.exclude.some((x) => name.startsWith(x))) continue;
          const target = path.resolve(dir, name);
          if (!target.startsWith(root)) continue;
          try {
            if (fs.statSync(target).size === entry.size) continue;
          } catch {
            // Not there yet.
          }
          const data = zip.get(name);
          if (!data) continue;
          fs.mkdirSync(path.dirname(target), { recursive: true });
          fs.writeFileSync(target, data);
        }
      });
    }
  }

  /** Very old versions read assets by name from a "virtual" folder or the game's resources/. */
  async legacyAssets(assets, gameDir) {
    const index = assets.index;
    if (!index.virtual && !index.map_to_resources) return;
    const base = index.virtual ? path.join(this.paths.assets, 'virtual', assets.indexName) : path.join(gameDir, 'resources');
    for (const [name, obj] of Object.entries(index.objects)) {
      const target = path.join(base, ...name.split('/'));
      if (fs.existsSync(target)) continue;
      await fsp.mkdir(path.dirname(target), { recursive: true });
      await fsp.copyFile(path.join(this.paths.assets, 'objects', obj.hash.slice(0, 2), obj.hash), target);
    }
  }
}

module.exports = { Installer };
