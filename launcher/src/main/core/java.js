'use strict';

const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const { execFile } = require('child_process');
const net = require('./net');
const { readJson, writeJson } = require('./store');
const { LauncherError } = require('./errors');

const RUNTIMES = 'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';
const CATALOG_TTL = 60 * 60 * 1000;

function platformKey() {
  if (process.platform === 'win32') {
    return process.arch === 'arm64' ? 'windows-arm64' : process.arch === 'ia32' ? 'windows-x86' : 'windows-x64';
  }
  if (process.platform === 'darwin') return process.arch === 'arm64' ? 'mac-os-arm64' : 'mac-os';
  return process.arch === 'ia32' ? 'linux-i386' : 'linux';
}

/**
 * Where the java binary sits inside a runtime folder. On Windows this is java.exe, not javaw.exe:
 * javaw reports a JVM that cannot start (a bad flag, too much memory) in a modal dialog and waits
 * for a click, while java.exe prints the reason and exits. The launcher hides the console.
 */
function executableIn(dir) {
  if (process.platform === 'win32') return path.join(dir, 'bin', 'java.exe');
  if (process.platform === 'darwin') return path.join(dir, 'jre.bundle', 'Contents', 'Home', 'bin', 'java');
  return path.join(dir, 'bin', 'java');
}

/**
 * The Java runtimes Mojang publishes for each game version (java-runtime-epsilon is Java 25),
 * installed per component under runtime/ so players never need their own Java.
 */
class JavaRuntimes {
  constructor(paths) {
    this.paths = paths;
    this.catalog = null;
  }

  async loadCatalog() {
    if (this.catalog && Date.now() - this.catalog.at < CATALOG_TTL) return this.catalog.data;
    const file = path.join(this.paths.cache, 'java-runtimes.json');
    try {
      const data = await net.getJson(RUNTIMES, { timeout: 15000, retries: 2 });
      writeJson(file, data);
      this.catalog = { at: Date.now(), data };
      return data;
    } catch (e) {
      const data = readJson(file, null);
      if (!data) throw e;
      this.catalog = { at: Date.now() - CATALOG_TTL + 60000, data };
      return data;
    }
  }

  /**
   * Works out what a runtime still needs: the files to download and a finish step that runs
   * after them. Nothing is downloaded here, so the caller can show one progress bar for all.
   */
  async plan(component, { verify = false } = {}) {
    const dir = path.join(this.paths.runtime, component);
    const javaPath = executableIn(dir);
    const markerFile = path.join(dir, '.aurora-runtime.json');
    const marker = readJson(markerFile, null);
    const ready = { javaPath, files: [], finish: async () => {} };
    let catalog;
    try {
      catalog = await this.loadCatalog();
    } catch (e) {
      if (marker && fs.existsSync(javaPath)) return ready;
      throw e;
    }
    let entry = catalog[platformKey()]?.[component]?.[0];
    if (!entry && platformKey() === 'mac-os-arm64') entry = catalog['mac-os']?.[component]?.[0];
    if (!entry) {
      if (marker && fs.existsSync(javaPath)) return ready;
      throw new LauncherError('java_unavailable', `${component} for ${platformKey()}`, { component });
    }
    if (!verify && marker && marker.sha1 === entry.manifest.sha1 && fs.existsSync(javaPath)) {
      return ready;
    }
    const manifest = await net.getJson(entry.manifest.url, { timeout: 20000 });
    const files = [];
    const dirs = [];
    const links = [];
    const executables = [];
    for (const [name, info] of Object.entries(manifest.files)) {
      const target = path.join(dir, ...name.split('/'));
      if (info.type === 'directory') {
        dirs.push(target);
      } else if (info.type === 'link') {
        links.push({ path: target, target: info.target });
      } else if (info.type === 'file') {
        const raw = info.downloads.raw;
        const item = { url: raw.url, path: target, sha1: raw.sha1, size: raw.size };
        if (info.executable) executables.push(target);
        if (!(await net.fileMatches(target, item, verify))) files.push(item);
      }
    }
    return {
      javaPath,
      files,
      finish: async () => {
        for (const d of dirs) await fsp.mkdir(d, { recursive: true });
        if (process.platform !== 'win32') {
          for (const f of executables) await fsp.chmod(f, 0o755).catch(() => {});
          for (const l of links) {
            await fsp.rm(l.path, { force: true }).catch(() => {});
            await fsp.symlink(l.target, l.path).catch(() => {});
          }
        }
        writeJson(markerFile, { component, version: entry.version.name, sha1: entry.manifest.sha1 });
      },
    };
  }
}

/** java.exe next to a javaw.exe someone picked, for the reason given at executableIn. */
function consoleJava(javaPath) {
  if (process.platform !== 'win32' || !/javaw\.exe$/i.test(javaPath)) return javaPath;
  const java = javaPath.replace(/javaw\.exe$/i, 'java.exe');
  return fs.existsSync(java) ? java : javaPath;
}

/** Asks a java binary for its version: {major, version} or null if it does not run. */
function probe(javaPath) {
  return new Promise((resolve) => {
    const exe = consoleJava(javaPath);
    execFile(exe, ['-XshowSettings:properties', '-version'], { timeout: 15000, windowsHide: true }, (err, stdout, stderr) => {
      const text = `${stdout}\n${stderr}`;
      const m = text.match(/java\.version = ([^\s]+)/) || text.match(/version "([^"]+)"/);
      if (!m) return resolve(null);
      const version = m[1];
      const parts = version.split(/[.\-_+]/);
      const major = parts[0] === '1' ? parseInt(parts[1], 10) : parseInt(parts[0], 10);
      const vendor = (text.match(/java\.vendor = (.+)/) || [])[1] || '';
      resolve({ version, major, vendor: vendor.trim() });
    });
  });
}

module.exports = { JavaRuntimes, probe, executableIn, platformKey, consoleJava };
