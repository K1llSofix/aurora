'use strict';

// Tools for whole installations: a copy to experiment in, a .mrpack to share with friends, and
// carrying settings (options.txt, Aurora's own, the server list) from one installation to another.

const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const net = require('./net');
const { ZipWriter, walk } = require('./zipwrite');

const MODRINTH = 'https://api.modrinth.com/v2';

/** Never copied or shared: logs, crashes, backups, and Aurora's private files (accounts, waypoints). */
const PRIVATE = [/^logs(\/|$)/, /^crash-reports(\/|$)/, /^backups(\/|$)/, /^config\/aurora\/(accounts|waypoints)\.json$/, /^quickPlay(\/|$)/, /(^|\/)session\.lock$/];

function isPrivate(rel) {
  return PRIVATE.some((re) => re.test(rel));
}

/** Copies a game folder for a new installation; worlds and screenshots only when asked for. */
async function cloneFolder(from, to, { worlds = false, screenshots = false } = {}) {
  await fsp.mkdir(to, { recursive: true });
  const files = await walk(from, (rel) => isPrivate(rel) || (!worlds && /^saves(\/|$)/.test(rel)) || (!screenshots && /^screenshots(\/|$)/.test(rel)));
  for (const rel of files) {
    const target = path.join(to, rel);
    await fsp.mkdir(path.dirname(target), { recursive: true });
    await fsp.copyFile(path.join(from, rel), target);
  }
  return files.length;
}

/** What can be carried from one installation to another, and which files that means. */
const SETTING_PARTS = {
  options: ['options.txt'],
  aurora: ['config/aurora.json', 'config/aurora/profiles'],
  servers: ['servers.dat'],
};

/** Copies the chosen settings; the files they replace are kept next to them as .bak. */
async function copySettings(from, to, parts) {
  let copied = 0;
  for (const part of parts) {
    for (const rel of SETTING_PARTS[part] || []) {
      const source = path.join(from, rel);
      if (!fs.existsSync(source)) continue;
      const stat = await fsp.stat(source);
      const files = stat.isDirectory() ? (await walk(source)).map((f) => `${rel}/${f}`) : [rel];
      for (const file of files) {
        const target = path.join(to, file);
        await fsp.mkdir(path.dirname(target), { recursive: true });
        if (fs.existsSync(target)) await fsp.copyFile(target, `${target}.bak`);
        await fsp.copyFile(path.join(from, file), target);
        copied++;
      }
    }
  }
  return copied;
}

/**
 * Writes an installation as a Modrinth modpack. Mods, resource packs and shader packs Modrinth
 * knows are listed by their download (the pack stays small); everything else, and the settings in
 * config/ and options.txt, go into overrides/.
 */
async function exportPack(gameDir, file, { name, version, minecraft, loader, loaderVersion, onProgress } = {}) {
  const content = [];
  for (const folder of ['mods', 'resourcepacks', 'shaderpacks']) {
    let names = [];
    try {
      names = await fsp.readdir(path.join(gameDir, folder));
    } catch {
      continue;
    }
    for (const n of names) {
      // Switched-off mods stay out of the pack.
      if (folder === 'mods' ? !/\.jar$/i.test(n) : !/\.zip$/i.test(n)) continue;
      const full = path.join(gameDir, folder, n);
      if ((await fsp.stat(full)).isFile()) content.push({ rel: `${folder}/${n}`, full, sha1: await net.sha1File(full) });
    }
  }
  let known = {};
  if (content.length) {
    try {
      const res = await net.postJson(`${MODRINTH}/version_files`, { hashes: content.map((c) => c.sha1), algorithm: 'sha1' }, { timeout: 20000 });
      if (res.ok) known = res.json;
    } catch {
      // Offline: every file goes into the pack itself.
    }
  }
  const files = [];
  const overrides = [];
  for (const c of content) {
    const version = known[c.sha1];
    const f = version && version.files.find((x) => x.hashes.sha1 === c.sha1);
    if (f && f.hashes.sha512) {
      files.push({ path: c.rel, hashes: { sha1: f.hashes.sha1, sha512: f.hashes.sha512 }, env: { client: 'required', server: 'optional' }, downloads: [f.url], fileSize: f.size });
    } else {
      overrides.push(c);
    }
  }
  const settings = (await walk(path.join(gameDir, 'config'))).map((rel) => `config/${rel}`).filter((rel) => !isPrivate(rel));
  if (fs.existsSync(path.join(gameDir, 'options.txt'))) settings.push('options.txt');

  const dependencies = { minecraft };
  if (loader === 'fabric' && loaderVersion) dependencies['fabric-loader'] = loaderVersion;
  const index = { formatVersion: 1, game: 'minecraft', versionId: version || '1.0.0', name, summary: 'Exported from Aurora Launcher', files, dependencies };

  const zip = await new ZipWriter(file).open();
  try {
    await zip.addBuffer('modrinth.index.json', Buffer.from(JSON.stringify(index, null, 2)));
    const all = [...overrides.map((c) => c.rel), ...settings];
    let done = 0;
    for (const rel of all) {
      await zip.addFile(`overrides/${rel}`, path.join(gameDir, rel));
      done++;
      if (onProgress) onProgress({ done, total: all.length });
    }
    await zip.close();
  } catch (e) {
    await zip.discard();
    throw e;
  }
  return { linked: files.length, included: overrides.length, settings: settings.length };
}

module.exports = { cloneFolder, copySettings, exportPack, SETTING_PARTS };
