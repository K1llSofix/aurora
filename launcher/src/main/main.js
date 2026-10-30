'use strict';

const { app, BrowserWindow, ipcMain, shell, dialog, protocol, net: electronNet, safeStorage, Tray, Menu, nativeImage, clipboard } = require('electron');
const fs = require('fs');
const path = require('path');
const os = require('os');
const { pathToFileURL } = require('url');

const net = require('./core/net');
const { JsonStore } = require('./core/store');
const { LauncherError, describe } = require('./core/errors');
const { Versions } = require('./core/versions');
const { JavaRuntimes, probe, consoleJava } = require('./core/java');
const { Mods, readMeta } = require('./core/mods');
const { Packs } = require('./core/packs');
const { Modpacks, readPack, installPack } = require('./core/modpacks');
const { Installer } = require('./core/installer');
const { buildCommand, GameProcess } = require('./core/launch');
const { Accounts } = require('./core/accounts');
const { Skins } = require('./core/skins');
const { Icons, CATALOG } = require('./core/icons');
const { ServerPinger, parseAddress } = require('./core/servers');
const { News } = require('./core/news');
const { DiscordPresence } = require('./core/discord');
const { Updater } = require('./core/updater');
const { ContentUpdates } = require('./core/content-updates');
const worlds = require('./core/worlds');
const { diagnose } = require('./core/crash');
const installTools = require('./core/install-tools');
const gpu = require('./core/gpu');
const changelog = require('./core/changelog');
const defaults = require('./defaults');

const APP_DIR = path.join(__dirname, '..', '..');
const RENDERER = path.join(APP_DIR, 'src', 'renderer');
const DEV = !app.isPackaged || process.argv.includes('--dev');
const paths = require('./core/paths').create();
const BUNDLED_MODS = app.isPackaged ? path.join(process.resourcesPath, 'mods') : path.join(APP_DIR, '..', 'build', 'libs');
const WINDOW_ICON = path.join(APP_DIR, 'build', 'icon.png');
const LAUNCHER_CHANGELOG = path.join(APP_DIR, 'CHANGELOG.md');
const MOD_CHANGELOG = app.isPackaged ? path.join(process.resourcesPath, 'mods', 'CHANGELOG.md') : path.join(APP_DIR, '..', 'CHANGELOG.md');

protocol.registerSchemesAsPrivileged([
  { scheme: 'app', privileges: { standard: true, secure: true, supportFetchAPI: true, codeCache: true } },
]);

if (!app.requestSingleInstanceLock()) {
  // A second start (a game shortcut, the Start menu) hands its arguments to the running launcher.
  app.quit();
  process.exit(0);
}

app.setAppUserModelId('dev.aurora.launcher');

// ---------------------------------------------------------------------------- services

const mods = new Mods({ bundledDir: BUNDLED_MODS, auroraProject: defaults.AURORA_MODRINTH, cacheDir: paths.cache });
const packs = new Packs();
const modpacks = new Modpacks({ cacheDir: paths.cache });
const versions = new Versions(paths);
const java = new JavaRuntimes(paths);
const installer = new Installer({ paths, versions, java, mods });
const pinger = new ServerPinger();
const news = new News({ cacheDir: paths.cache, bundled: defaults.AURORA_NEWS });
const skins = new Skins({ dir: paths.skins, cacheDir: paths.cache });
const icons = new Icons({ iconsDir: paths.icons, versionsDir: paths.versions });
const contentUpdates = new ContentUpdates({
  trash: (file) => shell.trashItem(file),
  renamePack: (gameDir, kind, from, to) => packs.renamePack(gameDir, kind, from, to),
});

function auroraMinecraft() {
  const bundled = mods.bundled();
  const range = bundled && bundled.minecraft;
  return range ? String(range).replace(/^[~^>=\s]+/, '') : '26.3';
}

const settings = new JsonStore(paths.settings, defaults.defaultSettings(auroraMinecraft()));
if (!Array.isArray(settings.data.installs) || settings.data.installs.length === 0) {
  settings.data.installs = [defaults.defaultInstall(auroraMinecraft())];
}

const accounts = new Accounts({
  file: paths.accounts,
  versionsDir: paths.versions,
  cacheDir: paths.cache,
  protect: (text) => (safeStorage.isEncryptionAvailable() ? `enc:${safeStorage.encryptString(text).toString('base64')}` : `plain:${text}`),
  unprotect: (value) => {
    if (!value) return null;
    if (value.startsWith('plain:')) return value.slice(6);
    try {
      return safeStorage.decryptString(Buffer.from(value.slice(4), 'base64'));
    } catch {
      return null;
    }
  },
});

function writeLog(text) {
  try {
    fs.mkdirSync(paths.logs, { recursive: true });
    fs.appendFileSync(path.join(paths.logs, 'launcher.log'), `[${new Date().toISOString()}] ${text}\n`);
  } catch {
    // Nothing sensible left to do.
  }
}

function trace(message) {
  writeLog(message);
}

function logError(where, e) {
  writeLog(`${where}: ${e && e.stack ? e.stack : e}`);
}

function installById(id) {
  return settings.data.installs.find((i) => i.id === id) || null;
}

function selectedInstall() {
  return installById(settings.data.selectedInstall) || settings.data.installs[0];
}

function gameDirOf(inst) {
  return inst.gameDir || path.join(paths.instances, inst.id);
}

// ---------------------------------------------------------------------------- windows

let win = null;
let splash = null;
let tray = null;
let quitting = false;
/** Started from a game shortcut with no launcher window: quit once the game is closed. */
let direct = false;

function send(channel, payload) {
  for (const w of [win, splash]) {
    if (w && !w.isDestroyed()) w.webContents.send(channel, payload);
  }
}

function webPreferences() {
  return {
    preload: path.join(__dirname, 'preload.js'),
    contextIsolation: true,
    nodeIntegration: false,
    sandbox: true,
    spellcheck: false,
    devTools: DEV,
    // Screenshot scripts render the window off screen, without ever showing it.
    offscreen: Boolean(process.env.AURORA_SCRIPT && process.env.AURORA_HIDDEN),
    backgroundThrottling: !process.env.AURORA_SCRIPT,
  };
}

function guard(w) {
  w.webContents.setWindowOpenHandler(({ url }) => {
    openExternal(url);
    return { action: 'deny' };
  });
  w.webContents.on('will-navigate', (event, url) => {
    if (!url.startsWith('app://')) {
      event.preventDefault();
      openExternal(url);
    }
  });
}

function createWindow() {
  const saved = settings.data.window || {};
  win = new BrowserWindow({
    width: Math.max(1040, saved.width || 1280),
    height: Math.max(660, saved.height || 780),
    x: saved.x,
    y: saved.y,
    minWidth: 1040,
    minHeight: 660,
    frame: false,
    show: false,
    backgroundColor: '#060814',
    title: 'Aurora Launcher',
    icon: WINDOW_ICON,
    webPreferences: webPreferences(),
  });
  if (saved.maximized) win.maximize();
  win.loadURL('app://launcher/index.html');
  win.once('ready-to-show', () => {
    if (!process.env.AURORA_HIDDEN) win.show();
  });
  guard(win);
  if (DEV) {
    win.webContents.on('before-input-event', (event, input) => {
      if (input.type === 'keyDown' && input.control && input.shift && input.key.toLowerCase() === 'i') win.webContents.toggleDevTools();
      if (input.type === 'keyDown' && input.key === 'F5') win.webContents.reload();
    });
  }
  const remember = () => {
    if (!win || win.isDestroyed() || win.isMinimized()) return;
    const maximized = win.isMaximized();
    const bounds = maximized ? settings.data.window || {} : win.getBounds();
    settings.data.window = { width: bounds.width, height: bounds.height, x: bounds.x, y: bounds.y, maximized };
    settings.save();
  };
  win.on('resize', remember);
  win.on('move', remember);
  win.on('maximize', () => send('window:state', { maximized: true }));
  win.on('unmaximize', () => send('window:state', { maximized: false }));
  win.on('close', () => {
    if (!quitting && game.state !== 'idle' && game.state !== 'running') {
      // Closing mid-install would leave half-written files; cancel first.
      game.controller?.abort();
    }
  });
  win.on('closed', () => {
    win = null;
  });
}

