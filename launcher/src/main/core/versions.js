'use strict';

const fs = require('fs');
const path = require('path');
const net = require('./net');
const { readJson, writeJson } = require('./store');
const { compareVersions } = require('./rules');
const { LauncherError } = require('./errors');

const MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const FABRIC_META = 'https://meta.fabricmc.net/v2';
const MANIFEST_TTL = 10 * 60 * 1000;
const LOADERS_TTL = 30 * 60 * 1000;

/**
 * Version files: the Mojang manifest, vanilla version JSONs and Fabric profiles. Everything is
 * cached on disk, so a game that was installed once still starts without internet.
 */
class Versions {
  constructor(paths) {
    this.paths = paths;
    this.manifestCache = null;
    this.loaderCache = new Map();
  }

  async manifest(force = false) {
    if (!force && this.manifestCache && Date.now() - this.manifestCache.at < MANIFEST_TTL) {
      return this.manifestCache.data;
    }
    const file = path.join(this.paths.cache, 'version_manifest_v2.json');
    try {
      const data = await net.getJson(MANIFEST, { timeout: 15000, retries: 2 });
      writeJson(file, data);
      this.manifestCache = { at: Date.now(), data };
      return data;
    } catch (e) {
      const cached = readJson(file, null);
      if (!cached) throw e;
      // Try the network again in a minute rather than in ten.
      this.manifestCache = { at: Date.now() - MANIFEST_TTL + 60000, data: cached };
      return cached;
    }
  }

  /** Releases (and snapshots when asked) for the version picker, newest first. */
  async list(snapshots = false) {
    const manifest = await this.manifest();
    return manifest.versions
      .filter((v) => v.type === 'release' || (snapshots && v.type === 'snapshot'))
      .map((v) => ({ id: v.id, type: v.type, releaseTime: v.releaseTime }));
  }

  async latestRelease() {
    return (await this.manifest()).latest.release;
  }

  /** The vanilla version file, downloaded once and re-checked against the manifest's SHA-1. */
  async vanilla(id) {
    const file = path.join(this.paths.versions, id, `${id}.json`);
    let entry = null;
    let offline = null;
    try {
      entry = (await this.manifest()).versions.find((v) => v.id === id) || null;
    } catch (e) {
      // Offline: fall back to whatever is on disk.
      offline = e;
    }
    if (entry && !(await net.fileMatches(file, { sha1: entry.sha1 }, true))) {
      await net.download(entry.url, file, { sha1: entry.sha1 });
    }
    const json = readJson(file, null);
    if (!json) {
      // Nothing on disk and no manifest: the real problem is the connection.
      if (offline) throw offline;
      throw new LauncherError(entry ? 'version_broken' : 'version_missing', id, { version: id });
    }
    return json;
  }

  async fabricLoaders(mc) {
    const cached = this.loaderCache.get(mc);
    if (cached && Date.now() - cached.at < LOADERS_TTL) return cached.data;
    const file = path.join(this.paths.cache, 'fabric', `loaders-${mc}.json`);
    try {
      const data = await net.getJson(`${FABRIC_META}/versions/loader/${encodeURIComponent(mc)}`, { timeout: 15000, retries: 2 });
      writeJson(file, data);
      this.loaderCache.set(mc, { at: Date.now(), data });
      return data;
    } catch (e) {
      const data = readJson(file, null);
      if (!data) throw e;
      this.loaderCache.set(mc, { at: Date.now() - LOADERS_TTL + 60000, data });
      return data;
    }
  }

  /** The loader to use: an exact version, or the newest stable one for this Minecraft version. */
  async fabricLoader(mc, wanted) {
    if (wanted && wanted !== 'latest') return wanted;
    let list;
    try {
      list = await this.fabricLoaders(mc);
    } catch (e) {
      const local = this.localFabricLoaders(mc);
      if (local.length) return local[0];
      throw e;
    }
    const pick = list.find((e) => e.loader.stable) || list[0];
    if (!pick) throw new LauncherError('fabric_unsupported', mc, { version: mc });
    return pick.loader.version;
  }

  /** Fabric profiles already on disk for this Minecraft version, newest loader first. */
  localFabricLoaders(mc) {
    const suffix = `-${mc}`;
    let names = [];
    try {
      names = fs.readdirSync(this.paths.versions);
    } catch {
      return [];
    }
    return names
      .filter((n) => n.startsWith('fabric-loader-') && n.endsWith(suffix))
      .map((n) => n.slice('fabric-loader-'.length, n.length - suffix.length))
      .sort((a, b) => compareVersions(b, a));
  }

  async fabricProfile(mc, loader) {
    const id = `fabric-loader-${loader}-${mc}`;
    const file = path.join(this.paths.versions, id, `${id}.json`);
    let json = readJson(file, null);
    if (!json) {
      json = await net.getJson(`${FABRIC_META}/versions/loader/${encodeURIComponent(mc)}/${encodeURIComponent(loader)}/profile/json`);
      writeJson(file, json);
    }
    return json;
  }

  /** One version file for an installation: vanilla, or Fabric layered on top of it. */
  async resolve(inst) {
    const base = await this.vanilla(inst.version);
    if (inst.loader !== 'fabric') {
      return { ...base, vanillaId: base.id, loader: 'vanilla' };
    }
    const loader = await this.fabricLoader(inst.version, inst.loaderVersion);
    const child = await this.fabricProfile(inst.version, loader);
    return { ...merge(base, child), vanillaId: base.id, loader: 'fabric', loaderVersion: loader };
  }
}

/**
 * Applies a child profile (inheritsFrom) on top of its parent the way the official launcher
 * does: the child's libraries come first and its arguments are appended.
 */
function merge(parent, child) {
  const out = { ...parent, ...child };
  out.mainClass = child.mainClass || parent.mainClass;
  out.libraries = [...(child.libraries || []), ...(parent.libraries || [])];
  if (parent.arguments || child.arguments) {
    out.arguments = {
      ...(parent.arguments || {}),
      game: [...(parent.arguments?.game || []), ...(child.arguments?.game || [])],
      jvm: [...(parent.arguments?.jvm || []), ...(child.arguments?.jvm || [])],
    };
  }
  out.minecraftArguments = child.minecraftArguments || parent.minecraftArguments;
  out.assetIndex = parent.assetIndex;
  out.assets = parent.assets;
  out.downloads = parent.downloads;
  out.logging = parent.logging;
  out.javaVersion = child.javaVersion || parent.javaVersion;
  out.type = parent.type;
  return out;
}

module.exports = { Versions, merge };
