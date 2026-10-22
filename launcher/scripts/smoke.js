'use strict';

// Installs and (with --launch) starts the default Aurora installation without the UI.
// node scripts/smoke.js [--launch] [--verify]

const path = require('path');
const crypto = require('crypto');
const paths = require('../src/main/core/paths').create();
const { Versions } = require('../src/main/core/versions');
const { JavaRuntimes } = require('../src/main/core/java');
const { Mods } = require('../src/main/core/mods');
const { Installer } = require('../src/main/core/installer');
const { buildCommand, redact, GameProcess } = require('../src/main/core/launch');

function offlineUuid(name) {
  const md5 = crypto.createHash('md5').update(`OfflinePlayer:${name}`, 'utf8').digest();
  md5[6] = (md5[6] & 0x0f) | 0x30;
  md5[8] = (md5[8] & 0x3f) | 0x80;
  const h = md5.toString('hex');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

(async () => {
  const versions = new Versions(paths);
  const java = new JavaRuntimes(paths);
  const mods = new Mods({ bundledDir: path.join(__dirname, '..', '..', 'build', 'libs') });
  const installer = new Installer({ paths, versions, java, mods });
  const inst = { id: 'aurora', name: 'Aurora', version: '26.3', loader: 'fabric', loaderVersion: 'latest', aurora: true };
  const gameDir = path.join(paths.instances, inst.id);
  const t0 = Date.now();
  let last = 0;
  let lastStage = '';
  const prepared = await installer.prepare(inst, {
    gameDir,
    verify: process.argv.includes('--verify'),
    onProgress: (p) => {
      const now = Date.now();
      if (p.stage !== lastStage || now - last > 2000) {
        last = now;
        lastStage = p.stage;
        const extra = p.stage === 'download' ? ` ${p.doneFiles}/${p.totalFiles} files, ${(p.doneBytes / 1e6).toFixed(1)}/${(p.totalBytes / 1e6).toFixed(1)} MB` : p.total ? ` ${p.total}` : '';
        console.log(`[${((now - t0) / 1000).toFixed(1)}s] ${p.stage}${extra}`);
      }
    },
  });
  console.log(`prepared in ${((Date.now() - t0) / 1000).toFixed(1)}s: java=${prepared.javaPath}, ${prepared.classpath.length} classpath entries, loader ${prepared.version.loaderVersion}`);
  console.log('mods:', (await mods.list(gameDir)).map((m) => `${m.name} ${m.version}${m.managed ? ' (managed)' : ''}`).join(', '));
  const cmd = buildCommand(prepared, {
    account: { name: 'AuroraTester', uuid: offlineUuid('AuroraTester'), accessToken: '0', type: 'offline' },
    memory: 4096,
    jvmArgs: '',
    launcherVersion: '1.0.0',
  });
  console.log('command:', [cmd.java, ...redact(cmd.args)].map((a) => (a.length > 160 ? a.slice(0, 160) + '…' : a)).join('\n  '));
  if (!process.argv.includes('--launch')) return;
  const game = new GameProcess(cmd, { logFile: path.join(paths.logs, 'smoke.log') }).start();
  console.log('pid', game.pid);
  game.on('log', (e) => {
    if (/WARN|ERROR|FATAL/.test(e.level) || /Aurora|Loading Minecraft|Setting user|Created window|Fabric/i.test(e.message)) {
      console.log(`  [${e.time}] [${e.level}] ${e.message.split('\n')[0].slice(0, 200)}`);
    }
  });
  game.on('window', () => console.log(`>>> window up after ${((Date.now() - game.started) / 1000).toFixed(1)}s`));
  game.on('exit', (r) => {
    console.log('>>> exit', r);
    process.exit(0);
  });
  const seconds = Number((process.argv.find((a) => a.startsWith('--seconds=')) || '--seconds=60').split('=')[1]);
  setTimeout(() => {
    console.log('>>> stopping the game');
    game.kill();
  }, seconds * 1000);
})().catch((e) => {
  console.error('FAILED', e);
  process.exit(1);
});