function showWindow() {
  direct = false;
  if (!win) createWindow();
  if (win.isMinimized()) win.restore();
  win.show();
  win.focus();
  destroyTray();
}

/** The small card shown while a game shortcut prepares and starts the game. */
function createSplash(installId) {
  if (splash) return;
  splash = new BrowserWindow({
    width: 440,
    height: 176,
    frame: false,
    resizable: false,
    maximizable: false,
    transparent: true,
    backgroundColor: '#00000000',
    alwaysOnTop: true,
    show: false,
    title: 'Aurora',
    icon: WINDOW_ICON,
    webPreferences: webPreferences(),
  });
  splash.loadURL(`app://launcher/splash.html?install=${encodeURIComponent(installId)}`);
  splash.once('ready-to-show', () => splash && splash.show());
  guard(splash);
  splash.on('closed', () => {
    splash = null;
  });
}

function closeSplash() {
  if (splash && !splash.isDestroyed()) splash.close();
}

function trayIcon() {
  const image = nativeImage.createFromPath(WINDOW_ICON);
  return image.isEmpty() ? image : image.resize({ width: 16, height: 16 });
}

/** Whether the interface speaks Russian (the main process has no dictionary of its own). */
function russian() {
  const lang = settings.data.language === 'auto' ? app.getLocale() : settings.data.language;
  return /^(ru|uk|be|kk)/i.test(lang || '');
}

/** Tray menu text in the interface language. */
function trayText() {
  return russian()
    ? { open: 'Открыть Aurora', stop: 'Остановить игру', quit: 'Выйти' }
    : { open: 'Open Aurora', stop: 'Stop the game', quit: 'Quit' };
}

function ensureTray() {
  const text = trayText();
  if (!tray) {
    tray = new Tray(trayIcon());
    tray.setToolTip('Aurora Launcher');
    tray.on('click', showWindow);
  }
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: text.open, click: showWindow },
    { label: text.stop, click: () => killGame(), enabled: Boolean(game.process) },
    { type: 'separator' },
    { label: text.quit, click: () => { quitting = true; app.quit(); } },
  ]));
}

function destroyTray() {
  if (tray) {
    tray.destroy();
    tray = null;
  }
}

function openExternal(url) {
  try {
    const parsed = new URL(url);
    if (parsed.protocol === 'https:' || parsed.protocol === 'http:') shell.openExternal(parsed.toString());
  } catch {
    // Not a URL: ignore.
  }
}

// ---------------------------------------------------------------------------- updates

// The installed launcher follows the releases of LAUNCHER_REPO; a development run only checks a
// test feed given in AURORA_UPDATE_FEED.
const updater = new Updater({
  feed: DEV
    ? process.env.AURORA_UPDATE_FEED || null
    : defaults.LAUNCHER_REPO ? `https://github.com/${defaults.LAUNCHER_REPO}/releases/latest/download` : null,
  version: app.getVersion(),
  dir: path.join(paths.cache, 'launcher-update'),
  log: trace,
});
updater.on('change', (s) => send('updates:state', s));

// ---------------------------------------------------------------------------- Discord

const presence = new DiscordPresence(defaults.DISCORD_APP_ID, trace);
const launcherStartedAt = Date.now();

/**
 * The Discord status: the launcher itself, or a game without Aurora. A game with Aurora shows the
 * mod's own, more detailed status, so the launcher steps aside while it runs.
 */
function updatePresence() {
  // Development scripts (screenshots, the trailer) drive the window on their own: no status for them.
  presence.setEnabled(settings.data.discord !== false && !process.env.AURORA_SCRIPT);
  const ru = russian();
  const inst = game.installId ? installById(game.installId) : null;
  const assets = { large_image: 'aurora', large_text: `Aurora Launcher ${app.getVersion()}` };
  // A link under the status for whoever looks at the profile.
  const buttons = [{ label: ru ? 'Скачать Aurora Client' : 'Get Aurora Client', url: defaults.AURORA_PAGE }];
  if (game.state === 'idle' || !inst) {
    presence.set({ type: 0, details: ru ? 'В лаунчере Aurora' : 'In the Aurora Launcher', assets, buttons, timestamps: { start: launcherStartedAt } });
    return;
  }
  if (game.state !== 'running') {
    presence.set({ type: 0, details: `${ru ? 'Запускает' : 'Starting'} Minecraft ${inst.version}`, assets, buttons });
    return;
  }
  if (inst.aurora && mods.auroraSupports(inst.version)) {
    presence.set(null);
    return;
  }
  const loader = inst.loader === 'fabric' ? 'Fabric' : (ru ? 'Без модов' : 'Vanilla');
  presence.set({ type: 0, details: `Minecraft ${inst.version}`, state: loader, assets, buttons, timestamps: { start: game.startedAt || Date.now() } });
}

// ---------------------------------------------------------------------------- game

const game = {
  state: 'idle',
  startedAt: 0,
  installId: null,
  process: null,
  controller: null,
  killed: false,
  lines: [],
  lastExit: null,
  lastError: null,
};

function gameState(extra = {}) {
  return { state: game.state, installId: game.installId, pid: game.process ? game.process.pid : null, ...extra };
}

function setGameState(state, extra = {}) {
  game.state = state;
  if (state === 'starting') game.startedAt = Date.now();
  if (state === 'idle') game.startedAt = 0;
  send('game:state', gameState(extra));
  updatePresence();
  if (state === 'running') closeSplash();
  if (tray) ensureTray();
}

let logBatch = [];
let logTimer = null;

function pushLog(entry) {
  game.lines.push(entry);
  if (game.lines.length > 4000) game.lines.splice(0, game.lines.length - 4000);
  logBatch.push(entry);
  if (!logTimer) {
    logTimer = setTimeout(() => {
      send('game:log', logBatch);
      logBatch = [];
      logTimer = null;
    }, 120);
  }
}

function newestCrashReport(gameDir, since) {
  try {
    const dir = path.join(gameDir, 'crash-reports');
    const files = fs.readdirSync(dir).map((f) => ({ f, t: fs.statSync(path.join(dir, f)).mtimeMs })).filter((x) => x.t >= since - 1000);
    files.sort((a, b) => b.t - a.t);
    return files.length ? path.join(dir, files[0].f) : null;
  } catch {
    return null;
  }
}

function speedMeter() {
  const samples = [];
  return (bytes) => {
    const now = Date.now();
    samples.push([now, bytes]);
    while (samples.length > 2 && now - samples[0][0] > 3000) samples.shift();
    const [t0, b0] = samples[0];
    return now > t0 ? ((bytes - b0) * 1000) / (now - t0) : 0;
  };
}

