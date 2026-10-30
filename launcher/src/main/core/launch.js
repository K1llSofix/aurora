'use strict';

const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const { EventEmitter } = require('events');
const { allowed, compareVersions } = require('./rules');

/** Splits user-typed JVM arguments; quotes keep spaces inside one argument (-Dname="a b"). */
function splitArgs(text) {
  const out = [];
  let current = '';
  let quote = null;
  let started = false;
  for (const ch of String(text || '')) {
    if (quote) {
      if (ch === quote) quote = null;
      else current += ch;
    } else if (ch === '"' || ch === "'") {
      quote = ch;
      started = true;
    } else if (/\s/.test(ch)) {
      if (started) out.push(current);
      current = '';
      started = false;
    } else {
      current += ch;
      started = true;
    }
  }
  if (started) out.push(current);
  return out;
}

function substitute(value, vars) {
  return String(value).replace(/\$\{([^}]+)\}/g, (all, key) => (Object.prototype.hasOwnProperty.call(vars, key) ? vars[key] : all));
}

function collect(list, vars, features) {
  const out = [];
  for (const item of list || []) {
    if (typeof item === 'string') {
      out.push(substitute(item, vars));
      continue;
    }
    if (!allowed(item.rules, features)) continue;
    for (const value of Array.isArray(item.value) ? item.value : [item.value]) out.push(substitute(value, vars));
  }
  return out;
}

/** Drops "--option value" pairs whose value came out empty (an offline account has no xuid). */
function dropEmpty(args, options) {
  const out = [];
  for (let i = 0; i < args.length; i++) {
    if (options.includes(args[i]) && (i + 1 >= args.length || args[i + 1] === '' || args[i + 1].startsWith('--'))) {
      if (i + 1 < args.length && args[i + 1] === '') i++;
      continue;
    }
    out.push(args[i]);
  }
  return out;
}

/**
 * authlib 2.0.26 to 2.1.28 (1.16.4, 1.16.5 and the snapshots around them) take the 401 an offline
 * token gets for a valid answer with every privilege off, which greys out Multiplayer and chat with
 * "check your Microsoft account settings". When the request fails outright, the game treats the
 * player as offline and allows everything, so these versions get a services host that does not
 * exist. authlib ignores the override unless the other hosts are set too; they keep their real values.
 */
function offlineServiceHosts(version) {
  const lib = (version.libraries || []).find((l) => l.name.startsWith('com.mojang:authlib:'));
  const authlib = lib && lib.name.split(':')[2];
  if (!authlib || compareVersions(authlib, '2.0') < 0 || compareVersions(authlib, '2.1.29') >= 0) return [];
  return [
    '-Dminecraft.api.auth.host=https://authserver.mojang.com',
    '-Dminecraft.api.account.host=https://api.mojang.com',
    '-Dminecraft.api.session.host=https://sessionserver.mojang.com',
    '-Dminecraft.api.services.host=https://nope.invalid',
  ];
}

const GC_FLAG = /^-XX:[+-]Use\w*GC$/;
const GC_TUNING = /^-XX:(G1|Z|Shenandoah|MaxGCPauseMillis|ParallelGCThreads|ConcGCThreads)/;

/**
 * The full java command line for a prepared installation.
 *
 * @param prepared result of Installer.prepare
 * @param o {account, memory (MB), jvmArgs, resolution {width,height}|null, server "host:port"|null,
 *          world folder name|null, launcherVersion}
 */
