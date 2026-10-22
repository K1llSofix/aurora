'use strict';

// Updates of the launcher itself. Every release on GitHub carries the installer and latest.yml
// (version, installer name, SHA-512 and size, written by scripts/update-info.js after the build).
// The launcher reads latest.yml of the newest release now and then, downloads a newer installer
// in the background, checks it, and runs it without its windows: right away when the player asks
// (the launcher then starts again), otherwise when the launcher quits.

const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const crypto = require('crypto');
const { spawn } = require('child_process');
const { EventEmitter } = require('events');
const net = require('./net');
const { compareVersions } = require('./rules');
const { describe } = require('./errors');

const FIRST_CHECK = 15 * 1000;
const EVERY = 6 * 60 * 60 * 1000;

/** The fields of electron-builder's latest.yml the launcher needs; null if they are not all there. */
function parseLatest(text) {
  const field = (name) => {
    const m = new RegExp(`^${name}:[ \\t]*(.+?)[ \\t]*$`, 'm').exec(String(text));
    return m ? m[1].replace(/^['"]|['"]$/g, '') : null;
  };
  const size = /^[ \t]+size:[ \t]*(\d+)[ \t]*$/m.exec(String(text));
  const info = { version: field('version'), path: field('path'), sha512: field('sha512'), size: size ? Number(size[1]) : null };
  // The installer is fetched by this name from the same release: a bare file name only.
  if (!info.version || !info.sha512 || !/^[\w.\-]+\.exe$/i.test(info.path || '')) return null;
  return info;
}

async function sha512File(file) {
  const hash = crypto.createHash('sha512');
  for await (const chunk of fs.createReadStream(file)) hash.update(chunk);
  return hash.digest('base64');
}

class Updater extends EventEmitter {
  /**
   * @param feed where the newest release's files are (null turns updates off)
   * @param version the running launcher's version
   * @param dir the folder for the downloaded installer
   */
  constructor({ feed, version, dir, log = () => {} }) {
    super();
    this.feed = feed ? feed.replace(/\/+$/, '') : null;
    this.version = version;
    this.dir = dir;
    this.log = log;
    this.file = null;
    this.timer = null;
    this.running = null;
    this.installing = false;
    this.state = { status: this.feed ? 'idle' : 'off', current: version, version: null, progress: null, error: null, checkedAt: 0 };
  }

  set(patch) {
    this.state = { ...this.state, ...patch };
    this.emit('change', this.state);
  }

  /** Checks on its own (soon after the start, then every few hours) while {@code on}. */
  schedule(on) {
    clearTimeout(this.timer);
    this.timer = null;
    if (!on || !this.feed) return;
    const wait = this.state.checkedAt ? Math.max(FIRST_CHECK, this.state.checkedAt + EVERY - Date.now()) : FIRST_CHECK;
    this.timer = setTimeout(() => {
      this.check().finally(() => this.schedule(true));
    }, wait);
  }

  /** Looks for a newer release and downloads it; resolves to the new state, never rejects. */
  check() {
    if (!this.feed || this.state.status === 'ready') return Promise.resolve(this.state);
    if (!this.running) this.running = this.run().finally(() => { this.running = null; });
    return this.running;
  }

  async run() {
    this.set({ status: 'checking', error: null });
    try {
      const latest = parseLatest(await net.getText(`${this.feed}/latest.yml`, { timeout: 15000, retries: 2 }));
      if (!latest) throw new Error('latest.yml of the release cannot be read');
      if (compareVersions(latest.version, this.version) <= 0) {
        await this.clean(null);
        this.set({ status: 'none', version: null, progress: null, checkedAt: Date.now() });
        return this.state;
      }
      const file = path.join(this.dir, latest.path);
      if (!(await this.matches(file, latest))) {
        this.set({ status: 'downloading', version: latest.version, progress: 0 });
        await net.downloadAll([{ url: `${this.feed}/${encodeURIComponent(latest.path)}`, path: file, size: latest.size || undefined }], {
          onProgress: (p) => this.set({ progress: p.totalBytes ? p.doneBytes / p.totalBytes : null }),
        });
        if (!(await this.matches(file, latest))) {
          await fsp.rm(file, { force: true });
          throw new Error(`Checksum mismatch for ${latest.path}`);
        }
      }
      await this.clean(file);
      this.file = file;
      this.set({ status: 'ready', version: latest.version, progress: 1, checkedAt: Date.now() });
    } catch (e) {
      this.log(`update: ${e && e.stack ? e.stack : e}`);
      // No release with latest.yml yet (or no repository): for the player there is simply no update.
      if (e && e.status === 404) this.set({ status: 'none', version: null, progress: null, checkedAt: Date.now() });
      else this.set({ status: 'error', progress: null, error: describe(e), checkedAt: Date.now() });
    }
    return this.state;
  }

  async matches(file, latest) {
    try {
      const st = await fsp.stat(file);
      if (latest.size && st.size !== latest.size) return false;
      return (await sha512File(file)) === latest.sha512;
    } catch {
      return false;
    }
  }

  /** Removes everything in the download folder but {@code keep}: older installers and leftovers. */
  async clean(keep) {
    let names = [];
    try {
      names = await fsp.readdir(this.dir);
    } catch {
      return;
    }
    for (const name of names) {
      const file = path.join(this.dir, name);
      if (file !== keep) await fsp.rm(file, { force: true, recursive: true }).catch(() => {});
    }
  }

  /**
   * Starts the downloaded installer without its windows; with {@code relaunch} it opens the
   * launcher again when done. The caller quits right after, so the files can be replaced.
   */
  install({ relaunch = false } = {}) {
    if (this.state.status !== 'ready' || !this.file || this.installing) return false;
    this.installing = true;
    // electron-builder's installer: /S is silent, --updated keeps the folder and settings,
    // --force-run starts the app again after a silent install.
    const args = ['/S', '--updated', ...(relaunch ? ['--force-run'] : [])];
    if (process.env.AURORA_UPDATE_DRY) {
      this.log(`update: would run ${this.file} ${args.join(' ')}`);
      return true;
    }
    spawn(this.file, args, { detached: true, stdio: 'ignore' }).unref();
    this.log(`update: started ${path.basename(this.file)} ${args.join(' ')}`);
    return true;
  }
}

module.exports = { Updater, parseLatest };