async function launch({ installId, server = null, world = null, verify = false } = {}) {
  if (game.state !== 'idle') throw new LauncherError('busy');
  const inst = installById(installId) || selectedInstall();
  const account = accounts.current();
  if (!account) throw new LauncherError('no_account');
  if (server) {
    const { host, port } = parseAddress(server);
    server = port ? `${host}:${port}` : host;
  }
  if (world) world = worlds.safeFolder(world);
  game.installId = inst.id;
  game.killed = false;
  game.lines = [];
  game.lastExit = null;
  game.lastError = null;
  game.controller = new AbortController();
  const signal = game.controller.signal;
  setGameState('preparing');
  const meter = speedMeter();
  try {
    const gameDir = gameDirOf(inst);
    const customJava = settings.data.javaMode === 'custom' && settings.data.javaPath ? consoleJava(settings.data.javaPath) : null;
    const prepared = await installer.prepare(inst, {
      gameDir,
      javaPath: customJava,
      verify,
      signal,
      onProgress: (p) => {
        if (p.stage === 'download') p.speed = meter(p.doneBytes);
        send('game:progress', p);
      },
    });
    signal.throwIfAborted();
    icons.forget();
    const session = await accounts.session(account.id);
    await applyGpu(prepared.javaPath);
    setGameState('starting');
    const command = buildCommand(prepared, {
      account: session,
      memory: inst.memory || settings.data.memory,
      jvmArgs: [settings.data.jvmArgs, inst.jvmArgs].filter(Boolean).join(' '),
      resolution: inst.resolution || settings.data.resolution,
      server,
      world,
      launcherVersion: app.getVersion(),
      properties: { 'aurora.skins': paths.skins },
    });
    const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const proc = new GameProcess(command, { logFile: path.join(paths.logs, `game-${stamp}.log`) });
    game.process = proc;
    proc.on('log', pushLog);
    proc.on('trace', trace);
    proc.on('window', () => {
      trace(`game ${proc.pid}: window is up after ${Date.now() - proc.started} ms`);
      setGameState('running');
      afterLaunch();
    });
    proc.on('failed', (e) => {
      logError('spawn', e);
      game.process = null;
      failed(describe(new LauncherError('launch_failed', e.message)));
    });
    proc.on('exit', async ({ code, runtime }) => {
      trace(`game ${proc.pid}: exited with ${code} after ${runtime} ms${game.killed ? ' (stopped by the player)' : ''}`);
      const crashed = !game.killed && code !== 0 && code !== null;
      const report = crashed ? newestCrashReport(gameDir, proc.started) : null;
      const tail = crashed ? game.lines.slice(-80) : [];
      const diagnosis = crashed ? await diagnoseCrash(inst, gameDir, report).catch(() => []) : [];
      game.process = null;
      game.lastExit = { code, runtime, crashed, report, tail, diagnosis, installId: inst.id, seen: false };
      setGameState('idle', { exit: game.lastExit });
      closeSplash();
      pruneGameLogs();
      if (direct && !crashed) {
        // A shortcut start ends with the game, like starting the game itself.
        quitting = true;
        app.quit();
        return;
      }
      if (settings.data.afterLaunch !== 'keep' || crashed || direct) showWindow();
    });
    proc.start();
    trace(`game ${proc.pid}: started ${inst.name} (${inst.version}, ${inst.loader}) as ${session.name}`);
    inst.lastPlayed = Date.now();
    settings.save();
    // Hide the launcher even if the game never prints a line we recognise.
    setTimeout(() => {
      if (game.process === proc && game.state === 'starting') {
        trace(`game ${proc.pid}: no window line after 25 s, treating it as running`);
        setGameState('running');
        afterLaunch();
      }
    }, 25000);
    return { pid: proc.pid };
  } catch (e) {
    if (!(e instanceof LauncherError) && !signal.aborted) logError('launch', e);
    game.process = null;
    failed(describe(signal.aborted ? new LauncherError('cancelled') : e));
    return null;
  } finally {
    game.controller = null;
  }
}

/** The installations' cards ask for fresh data (mod counts, Aurora's version). */
function refreshInstallsSoon() {
  send('app:event', { type: 'installsChanged' });
}

/** What most likely made the game crash, read from its log and crash report. */
async function diagnoseCrash(inst, gameDir, report) {
  const text = game.lines.slice(-3000).map((e) => e.message || '').join('\n');
  let reportText = '';
  if (report) {
    try {
      reportText = fs.readFileSync(report, 'utf8').slice(0, 300000);
    } catch {
      reportText = '';
    }
  }
  let list = [];
  try {
    list = (await mods.list(gameDir)).filter((m) => m.enabled && !m.managed).map((m) => ({ id: m.id, name: m.name, file: m.file }));
  } catch {
    list = [];
  }
  const total = Math.floor(os.totalmem() / 1024 ** 2);
  return diagnose({
    text: `${text}\n${reportText}`,
    mods: list,
    memory: inst.memory || settings.data.memory,
    maxMemory: Math.max(2048, Math.min(32768, Math.floor((total - 1024) / 512) * 512)),
    customJava: settings.data.javaMode === 'custom',
  });
}

/** Which graphics card Windows gives the Java that runs the game (laptops: the discrete one). */
async function applyGpu(exe) {
  if (process.platform !== 'win32' || !exe) return;
  const mode = settings.data.gpu || 'auto';
  const ours = new Set(settings.data.gpuApplied || []);
  if (mode === 'auto' && !ours.has(exe)) return;
  const ok = await gpu.setPreference(exe, mode, ours.has(exe));
  if (!ok) return;
  if (mode === 'auto') ours.delete(exe);
  else ours.add(exe);
  settings.data.gpuApplied = [...ours];
  settings.save();
}

/** A launch that did not get to the game: the splash (if any) shows why. */
function failed(error) {
  game.lastError = error;
  setGameState('idle', { error });
  if (!splash) showWindow();
}

function afterLaunch() {
  if (!win) {
    // Started from a shortcut: a tray icon still lets the player open the launcher or stop the game.
    if (direct) ensureTray();
    return;
  }
  switch (settings.data.afterLaunch) {
    case 'hide':
      ensureTray();
      win.hide();
      break;
    case 'minimize':
      win.minimize();
      break;
    case 'close':
      quitting = true;
      setTimeout(() => app.quit(), 500);
      break;
    default:
      break;
  }
}

function killGame() {
  if (!game.process) return;
  trace(`game ${game.process.pid}: stop requested`);
  game.killed = true;
  game.process.kill();
}

/** Keeps the last 20 game logs the launcher wrote. */
function pruneGameLogs() {
  try {
    const files = fs.readdirSync(paths.logs).filter((f) => /^game-.*\.log$/.test(f)).sort();
    for (const f of files.slice(0, Math.max(0, files.length - 20))) fs.rmSync(path.join(paths.logs, f), { force: true });
  } catch {
    // Not important.
  }
}

// ---------------------------------------------------------------------------- shortcuts

/** `--launch <installation>` (or `--launch=<id>`) from a game shortcut. */
function parseLaunch(argv) {
  const i = argv.findIndex((a) => a === '--launch' || a.startsWith('--launch='));
  if (i < 0) return null;
  const id = argv[i].includes('=') ? argv[i].slice(argv[i].indexOf('=') + 1) : argv[i + 1];
  return id && /^[\w-]+$/.test(id) ? { installId: id } : null;
}

/** Starts an installation straight away, showing only a small progress card. */
function directLaunch({ installId }) {
  const inst = installById(installId);
  if (!inst || !accounts.current()) {
    // A deleted installation or no account yet: the launcher itself explains what to do.
    showWindow();
    if (!accounts.current()) win.webContents.once('did-finish-load', () => send('app:event', { type: 'needAccount' }));
    return;
  }
  if (game.state !== 'idle') {
    if (win) showWindow();
    return;
  }
  if (win && win.isVisible()) {
    win.focus();
  } else {
    direct = !win;
    createSplash(inst.id);
  }
  launch({ installId: inst.id }).catch((e) => logError('direct launch', e));
}

function shortcutName(inst) {
  return `${inst.name.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '').trim() || 'Minecraft'}.lnk`;
}

/** Where shortcuts go: the desktop, or a folder in the Start menu. */
function shortcutDir(where) {
  if (where === 'startmenu') return path.join(app.getPath('appData'), 'Microsoft', 'Windows', 'Start Menu', 'Programs', 'Aurora');
  return app.getPath('desktop');
}