function buildCommand(prepared, o) {
  const v = prepared.version;
  const separator = path.delimiter;
  const account = o.account;
  const quickPlay = Boolean(v.arguments && JSON.stringify(v.arguments.game || []).includes('quickPlayMultiplayer'));
  const features = {
    is_demo_user: false,
    has_custom_resolution: Boolean(o.resolution),
    has_quick_plays_support: false,
    is_quick_play_singleplayer: Boolean(o.world) && quickPlay,
    is_quick_play_multiplayer: Boolean(o.server) && quickPlay,
    is_quick_play_realms: false,
  };
  const uuid = account.uuid.replace(/-/g, '');
  const vars = {
    auth_player_name: account.name,
    version_name: v.id,
    game_directory: prepared.gameDir,
    assets_root: prepared.assetsRoot,
    game_assets: prepared.virtualAssets || prepared.assetsRoot,
    assets_index_name: prepared.assetsIndex,
    auth_uuid: uuid,
    auth_access_token: account.accessToken,
    auth_session: `token:${account.accessToken}:${uuid}`,
    auth_xuid: account.xuid || '',
    clientid: account.clientId || '',
    user_type: account.type === 'microsoft' ? 'msa' : 'legacy',
    user_properties: '{}',
    version_type: v.type || 'release',
    resolution_width: String(o.resolution ? o.resolution.width : 854),
    resolution_height: String(o.resolution ? o.resolution.height : 480),
    natives_directory: prepared.nativesDir,
    launcher_name: 'Aurora',
    launcher_version: o.launcherVersion,
    classpath: prepared.classpath.join(separator),
    classpath_separator: separator,
    library_directory: prepared.librariesRoot,
    quickPlayPath: path.join(prepared.gameDir, 'quickPlay', 'log.json'),
    quickPlaySingleplayer: o.world || '',
    quickPlayMultiplayer: o.server || '',
    quickPlayRealms: '',
  };

  const user = splitArgs(o.jvmArgs);
  const userPicksGc = user.some((a) => GC_FLAG.test(a));
  const jvm = [];
  const memory = Math.max(512, Math.round(o.memory || 4096));
  jvm.push(`-Xms${Math.min(memory, 2048)}M`, `-Xmx${memory}M`);
  // 26.x files carry Mojang's recommended flags (ZGC, compact headers); memory comes from us.
  for (const arg of collect(v.arguments && v.arguments['default-user-jvm'], vars, features)) {
    if (/^-Xm[sx]/.test(arg)) continue;
    if (userPicksGc && (GC_FLAG.test(arg) || GC_TUNING.test(arg) || arg === '-XX:+UnlockExperimentalVMOptions')) continue;
    jvm.push(arg);
  }
  if (v.arguments && v.arguments.jvm) {
    jvm.push(...collect(v.arguments.jvm, vars, features));
  } else {
    if (process.platform === 'win32') jvm.push('-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump');
    jvm.push(`-Djava.library.path=${prepared.nativesDir}`, '-Dminecraft.launcher.brand=Aurora', `-Dminecraft.launcher.version=${o.launcherVersion}`, '-cp', vars.classpath);
  }
  const logging = v.logging && v.logging.client;
  if (logging && prepared.loggingFile) jvm.push(substitute(logging.argument, { path: prepared.loggingFile }));
  // Tells the Aurora mod that the account was picked in the launcher, and where skins are.
  jvm.push(`-Daurora.launcher=${o.launcherVersion}`);
  for (const [key, value] of Object.entries(o.properties || {})) jvm.push(`-D${key}=${value}`);
  if (account.type !== 'microsoft') jvm.push(...offlineServiceHosts(v));
  jvm.push(...user);

  let game;
  if (v.arguments && v.arguments.game) {
    game = collect(v.arguments.game, vars, features);
  } else {
    game = splitArgs(v.minecraftArguments).map((a) => substitute(a, vars));
    if (o.resolution) game.push('--width', vars.resolution_width, '--height', vars.resolution_height);
  }
  if (o.server && !quickPlay) {
    const [host, port] = o.server.split(':');
    game.push('--server', host, '--port', port || '25565');
  }
  game = dropEmpty(game, ['--clientId', '--xuid', '--quickPlayPath', '--quickPlaySingleplayer', '--quickPlayMultiplayer', '--quickPlayRealms']);
  return { java: prepared.javaPath, args: [...jvm, v.mainClass, ...game], cwd: prepared.gameDir };
}

/** Hides the session token when a command line goes to a log file. */
function redact(args) {
  return args.map((a, i) => (args[i - 1] === '--accessToken' || args[i - 1] === '--session' ? '••••••' : a));
}

