'use strict';

const os = require('os');

/** Memory for the game by default: Mojang's 4 GB, less on small machines, more on big ones. */
function recommendedMemory() {
  const gb = os.totalmem() / 1024 ** 3;
  if (gb <= 4.5) return 2048;
  if (gb <= 8.5) return 3072;
  if (gb <= 16.5) return 4096;
  return 6144;
}

function defaultInstall(mcVersion) {
  return {
    id: 'aurora',
    name: 'Aurora',
    version: mcVersion,
    loader: 'fabric',
    loaderVersion: 'latest',
    aurora: true,
    fabricApi: true,
    icon: 'aurora',
    memory: null,
    jvmArgs: '',
    gameDir: null,
    resolution: null,
    created: Date.now(),
    lastPlayed: 0,
  };
}

/**
 * Servers listed out of the box. All of them accept the current game version; the cards show
 * each server's own live MOTD, so only the big international ones get a tagline here.
 */
const SERVERS = [
  { id: 'hypixel', name: 'Hypixel', address: 'mc.hypixel.net', tagline: { ru: 'Мини-игры, SkyBlock, BedWars', en: 'Minigames, SkyBlock, BedWars' } },
  { id: 'funtime', name: 'FunTime', address: 'mc.funtime.su' },
  { id: 'reallyworld', name: 'ReallyWorld', address: 'mc.reallyworld.ru' },
  { id: 'holyworld', name: 'HolyWorld', address: 'mc.holyworld.ru' },
  { id: 'cubecraft', name: 'CubeCraft', address: 'play.cubecraft.net', tagline: { ru: 'EggWars, SkyWars и другие мини-игры', en: 'EggWars, SkyWars and more minigames' } },
  { id: 'wynncraft', name: 'Wynncraft', address: 'play.wynncraft.com', tagline: { ru: 'Огромная MMORPG внутри Minecraft', en: 'A huge MMORPG inside Minecraft' } },
];

function defaultSettings(mcVersion) {
  return {
    language: 'auto',
    memory: recommendedMemory(),
    javaMode: 'auto',
    javaPath: '',
    jvmArgs: '',
    resolution: null,
    afterLaunch: 'hide',
    background: 'aurora',
    motion: true,
    showSnapshots: false,
    discord: true,
    autoUpdate: true,
    selectedInstall: 'aurora',
    installs: [defaultInstall(mcVersion)],
    servers: SERVERS.map((s) => ({ ...s, builtin: true })),
    window: { width: 1280, height: 780, maximized: false },
  };
}

/** Aurora's own announcements for the news card, in both interface languages. */
const AURORA_NEWS = [
  {
    id: 'aurora-launcher-1.0',
    source: 'aurora',
    tag: 'Aurora',
    date: '2026-10-20',
    title: { ru: 'Встречайте Aurora Launcher', en: 'Meet Aurora Launcher' },
    text: {
      ru: 'Игра, Fabric и мод Aurora ставятся в один клик. Аккаунты Microsoft и офлайн, живой список серверов с онлайном и каталог модов Modrinth.',
      en: 'The game, Fabric and the Aurora mod install in one click. Microsoft and offline accounts, a live server list and the Modrinth mod catalog.',
    },
    action: 'about',
  },
  {
    id: 'aurora-mod-1.0',
    source: 'aurora',
    tag: 'Aurora',
    date: '2026-10-20',
    title: { ru: 'Aurora 1.0 для Minecraft 26.3', en: 'Aurora 1.0 for Minecraft 26.3' },
    text: {
      ru: 'Меню модов, перетаскиваемый HUD, хитбоксы, новый чат, таб и меню паузы, анимированные фоны и смена аккаунта прямо в игре.',
      en: 'A mod menu, draggable HUD, hitboxes, a new chat, tab list and pause menu, animated backgrounds and account switching in game.',
    },
    action: 'mods',
  },
];

/**
 * The Discord application whose name and logo the Rich Presence shows ("Playing Aurora Client").
 * The mod has the same id in DiscordModule.APP_ID; its art assets hold the logo as "aurora".
 */
const DISCORD_APP_ID = '1562074726245998623';

/**
 * The ID of the Aurora project on Modrinth (the ID, which never changes, not the address). When a
 * newer build is published there, installations get it without a launcher update, and so do new
 * Minecraft versions it supports. Empty: no lookups, the bundled build only.
 */
const AURORA_MODRINTH = 'L7X9iCgB';
// The public page of the mod: the button under the Discord status leads there.
const AURORA_PAGE = `https://modrinth.com/mod/${AURORA_MODRINTH}`;

/**
 * The public GitHub repository whose releases update the launcher (owner/name). Each release
 * carries the installer and latest.yml (npm run dist writes both, npm run release publishes them).
 * Empty: the launcher never looks for updates.
 */
const LAUNCHER_REPO = 'K1llSofix/aurora';

module.exports = { recommendedMemory, defaultInstall, defaultSettings, SERVERS, AURORA_NEWS, DISCORD_APP_ID, AURORA_MODRINTH, AURORA_PAGE, LAUNCHER_REPO };