function shortcutOptions(inst, iconFile) {
  return {
    target: process.execPath,
    // In development the executable is Electron itself, which needs the app folder first.
    args: app.isPackaged ? `--launch ${inst.id}` : `"${APP_DIR}" --launch ${inst.id}`,
    cwd: path.dirname(process.execPath),
    icon: iconFile,
    iconIndex: 0,
    description: `${inst.name} · Minecraft ${inst.version} · Aurora Launcher`,
    appUserModelId: 'dev.aurora.launcher',
  };
}

/** The installation's icon as an .ico, or the launcher's own icon when there is no picture. */
function shortcutIcon(inst, pngs) {
  try {
    return icons.writeIco(inst.id, pngs);
  } catch (e) {
    logError('shortcut icon', e);
    return process.execPath;
  }
}

function createShortcut(inst, where, pngs) {
  if (process.platform !== 'win32') throw new LauncherError('shortcuts_windows');
  const iconFile = shortcutIcon(inst, pngs);
  const dir = shortcutDir(where);
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, shortcutName(inst));
  if (!shell.writeShortcutLink(file, fs.existsSync(file) ? 'replace' : 'create', shortcutOptions(inst, iconFile))) {
    throw new LauncherError('shortcut_failed', file);
  }
  inst.shortcuts = [...new Set([...(inst.shortcuts || []), file])];
  settings.save();
  return file;
}

/** Gives every shortcut of an installation its current icon and name. */
function refreshShortcuts(inst, pngs) {
  const list = (inst.shortcuts || []).filter((f) => fs.existsSync(f));
  inst.shortcuts = list;
  if (!list.length || process.platform !== 'win32') return 0;
  const iconFile = shortcutIcon(inst, pngs);
  for (const file of list) shell.writeShortcutLink(file, 'replace', shortcutOptions(inst, iconFile));
  settings.save();
  return list.length;
}

// ---------------------------------------------------------------------------- IPC

/** Every handler answers {ok, value} or {ok: false, error: {code, detail}} for the interface. */
function handle(channel, fn) {
  ipcMain.handle(channel, async (event, ...args) => {
    const known = [win, splash].some((w) => w && !w.isDestroyed() && event.sender === w.webContents);
    if (!known) return { ok: false, error: { code: 'forbidden' } };
    try {
      return { ok: true, value: await fn(...args) };
    } catch (e) {
      if (!(e instanceof LauncherError)) logError(channel, e);
      return { ok: false, error: describe(e) };
    }
  });
}

function publicSettings() {
  const s = settings.data;
  return {
    language: s.language,
    memory: s.memory,
    javaMode: s.javaMode,
    javaPath: s.javaPath,
    jvmArgs: s.jvmArgs,
    resolution: s.resolution,
    afterLaunch: s.afterLaunch,
    background: s.background,
    motion: s.motion,
    showSnapshots: s.showSnapshots,
    discord: s.discord !== false,
    autoUpdate: s.autoUpdate !== false,
    selectedInstall: s.selectedInstall,
    gpu: s.gpu || 'auto',
    themeFromMod: s.themeFromMod === true,
  };
}

const SETTING_KEYS = {
  language: (v) => ['auto', 'ru', 'en'].includes(v),
  memory: (v) => Number.isInteger(v) && v >= 1024 && v <= 65536,
  javaMode: (v) => ['auto', 'custom'].includes(v),
  javaPath: (v) => typeof v === 'string' && v.length < 1024,
  jvmArgs: (v) => typeof v === 'string' && v.length < 4096,
  resolution: (v) => v === null || (v && Number.isInteger(v.width) && Number.isInteger(v.height) && v.width >= 320 && v.height >= 240),
  afterLaunch: (v) => ['hide', 'minimize', 'keep', 'close'].includes(v),
  background: (v) => ['aurora', 'sunset', 'synthwave', 'static'].includes(v),
  motion: (v) => typeof v === 'boolean',
  showSnapshots: (v) => typeof v === 'boolean',
  discord: (v) => typeof v === 'boolean',
  autoUpdate: (v) => typeof v === 'boolean',
  gpu: (v) => ['auto', 'high', 'saving'].includes(v),
  themeFromMod: (v) => typeof v === 'boolean',
};

const ICON_IDS = new Set(CATALOG.map((c) => `mc:${c.id}`));

function validIcon(icon) {
  if (typeof icon !== 'string') return false;
  return icon === 'aurora' || ICON_IDS.has(icon) || /^color:\w{1,16}$/.test(icon) || /^custom:[0-9a-f]{16}\.png$/.test(icon);
}

async function installInfo(inst) {
  const gameDir = gameDirOf(inst);
  const aurora = inst.aurora ? await mods.auroraIn(gameDir, inst.version) : null;
  return {
    ...inst,
    gameDir,
    customDir: Boolean(inst.gameDir),
    modCount: await mods.countEnabled(gameDir),
    installed: fs.existsSync(path.join(paths.versions, inst.version, `${inst.version}.jar`)),
    auroraVersion: aurora ? aurora.version : null,
    shortcuts: (inst.shortcuts || []).filter((f) => fs.existsSync(f)),
  };
}

function sanitizeInstall(input, existing) {
  const out = { ...(existing || defaults.defaultInstall(auroraMinecraft())) };
  if (!existing) {
    out.id = `i${Date.now().toString(36)}`;
    out.created = Date.now();
    out.lastPlayed = 0;
    out.shortcuts = [];
  }
  if (typeof input.name === 'string') out.name = input.name.trim().slice(0, 40) || 'Minecraft';
  if (typeof input.version === 'string' && /^[\w.\-]+$/.test(input.version)) out.version = input.version;
  if (input.loader === 'fabric' || input.loader === 'vanilla') out.loader = input.loader;
  if (typeof input.loaderVersion === 'string' && /^[\w.\-+]+$/.test(input.loaderVersion)) out.loaderVersion = input.loaderVersion;
  if (typeof input.aurora === 'boolean') out.aurora = input.aurora;
  if (typeof input.fabricApi === 'boolean') out.fabricApi = input.fabricApi;
  if (validIcon(input.icon)) out.icon = input.icon;
  if (input.memory === null || (Number.isInteger(input.memory) && input.memory >= 1024 && input.memory <= 65536)) out.memory = input.memory;
  if (typeof input.jvmArgs === 'string') out.jvmArgs = input.jvmArgs.slice(0, 4096);
  if (input.gameDir === null || (typeof input.gameDir === 'string' && path.isAbsolute(input.gameDir))) out.gameDir = input.gameDir;
  if (input.resolution === null || (input.resolution && Number.isInteger(input.resolution.width) && Number.isInteger(input.resolution.height))) out.resolution = input.resolution;
  if (out.loader !== 'fabric') out.aurora = false;
  if (out.aurora && !mods.auroraSupports(out.version)) out.aurora = false;
  return out;
}