const LEVELS = ['FATAL', 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'];

function unescapeXml(s) {
  return s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&#(\d+);/g, (m, n) => String.fromCharCode(+n)).replace(/&amp;/g, '&');
}

function cdata(xml, tag) {
  const m = xml.match(new RegExp(`<log4j:${tag}>([\\s\\S]*?)</log4j:${tag}>`));
  if (!m) return '';
  const body = m[1];
  if (!body.includes('<![CDATA[')) return unescapeXml(body);
  let out = '';
  const re = /<!\[CDATA\[([\s\S]*?)\]\]>/g;
  let part;
  while ((part = re.exec(body))) out += part[1];
  return out;
}

/**
 * Turns the game's output into log entries. Mojang's logging config writes log4j XML events to
 * stdout; anything else (early Fabric lines, System.out prints) arrives as plain text.
 */
class LogParser {
  constructor(onEntry) {
    this.buffer = '';
    this.onEntry = onEntry;
  }

  plain(line) {
    if (!line.trim()) return;
    const m = line.match(/^\[(\d\d:\d\d:\d\d)\] \[([^\]]+?)\/(\w+)\](?: \(([^)]+)\))?:? ?(.*)$/);
    if (m) {
      this.onEntry({ time: m[1], thread: m[2], level: LEVELS.includes(m[3]) ? m[3] : 'INFO', logger: m[4] || '', message: m[5] });
    } else {
      this.onEntry({ time: null, thread: '', level: /exception|error|^\s+at /i.test(line) ? 'ERROR' : 'INFO', logger: '', message: line });
    }
  }

  push(text) {
    this.buffer += text;
    for (;;) {
      const start = this.buffer.indexOf('<log4j:Event');
      if (start === -1) {
        const cut = this.buffer.lastIndexOf('\n');
        if (cut === -1) {
          // Hold back a tail that could be the start of an event tag.
          if (this.buffer.length > 65536) {
            this.plain(this.buffer);
            this.buffer = '';
          }
          return;
        }
        this.buffer.slice(0, cut).split(/\r?\n/).forEach((l) => this.plain(l));
        this.buffer = this.buffer.slice(cut + 1);
        return;
      }
      if (start > 0) {
        this.buffer.slice(0, start).split(/\r?\n/).forEach((l) => this.plain(l));
        this.buffer = this.buffer.slice(start);
      }
      const end = this.buffer.indexOf('</log4j:Event>');
      if (end === -1) return;
      const xml = this.buffer.slice(0, end + 14);
      this.buffer = this.buffer.slice(end + 14);
      const attr = (name) => {
        const m = xml.match(new RegExp(`${name}="([^"]*)"`));
        return m ? unescapeXml(m[1]) : '';
      };
      const ts = parseInt(attr('timestamp'), 10);
      const time = Number.isFinite(ts) ? new Date(ts).toTimeString().slice(0, 8) : null;
      const message = cdata(xml, 'Message');
      const throwable = cdata(xml, 'Throwable');
      this.onEntry({ time, thread: attr('thread'), level: attr('level') || 'INFO', logger: attr('logger'), message: throwable ? `${message}\n${throwable.trimEnd()}` : message });
    }
  }

  flush() {
    if (this.buffer.trim()) this.plain(this.buffer);
    this.buffer = '';
  }
}

/**
 * A running game: spawns java, parses its log and reports when the window is up and when the
 * process exits. The game outlives the launcher if the launcher is closed.
 */
class GameProcess extends EventEmitter {
  constructor(command, { logFile } = {}) {
    super();
    this.command = command;
    this.logFile = logFile;
    this.started = Date.now();
    this.windowShown = false;
    this.lines = [];
  }

  start() {
    const { java, args, cwd } = this.command;
    fs.mkdirSync(cwd, { recursive: true });
    if (this.logFile) {
      fs.mkdirSync(path.dirname(this.logFile), { recursive: true });
      this.log = fs.createWriteStream(this.logFile);
      this.log.write(`${java} ${redact(args).join(' ')}\n\n`);
    }
    const env = { ...process.env };
    // Variables Electron sets for itself must not leak into Java.
    delete env.ELECTRON_RUN_AS_NODE;
    delete env.ELECTRON_NO_ATTACH_CONSOLE;
    // Detached: no console window for java.exe, and the game outlives the launcher. windowsHide
    // stays off because Windows applies it to the first ShowWindow call, i.e. the game window.
    this.child = spawn(java, args, { cwd, env, windowsHide: false, detached: true, stdio: ['ignore', 'pipe', 'pipe'] });
    this.pid = this.child.pid;
    const parser = new LogParser((entry) => this.entry(entry));
    this.child.stdout.setEncoding('utf8');
    this.child.stderr.setEncoding('utf8');
    this.child.stdout.on('data', (t) => parser.push(t));
    this.child.stderr.on('data', (t) => t.split(/\r?\n/).forEach((l) => parser.plain(l)));
    this.child.on('error', (e) => {
      this.emit('failed', e);
    });
    this.child.on('exit', (code, signal) => {
      parser.flush();
      this.log?.end();
      this.emit('exit', { code, signal, runtime: Date.now() - this.started });
    });
    this.child.unref();
    return this;
  }

  entry(entry) {
    this.lines.push(entry);
    if (this.lines.length > 4000) this.lines.splice(0, this.lines.length - 4000);
    this.log?.write(`[${entry.time || '--:--:--'}] [${entry.thread}/${entry.level}] ${entry.message}\n`);
    this.emit('log', entry);
    // 26.x logs "Created window"; older versions are on screen by the time sound starts.
    if (!this.windowShown && /Created window|Sound engine started/i.test(entry.message)) {
      this.windowShown = true;
      this.emit('window');
    }
  }

  kill() {
    if (!this.child || this.child.exitCode !== null) return;
    // TerminateProcess first; taskkill also takes down anything the game started itself.
    this.child.kill('SIGKILL');
    if (process.platform === 'win32') {
      const tk = spawn('taskkill', ['/pid', String(this.child.pid), '/T', '/F'], { windowsHide: true, stdio: 'ignore' });
      tk.on('error', (e) => this.emit('trace', `taskkill failed: ${e.message}`));
    }
  }
}

module.exports = { buildCommand, splitArgs, redact, GameProcess, LogParser };