function registerIpc() {
  handle('app:info', () => {
    const bundled = mods.bundled();
    return {
      version: app.getVersion(),
      dev: DEV,
      platform: process.platform,
      arch: process.arch,
      osRelease: os.release(),
      totalMemory: os.totalmem(),
      cpus: os.cpus().length,
      root: paths.root,
      locale: app.getLocale(),
      electron: process.versions.electron,
      chrome: process.versions.chrome,
      node: process.versions.node,
      aurora: bundled ? { version: bundled.version, minecraft: auroraMinecraft() } : null,
      recommendedMemory: defaults.recommendedMemory(),
    };
  });
  handle('app:openExternal', (url) => openExternal(url));
  handle('app:copy', (text) => clipboard.writeText(String(text)));
  handle('app:showLauncher', () => {
    closeSplash();
    showWindow();
  });
  handle('app:quit', () => {
    quitting = true;
    app.quit();
  });
  handle('app:openFolder', async (kind, installId) => {
    const inst = installById(installId) || selectedInstall();
    const gameDir = gameDirOf(inst);
    const dirs = {
      root: paths.root,
      game: gameDir,
      mods: path.join(gameDir, 'mods'),
      resourcepacks: path.join(gameDir, 'resourcepacks'),
      shaderpacks: path.join(gameDir, 'shaderpacks'),
      screenshots: path.join(gameDir, 'screenshots'),
      saves: path.join(gameDir, 'saves'),
      logs: path.join(gameDir, 'logs'),
      launcherLogs: paths.logs,
      skins: paths.skins,
    };
    if (kind === 'crash' && game.lastExit && game.lastExit.report) {
      shell.showItemInFolder(game.lastExit.report);
      return;
    }
    const dir = dirs[kind] || gameDir;
    fs.mkdirSync(dir, { recursive: true });
    await shell.openPath(dir);
  });
  handle('app:showItem', (file) => {
    if (typeof file === 'string' && path.isAbsolute(file) && fs.existsSync(file)) shell.showItemInFolder(file);
  });

  handle('window:minimize', () => win && win.minimize());
  handle('window:toggleMaximize', () => win && (win.isMaximized() ? win.unmaximize() : win.maximize()));
  handle('window:close', () => {
    if (!win) return;
    if (game.process) {
      // The game keeps running; the tray icon brings the launcher back.
      ensureTray();
      win.hide();
    } else {
      win.close();
    }
  });

  handle('settings:get', () => ({ ...publicSettings(), window: { maximized: Boolean(win && win.isMaximized()) } }));
  handle('settings:set', (patch) => {
    for (const [key, value] of Object.entries(patch || {})) {
      if (SETTING_KEYS[key] && SETTING_KEYS[key](value)) settings.data[key] = value;
    }
    settings.save();
    updatePresence();
    updater.schedule(settings.data.autoUpdate !== false);
    const next = publicSettings();
    send('settings:changed', next);
    return next;
  });

  handle('installs:list', async () => Promise.all(settings.data.installs.map(installInfo)));
  handle('installs:save', async (input) => {
    const existing = input && input.id ? installById(input.id) : null;
    const inst = sanitizeInstall(input || {}, existing);
    if (existing) Object.assign(existing, inst);
    else settings.data.installs.push(inst);
    settings.save();
    return installInfo(existing || inst);
  });
  handle('installs:remove', (id) => {
    if (settings.data.installs.length <= 1) throw new LauncherError('last_install');
    const inst = installById(id);
    // Its shortcuts would only start the launcher with an error now.
    for (const file of (inst && inst.shortcuts) || []) fs.rmSync(file, { force: true });
    settings.data.installs = settings.data.installs.filter((i) => i.id !== id);
    if (settings.data.selectedInstall === id) settings.data.selectedInstall = settings.data.installs[0].id;
    settings.save();
  });
  handle('installs:select', (id) => {
    if (installById(id)) {
      settings.data.selectedInstall = id;
      settings.save();
    }
    return publicSettings();
  });
  handle('installs:versions', async (snapshots) => {
    const list = await versions.list(Boolean(snapshots));
    return list.map((v) => ({ ...v, aurora: mods.auroraSupports(v.id) }));
  });
  handle('installs:shortcut', (id, where, pngs) => {
    const inst = installById(id);
    if (!inst) throw new LauncherError('bad_file', id);
    return createShortcut(inst, where === 'startmenu' ? 'startmenu' : 'desktop', Array.isArray(pngs) ? pngs : []);
  });
  handle('installs:refreshShortcuts', (id, pngs) => {
    const inst = installById(id);
    return inst ? refreshShortcuts(inst, Array.isArray(pngs) ? pngs : []) : 0;
  });

  handle('icons:catalog', () => icons.catalog());
  handle('icons:saveCustom', (url) => icons.saveCustom(url));

  handle('game:launch', (opts) => launch(opts || {}));
  handle('game:repair', (id) => launch({ installId: id, verify: true }));
  handle('game:cancel', () => game.controller?.abort());
  handle('game:kill', () => killGame());
  handle('game:state', () => gameState({ exit: game.lastExit, error: game.state === 'idle' ? game.lastError : null }));
  handle('game:ackExit', () => {
    if (game.lastExit) game.lastExit.seen = true;
    game.lastError = null;
  });
  handle('game:logs', () => game.lines);

  let signIn = null;
  handle('accounts:list', () => accounts.list().map((a) => ({ ...a, customSkin: a.type === 'offline' && Boolean(skins.read(a.uuid)) })));
  handle('accounts:select', (id) => accounts.select(id));
  handle('accounts:remove', (id) => accounts.remove(id));
  handle('accounts:addOffline', (name) => accounts.addOffline(name));
  handle('accounts:skin', async (id) => {
    const account = accounts.data.accounts.find((a) => a.id === id);
    const custom = account && account.type === 'offline' ? skins.read(account.uuid) : null;
    return custom ? custom.dataUrl : accounts.skin(id);
  });
  handle('accounts:offlineUnlocked', () => accounts.offlineUnlocked());
  handle('accounts:signIn', async () => {
    signIn?.abort();
    signIn = new AbortController();
    try {
      return await accounts.signIn({ signal: signIn.signal, onCode: (code) => send('accounts:code', code) });
    } finally {
      signIn = null;
    }
  });
  handle('accounts:cancelSignIn', () => signIn?.abort());
  accounts.on('changed', () => send('accounts:changed', accounts.list().map((a) => ({ ...a, customSkin: a.type === 'offline' && Boolean(skins.read(a.uuid)) }))));

  const offlineAccount = (id) => {
    const account = accounts.data.accounts.find((a) => a.id === id);
    if (!account) throw new LauncherError('no_account');
    if (account.type !== 'offline') throw new LauncherError('skin_licensed');
    return account;
  };
  handle('skins:get', async (id) => {
    const account = offlineAccount(id);
    const custom = skins.read(account.uuid);
    if (custom) return { ...custom, custom: true };
    return { dataUrl: await accounts.skin(id), slim: false, custom: false };
  });
  handle('skins:save', (id, url, slim) => {
    const account = offlineAccount(id);
    skins.save(account.uuid, url, slim);
    accounts.emit('changed');
  });
  handle('skins:reset', (id) => {
    const account = offlineAccount(id);
    skins.remove(account.uuid);
    accounts.emit('changed');
  });
  handle('skins:byName', (name) => skins.byName(String(name || '').trim()));
  handle('skins:gallery', (query) => skins.gallery(query || {}));
  handle('skins:texture', (hash) => skins.texture(String(hash)));
  handle('skins:pick', async () => {
    const res = await dialog.showOpenDialog(win, { properties: ['openFile'], filters: [{ name: 'PNG', extensions: ['png'] }] });
    if (res.canceled || !res.filePaths[0]) return null;
    const data = fs.readFileSync(res.filePaths[0]);
    if (data.length > 2 * 1024 * 1024) throw new LauncherError('bad_skin');
    return `data:image/png;base64,${data.toString('base64')}`;
  });

  handle('servers:list', () => settings.data.servers);
  handle('servers:save', (input) => {
    const address = String(input && input.address || '').trim();
    if (!parseAddress(address).host || address.length > 255) throw new LauncherError('bad_address');
    const name = String(input.name || '').trim().slice(0, 40) || address;
    const existing = input.id ? settings.data.servers.find((s) => s.id === input.id) : null;
    if (existing) {
      existing.name = name;
      existing.address = address;
      if (typeof input.favorite === 'boolean') existing.favorite = input.favorite;
    } else {
      settings.data.servers.push({ id: `s${Date.now().toString(36)}`, name, address, builtin: false });
    }
    settings.save();
    return settings.data.servers;
  });
  handle('servers:remove', (id) => {
    settings.data.servers = settings.data.servers.filter((s) => s.id !== id);
    settings.save();
    return settings.data.servers;
  });
  handle('servers:move', (id, to) => {
    const list = settings.data.servers;
    const from = list.findIndex((s) => s.id === id);
    if (from < 0) return list;
    const [item] = list.splice(from, 1);
    list.splice(Math.max(0, Math.min(list.length, to)), 0, item);
    settings.save();
    return list;
  });
  handle('servers:ping', (address, force) => pinger.status(String(address), Boolean(force)));

  const instDir = (id) => {
    const inst = installById(id) || selectedInstall();
    return { inst, dir: gameDirOf(inst) };
  };
  const safeMod = (file) => {
    if (typeof file !== 'string' || file.includes('/') || file.includes('\\') || !/\.jar(\.disabled)?$/i.test(file)) throw new LauncherError('bad_file');
    return file;
  };
  const safePack = (file) => {
    if (typeof file !== 'string' || !file || file.includes('/') || file.includes('\\') || file === '.' || file === '..') throw new LauncherError('bad_file');
    return file;
  };
  const notRunning = () => {
    // The game rewrites options.txt when it closes, which would undo the change.
    if (game.process) throw new LauncherError('game_running');
  };
  handle('mods:list', (id) => mods.list(instDir(id).dir));
  handle('mods:setEnabled', (id, file, enabled) => mods.setEnabled(instDir(id).dir, safeMod(file), Boolean(enabled)));
  handle('mods:remove', async (id, file) => {
    await shell.trashItem(path.join(instDir(id).dir, 'mods', safeMod(file)));
  });
  handle('mods:pick', async (id) => {
    const res = await dialog.showOpenDialog(win, { properties: ['openFile', 'multiSelections'], filters: [{ name: 'Minecraft mods', extensions: ['jar'] }] });
    if (res.canceled) return [];
    return mods.addFiles(instDir(id).dir, res.filePaths);
  });
  handle('mods:addPaths', (id, list) => mods.addFiles(instDir(id).dir, (list || []).filter((p) => typeof p === 'string' && path.isAbsolute(p))));
  handle('mods:search', (query) => mods.search({ ...query, mc: (installById(query && query.installId) || selectedInstall()).version }));
  handle('mods:install', async (id, project) => {
    const { inst, dir } = instDir(id);
    if (inst.loader !== 'fabric') throw new LauncherError('mods_need_fabric');
    return mods.install(String(project), dir, inst.version, { onProgress: (p) => send('mods:progress', { project, ...p }) });
  });
  handle('mods:installed', (id) => mods.installedProjects(instDir(id).dir));

  handle('packs:list', async (id, kind) => {
    const { dir } = instDir(id);
    const list = await packs.list(dir, kind);
    const iris = kind === 'shader' ? (await mods.list(dir)).some((m) => m.id === 'iris' && m.enabled) : null;
    return { list, iris };
  });
  handle('packs:setEnabled', (id, kind, file, on) => {
    notRunning();
    const { dir } = instDir(id);
    if (kind === 'shader') packs.setShader(dir, on ? safePack(file) : null);
    else packs.setResourcePack(dir, safePack(file), Boolean(on));
  });
  handle('packs:remove', async (id, kind, file) => {
    await shell.trashItem(path.join(packs.folder(instDir(id).dir, kind), safePack(file)));
  });
  handle('packs:pick', async (id, kind) => {
    const res = await dialog.showOpenDialog(win, { properties: ['openFile', 'multiSelections'], filters: [{ name: 'ZIP', extensions: ['zip'] }] });
    if (res.canceled) return [];
    return packs.addFiles(instDir(id).dir, kind, res.filePaths);
  });
  handle('packs:addPaths', (id, kind, list) => packs.addFiles(instDir(id).dir, kind, (list || []).filter((p) => typeof p === 'string' && path.isAbsolute(p))));
  handle('packs:search', (kind, query) => packs.search(kind, { ...query, mc: (installById(query && query.installId) || selectedInstall()).version }));
  handle('packs:install', async (id, kind, project) => {
    const { inst, dir } = instDir(id);
    return packs.install(kind, String(project), dir, inst.version, { onProgress: (p) => send('mods:progress', { project, ...p }) });
  });
  handle('packs:installed', (id, kind) => packs.installedProjects(instDir(id).dir, kind));

  // A modpack becomes a new installation with its own folder; one is installed at a time.
  let packJob = null;
  const packFile = (file) => {
    if (typeof file !== 'string' || !path.isAbsolute(file) || !/\.mrpack$/i.test(file) || !fs.existsSync(file)) throw new LauncherError('pack_bad', String(file));
    return file;
  };
  handle('modpacks:search', (query) => modpacks.search(query || {}));
  handle('modpacks:versions', (project) => modpacks.versions(String(project)));
  handle('modpacks:icon', (url) => modpacks.icon(String(url)));
  handle('modpacks:read', (file) => readPack(packFile(file)));
  handle('modpacks:pick', async () => {
    const res = await dialog.showOpenDialog(win, { properties: ['openFile'], filters: [{ name: 'Modrinth modpack', extensions: ['mrpack'] }] });
    if (res.canceled || !res.filePaths[0]) return null;
    return readPack(res.filePaths[0]);
  });
  handle('modpacks:install', async (opts = {}) => {
    if (packJob) throw new LauncherError('pack_busy');
    const controller = new AbortController();
    packJob = controller;
    const { signal } = controller;
    const progress = (p) => send('modpacks:progress', p);
    let cached = null;
    let created = null;
    try {
      let file;
      let source = {};
      if (opts.versionId) {
        const version = await modpacks.version(String(opts.versionId));
        progress({ stage: 'pack', doneBytes: 0, totalBytes: version.file.size });
        file = cached = await modpacks.download(version, { signal, onProgress: (p) => progress({ stage: 'pack', ...p }) });
        source = { project: version.project, versionId: version.id };
      } else {
        file = packFile(opts.file);
      }
      const pack = readPack(file);
      const inst = sanitizeInstall({
        name: typeof opts.name === 'string' && opts.name.trim() ? opts.name : pack.name,
        version: pack.minecraft,
        loader: pack.loader,
        loaderVersion: pack.loaderVersion || 'latest',
        aurora: Boolean(opts.aurora),
        // The pack brings its own Fabric API if it needs one.
        fabricApi: false,
        icon: validIcon(opts.icon) ? opts.icon : 'mc:crafting_table',
      }, null);
      inst.modpack = { name: pack.name, version: pack.version, ...source };
      const gameDir = gameDirOf(inst);
      if (!fs.existsSync(gameDir)) created = gameDir;
      await installPack(file, gameDir, { signal, onProgress: progress });
      settings.data.installs.push(inst);
      settings.save();
      return installInfo(inst);
    } catch (e) {
      // A half-filled folder is of no use, but only one made for this pack is removed.
      if (created) await fs.promises.rm(created, { recursive: true, force: true }).catch(() => {});
      throw signal.aborted ? new LauncherError('cancelled') : e;
    } finally {
      if (cached) await fs.promises.rm(cached, { force: true }).catch(() => {});
      packJob = null;
    }
  });
  handle('modpacks:cancel', () => packJob?.abort());

  // -------------------------------------------------------------- content updates
  const contentKind = (kind) => {
    if (!['mod', 'resourcepack', 'shader'].includes(kind)) throw new LauncherError('bad_file', String(kind));
    return kind;
  };
  handle('content:check', async (id, kind) => {
    const { inst, dir } = instDir(id);
    const meta = readMeta(dir);
    // The launcher keeps Aurora and its Fabric API up to date itself.
    const skip = contentKind(kind) === 'mod' ? Object.values(meta.managed) : [];
    return contentUpdates.check(dir, kind, inst.version, { skip });
  });
  handle('content:update', async (id, kind, list) => {
    const { dir } = instDir(id);
    contentKind(kind);
    if (kind !== 'mod') notRunning();
    const done = [];
    for (const update of Array.isArray(list) ? list : []) {
      if (!update || typeof update.file !== 'string' || !update.download || typeof update.download.url !== 'string') continue;
      if (!/^https:\/\/cdn\.modrinth\.com\//.test(update.download.url)) continue;
      const name = await contentUpdates.apply(dir, kind, update, { onProgress: (p) => send('content:progress', { file: update.file, ...p }) });
      done.push({ from: update.file, to: name });
    }
    if (kind === 'mod') refreshInstallsSoon();
    return done;
  });

  // -------------------------------------------------------------- worlds
  const notRunningIn = (id) => {
    if (game.process && game.installId === id) throw new LauncherError('game_running');
  };
  handle('worlds:list', (id) => worlds.list(instDir(id).dir));
  handle('worlds:size', (id, folder) => worlds.size(instDir(id).dir, folder));
  handle('worlds:backups', (id) => worlds.backups(instDir(id).dir));
  handle('worlds:backup', async (id, folder) => {
    const { inst, dir } = instDir(id);
    notRunningIn(inst.id);
    const file = await worlds.backup(dir, folder, { onProgress: (p) => send('worlds:progress', { folder, ...p }) });
    return path.basename(file);
  });
  handle('worlds:restore', async (id, file, label) => {
    const { dir } = instDir(id);
    return worlds.restore(dir, file, { label: typeof label === 'string' ? label.slice(0, 40) : '' });
  });
  handle('worlds:remove', async (id, folder) => {
    const { inst, dir } = instDir(id);
    notRunningIn(inst.id);
    await shell.trashItem(path.join(worlds.savesDir(dir), worlds.safeFolder(folder)));
  });
  handle('worlds:removeBackup', async (id, file) => {
    await shell.trashItem(path.join(worlds.backupsDir(instDir(id).dir), safePack(path.basename(String(file)))));
  });
  handle('worlds:showBackup', (id, file) => {
    shell.showItemInFolder(path.join(worlds.backupsDir(instDir(id).dir), safePack(path.basename(String(file)))));
  });
  handle('worlds:open', async (id, folder) => {
    await shell.openPath(path.join(worlds.savesDir(instDir(id).dir), worlds.safeFolder(folder)));
  });

  // -------------------------------------------------------------- screenshots of every installation
  const shotDirs = () => settings.data.installs.map((inst) => ({ inst, dir: path.join(gameDirOf(inst), 'screenshots') }));
  const shotFile = (file) => {
    const target = path.resolve(String(file || ''));
    const ok = /\.(png|jpe?g)$/i.test(target) && shotDirs().some(({ dir }) => target.startsWith(path.resolve(dir) + path.sep));
    if (!ok || !fs.existsSync(target)) throw new LauncherError('bad_file', path.basename(target));
    return target;
  };
  const thumbCache = new Map();
  handle('shots:list', async () => {
    const out = [];
    for (const { inst, dir } of shotDirs()) {
      let names = [];
      try {
        names = await fs.promises.readdir(dir);
      } catch {
        continue;
      }
      for (const name of names) {
        if (!/\.(png|jpe?g)$/i.test(name)) continue;
        try {
          const stat = await fs.promises.stat(path.join(dir, name));
          out.push({ file: path.join(dir, name), name, installId: inst.id, installName: inst.name, modified: stat.mtimeMs, size: stat.size });
        } catch {
          // Gone meanwhile.
        }
      }
    }
    out.sort((a, b) => b.modified - a.modified);
    return out.slice(0, 600);
  });
  handle('shots:thumb', (file) => {
    const target = shotFile(file);
    const key = `${target}|${fs.statSync(target).mtimeMs}`;
    if (!thumbCache.has(key)) {
      const image = nativeImage.createFromPath(target);
      thumbCache.set(key, image.isEmpty() ? null : `data:image/jpeg;base64,${image.resize({ width: 360, quality: 'good' }).toJPEG(82).toString('base64')}`);
      if (thumbCache.size > 800) thumbCache.delete(thumbCache.keys().next().value);
    }
    return thumbCache.get(key);
  });
  handle('shots:image', (file) => {
    const image = nativeImage.createFromPath(shotFile(file));
    if (image.isEmpty()) return null;
    const { width, height } = image.getSize();
    const scaled = width > 1920 ? image.resize({ width: 1920, quality: 'best' }) : image;
    return { url: `data:image/jpeg;base64,${scaled.toJPEG(90).toString('base64')}`, width, height };
  });
  handle('shots:copy', (file) => {
    clipboard.writeImage(nativeImage.createFromPath(shotFile(file)));
  });
  handle('shots:remove', async (file) => {
    await shell.trashItem(shotFile(file));
  });

  // -------------------------------------------------------------- installation tools
  handle('installs:clone', async (id, opts = {}) => {
    const source = installById(id);
    if (!source) throw new LauncherError('bad_file', String(id));
    const copy = sanitizeInstall({ ...source, gameDir: null, name: typeof opts.name === 'string' && opts.name.trim() ? opts.name : `${source.name} (2)` }, null);
    copy.modpack = source.modpack || undefined;
    await installTools.cloneFolder(gameDirOf(source), gameDirOf(copy), { worlds: opts.worlds === true, screenshots: opts.screenshots === true });
    settings.data.installs.push(copy);
    settings.save();
    return installInfo(copy);
  });
  handle('installs:export', async (id) => {
    const { inst, dir } = instDir(id);
    const res = await dialog.showSaveDialog(win, {
      defaultPath: path.join(app.getPath('downloads'), `${inst.name.replace(/[\\/:*?"<>|]+/g, '_')}.mrpack`),
      filters: [{ name: 'Modrinth modpack', extensions: ['mrpack'] }],
    });
    if (res.canceled || !res.filePath) return null;
    const loaderVersion = inst.loader === 'fabric' ? await versions.fabricLoader(inst.version, inst.loaderVersion).catch(() => null) : null;
    const stats = await installTools.exportPack(dir, res.filePath, {
      name: inst.name,
      version: (inst.modpack && inst.modpack.version) || '1.0.0',
      minecraft: inst.version,
      loader: inst.loader,
      loaderVersion,
      onProgress: (p) => send('installs:exportProgress', p),
    });
    return { file: res.filePath, ...stats };
  });
  handle('installs:copySettings', async (fromId, toId, parts) => {
    const from = installById(fromId);
    const to = installById(toId);
    if (!from || !to || from.id === to.id) throw new LauncherError('bad_file', String(toId));
    notRunningIn(to.id);
    const wanted = (Array.isArray(parts) ? parts : []).filter((p) => Object.prototype.hasOwnProperty.call(installTools.SETTING_PARTS, p));
    return installTools.copySettings(gameDirOf(from), gameDirOf(to), wanted);
  });

  // -------------------------------------------------------------- crashes
  handle('crash:fix', async (action, id) => {
    const { inst, dir } = instDir(id);
    if (!action || typeof action.type !== 'string') throw new LauncherError('bad_file', 'action');
    switch (action.type) {
      case 'memory': {
        const value = Math.round(Number(action.value) / 512) * 512;
        if (!Number.isInteger(value) || value < 1024 || value > 65536) throw new LauncherError('bad_file', 'memory');
        if (inst.memory) inst.memory = value;
        else settings.data.memory = value;
        settings.save();
        send('settings:changed', publicSettings());
        refreshInstallsSoon();
        return { memory: value };
      }
      case 'disable':
        await mods.setEnabled(dir, safeMod(String(action.file)), false);
        refreshInstallsSoon();
        return { disabled: action.file };
      case 'install': {
        if (inst.loader !== 'fabric') throw new LauncherError('mods_need_fabric');
        const project = String(action.project || '');
        if (!/^[\w.-]{2,64}$/.test(project)) throw new LauncherError('bad_file', project);
        await mods.install(project, dir, inst.version);
        refreshInstallsSoon();
        return { installed: project };
      }
      case 'java':
        settings.data.javaMode = 'auto';
        settings.save();
        send('settings:changed', publicSettings());
        return { java: 'auto' };
      case 'drivers': {
        const info = await app.getGPUInfo('basic').catch(() => null);
        const vendor = info && info.gpuDevice && (info.gpuDevice.find((d) => d.active) || info.gpuDevice[0]);
        const pages = {
          0x10de: 'https://www.nvidia.com/drivers',
          0x1002: 'https://www.amd.com/en/support/download/drivers.html',
          0x8086: 'https://www.intel.com/content/www/us/en/support/detect.html',
        };
        openExternal(pages[vendor && vendor.vendorId] || 'https://www.nvidia.com/drivers');
        return {};
      }
      default:
        throw new LauncherError('bad_file', action.type);
    }
  });
  handle('logs:share', async () => {
    const lines = game.lines.slice(-20000).map((e) => `[${e.time || ''}] [${e.thread || 'main'}/${e.level || 'INFO'}] ${e.message || ''}`);
    let report = '';
    if (game.lastExit && game.lastExit.report) {
      try {
        report = fs.readFileSync(game.lastExit.report, 'utf8').slice(0, 2 * 1024 * 1024);
      } catch {
        report = '';
      }
    }
    const content = [lines.join('\n'), report].filter(Boolean).join('\n\n---- Crash report ----\n\n');
    if (!content.trim()) throw new LauncherError('log_empty');
    const res = await net.postForm('https://api.mclo.gs/1/log', { content }, { timeout: 30000 });
    if (!res.ok || !res.json.success || typeof res.json.url !== 'string') throw new LauncherError('log_upload_failed', String(res.json.error || res.status));
    clipboard.writeText(res.json.url);
    return res.json.url;
  });

  // -------------------------------------------------------------- what's new, theme from the mod
  const modVersionNow = () => {
    const bundled = mods.bundled();
    return bundled ? String(bundled.version).split('+')[0] : null;
  };
  // The changelogs are written in English; the Russian interface reads its own translation.
  const russian = (() => {
    try {
      return JSON.parse(fs.readFileSync(path.join(__dirname, 'changelog-ru.json'), 'utf8'));
    } catch {
      return { launcher: {}, mod: {} };
    }
  })();
  const translate = (notes, lang) => {
    if (!notes || lang !== 'ru') return notes;
    const swap = (list, group) => list.map((n) => (russian[group][n.version] ? { ...n, body: russian[group][n.version] } : n));
    return { launcher: swap(notes.launcher, 'launcher'), mod: swap(notes.mod, 'mod') };
  };
  handle('app:whatsNew', (lang) => translate(whatsNewNotes(), lang));
  const whatsNewNotes = () => {
    const launcherNow = app.getVersion();
    const modNow = modVersionNow();
    // The very first start shows nothing: everything is new then. A launcher from before this
    // feature (no record yet, but it has been played) shows what its update brought.
    const usedBefore = settings.data.installs.some((i) => i.lastPlayed > 0);
    if (!settings.data.seenLauncher && !usedBefore) {
      settings.data.seenLauncher = launcherNow;
      settings.data.seenMod = modNow;
      settings.save();
      return null;
    }
    const only = (list, version) => list.filter((n) => n.version === version);
    let launcherNotes = changelog.since(LAUNCHER_CHANGELOG, settings.data.seenLauncher || null, launcherNow);
    let modNotes = modNow ? changelog.since(MOD_CHANGELOG, settings.data.seenMod || null, modNow) : [];
    if (!settings.data.seenLauncher) {
      launcherNotes = only(launcherNotes, launcherNow);
      modNotes = only(modNotes, modNow);
    }
    if (!launcherNotes.length && !modNotes.length) return null;
    return { launcher: launcherNotes, mod: modNotes };
  };
  handle('app:whatsNewSeen', () => {
    settings.data.seenLauncher = app.getVersion();
    settings.data.seenMod = modVersionNow();
    settings.save();
  });
  handle('app:changelog', (lang) => translate({
    launcher: changelog.since(LAUNCHER_CHANGELOG, null, app.getVersion()),
    mod: changelog.since(MOD_CHANGELOG, null, modVersionNow() || '0'),
  }, lang));
  handle('app:modTheme', () => {
    const inst = selectedInstall();
    try {
      const config = JSON.parse(fs.readFileSync(path.join(gameDirOf(inst), 'config', 'aurora.json'), 'utf8'));
      const client = config.modules && config.modules.client && config.modules.client.settings;
      if (!client) return null;
      return { theme: typeof client.theme === 'string' ? client.theme : 'aurora', accent: client.accent && typeof client.accent.argb === 'string' ? client.accent.argb : null };
    } catch {
      return null;
    }
  });

  handle('updates:state', () => updater.state);
  handle('updates:check', () => updater.check());
  handle('updates:install', () => {
    // A running game or download would be left without its launcher: let them finish first.
    if (game.process || game.state !== 'idle' || packJob) throw new LauncherError('update_busy');
    if (updater.install({ relaunch: true })) {
      quitting = true;
      app.quit();
    }
  });

  handle('news:get', () => news.all());

  handle('java:pick', async () => {
    const res = await dialog.showOpenDialog(win, {
      properties: ['openFile'],
      filters: process.platform === 'win32' ? [{ name: 'Java', extensions: ['exe'] }] : [],
    });
    if (res.canceled || !res.filePaths[0]) return null;
    return { path: res.filePaths[0], info: await probe(res.filePaths[0]) };
  });
  handle('java:probe', (p) => (typeof p === 'string' && p ? probe(p) : null));
  handle('dialog:pickFolder', async (title) => {
    const res = await dialog.showOpenDialog(win, { title: String(title || ''), properties: ['openDirectory', 'createDirectory'] });
    return res.canceled ? null : res.filePaths[0];
  });
}

// ---------------------------------------------------------------------------- start

app.whenReady().then(() => {
  net.configure({ fetch: (url, init) => electronNet.fetch(url, init), agent: `AuroraLauncher/${app.getVersion()}` });
  protocol.handle('app', (request) => {
    const url = new URL(request.url);
    const pathname = decodeURIComponent(url.pathname);
    if (pathname.startsWith('/user-icons/')) {
      // Pictures the player picked as installation icons.
      const file = icons.customFile(pathname.slice('/user-icons/'.length));
      return file && fs.existsSync(file) ? electronNet.fetch(pathToFileURL(file).toString()) : new Response('Not found', { status: 404 });
    }
    const file = path.normalize(path.join(RENDERER, pathname));
    if (!file.startsWith(RENDERER + path.sep)) return new Response('Not found', { status: 404 });
    return electronNet.fetch(pathToFileURL(file).toString());
  });
  registerIpc();
  updatePresence();
  updater.schedule(settings.data.autoUpdate !== false);
  // A newer Aurora on Modrinth shows up in the installations without waiting for a launch.
  mods.refreshAurora().then(() => send('app:event', { type: 'installsChanged' })).catch(() => {});
  const launchArg = parseLaunch(process.argv);
  if (launchArg) directLaunch(launchArg);
  else createWindow();
  if (process.env.AURORA_SCRIPT && DEV) require(path.resolve(process.env.AURORA_SCRIPT))({ win, app, send, accounts });
});

app.on('second-instance', (event, argv) => {
  const launchArg = parseLaunch(argv);
  if (launchArg) directLaunch(launchArg);
  else showWindow();
});

app.on('before-quit', () => {
  quitting = true;
  settings.flush();
  accounts.store.flush();
  // A downloaded update goes in while the launcher is closed; the next start is the new version.
  if (settings.data.autoUpdate !== false) updater.install({ relaunch: false });
});

app.on('window-all-closed', () => {
  // A running game keeps the launcher alive in the tray to show its log and come back after.
  if (!game.process || quitting) app.quit();
  else ensureTray();
});
