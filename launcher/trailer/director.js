'use strict';

// Development only: films the launcher for the trailer. Each scene is played with a scripted
// mouse (real hover and click events, drawn with a smooth cursor) and recorded at 60 fps from the
// offscreen window into trailer/out/footage/<scene>.mp4, with its clicks in <scene>.json.
//
//   AURORA_SCRIPT=trailer/director.js AURORA_HIDDEN=1 npx electron .
//
// AURORA_TRAILER_SCENES picks scenes (default: all). Launching the game is faked: the play
// buttons show a download, the start and a running game without starting Minecraft.

const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const { ipcMain } = require('electron');
const { ffmpegPath } = require('./ffmpeg');

const OUT = path.join(__dirname, 'out', 'footage');
const ZOOM = 1.5;
const W = 1280;
const H = 780;
const FPS = 60;
const ALL = ['bg', 'intro', 'play', 'servers', 'mods', 'skin', 'installs', 'settings'];
const SCENES = (process.env.AURORA_TRAILER_SCENES || ALL.join(',')).split(',');

const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const ease = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);

module.exports = ({ win, app, send }) => {
  const js = (code) => win.webContents.executeJavaScript(code, true);
  let latest = null;
  let latestBitmap = null;
  win.webContents.on('paint', (event, dirty, image) => {
    latest = image;
    latestBitmap = null;
  });

  // ------------------------------------------------------------------ recording

  function startRecording(name) {
    const size = latest.getSize();
    const file = path.join(OUT, `${name}.mp4`);
    const ff = spawn(ffmpegPath(), ['-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'bgra', '-s', `${size.width}x${size.height}`,
      '-r', String(FPS), '-i', '-', '-c:v', 'h264_nvenc', '-preset', 'p5', '-rc', 'vbr', '-cq', '14', '-pix_fmt', 'yuv420p', file]);
    ff.stderr.on('data', (d) => process.stdout.write(`[ffmpeg ${name}] ${d}`));
    const rec = { name, start: Date.now(), written: 0, marks: [], stopped: false, size };
    const tick = () => {
      if (rec.stopped) return;
      const due = Math.floor(((Date.now() - rec.start) / 1000) * FPS);
      while (rec.written < due) {
        if (!latestBitmap) latestBitmap = latest.toBitmap();
        ff.stdin.write(latestBitmap);
        rec.written++;
      }
      setTimeout(tick, 3);
    };
    tick();
    rec.mark = (type, extra = {}) => rec.marks.push({ type, t: (Date.now() - rec.start) / 1000, ...extra });
    rec.stop = () => new Promise((resolve) => {
      rec.stopped = true;
      ff.on('close', resolve);
      ff.stdin.end();
      fs.writeFileSync(path.join(OUT, `${name}.json`), JSON.stringify({ seconds: rec.written / FPS, width: size.width, height: size.height, zoom: ZOOM, marks: rec.marks }, null, 2));
    });
    return rec;
  }

  // ------------------------------------------------------------------ the mouse

  const mouse = { x: W / 2, y: H / 2 };
  let rec = null;

  function sendMove(x, y, buttons = false) {
    win.webContents.sendInputEvent({ type: 'mouseMove', x: Math.round(x * ZOOM), y: Math.round(y * ZOOM), modifiers: buttons ? ['leftButtonDown'] : [] });
  }

  /** Glides the cursor along a gentle arc, like a hand would. */
  async function moveTo(x, y, ms = 650, drag = false) {
    const from = { ...mouse };
    const dist = Math.hypot(x - from.x, y - from.y);
    const steps = Math.max(1, Math.round(ms / (1000 / FPS)));
    const nx = -(y - from.y) / (dist || 1);
    const ny = (x - from.x) / (dist || 1);
    for (let i = 1; i <= steps; i++) {
      const t = ease(i / steps);
      const arc = Math.sin(Math.PI * t) * Math.min(60, dist * 0.12);
      mouse.x = from.x + (x - from.x) * t + nx * arc;
      mouse.y = from.y + (y - from.y) * t + ny * arc;
      sendMove(mouse.x, mouse.y, drag);
      await wait(1000 / FPS);
    }
  }

  async function rect(selector, index = 0) {
    const r = await js(`(() => { const el = document.querySelectorAll(${JSON.stringify(selector)})[${index}]; if (!el) return null; const b = el.getBoundingClientRect(); return { x: b.x, y: b.y, w: b.width, h: b.height }; })()`);
    if (!r) throw new Error(`not on screen: ${selector}[${index}]`);
    return r;
  }

  async function waitFor(selector, timeout = 8000) {
    const end = Date.now() + timeout;
    while (Date.now() < end) {
      if (await js(`Boolean(document.querySelector(${JSON.stringify(selector)}))`)) return;
      await wait(100);
    }
    throw new Error(`timed out waiting for ${selector}`);
  }

  async function hover(selector, index = 0, ms = 650, fx = 0.5, fy = 0.5) {
    const r = await rect(selector, index);
    await moveTo(r.x + r.w * fx, r.y + r.h * fy, ms);
  }

  async function click(selector, index, ms, fx, fy) {
    if (selector) await hover(selector, index, ms, fx, fy);
    rec?.mark('click');
    win.webContents.sendInputEvent({ type: 'mouseDown', x: Math.round(mouse.x * ZOOM), y: Math.round(mouse.y * ZOOM), button: 'left', clickCount: 1 });
    await wait(80);
    win.webContents.sendInputEvent({ type: 'mouseUp', x: Math.round(mouse.x * ZOOM), y: Math.round(mouse.y * ZOOM), button: 'left', clickCount: 1 });
    await wait(60);
  }

  async function type(text) {
    for (const ch of text) {
      rec?.mark('key');
      win.webContents.sendInputEvent({ type: 'keyDown', keyCode: ch });
      win.webContents.sendInputEvent({ type: 'char', keyCode: ch });
      win.webContents.sendInputEvent({ type: 'keyUp', keyCode: ch });
      await wait(85 + Math.random() * 60);
    }
  }

  async function escape() {
    win.webContents.sendInputEvent({ type: 'keyDown', keyCode: 'Escape' });
    win.webContents.sendInputEvent({ type: 'keyUp', keyCode: 'Escape' });
    await wait(250);
  }

  async function cursor(visible) {
    await js(`document.getElementById('trailer-cursor').style.opacity = '${visible ? 1 : 0}'; true`);
  }

  async function nav(page) {
    await click(`.nav-item[data-page="${page}"]`, 0, 700);
    await wait(500);
  }

  // ------------------------------------------------------------------ the fake game

  let launches = 0;
  async function fakeLaunch(opts = {}) {
    launches++;
    const quick = Boolean(opts.server) || launches > 1;
    send('game:state', { state: 'preparing', installId: null, pid: null });
    await wait(450);
    if (!quick) {
      const totalBytes = 742 * 1024 * 1024;
      const steps = 46;
      for (let i = 1; i <= steps; i++) {
        const f = ease(i / steps);
        send('game:progress', { stage: 'download', done: Math.round(f * 3812), total: 3812, doneBytes: Math.round(f * totalBytes), totalBytes, speed: (68 + Math.sin(i / 3) * 9) * 1024 * 1024 });
        await wait(65);
      }
    }
    send('game:state', { state: 'starting', installId: null, pid: null });
    await wait(quick ? 700 : 900);
    send('game:state', { state: 'running', installId: null, pid: 4242 });
  }

  function gameIdle() {
    send('game:state', { state: 'idle', installId: null, pid: null });
  }

  // ------------------------------------------------------------------ scenes

  const scenes = {
    // The pure aurora scene, full frame, for behind the launcher and the title cards.
    async bg() {
      await js("document.getElementById('root').style.visibility='hidden';document.getElementById('shade').style.visibility='hidden';true");
      win.setSize(1920, 1080);
      win.webContents.setZoomFactor(1);
      await wait(1500);
      rec = startRecording('bg');
      await wait(66000); // the whole trailer long, so it never loops
      await rec.stop();
      await js("document.getElementById('root').style.visibility='';document.getElementById('shade').style.visibility='';true");
      win.setSize(W * ZOOM, H * ZOOM);
      win.webContents.setZoomFactor(ZOOM);
      await wait(1500);
    },

    // The home page on its own: the launcher fades in over it in the edit.
    async intro() {
      await cursor(false);
      await nav('home');
      await cursor(false);
      await wait(1200);
      rec = startRecording('intro');
      await wait(9000);
      await rec.stop();
    },

    async play() {
      mouse.x = 760;
      mouse.y = 330;
      sendMove(mouse.x, mouse.y);
      rec = startRecording('play');
      await wait(600);
      await hover('.play-btn', 0, 1100);
      await wait(250);
      await click(null);
      await wait(6500);
      await rec.stop();
      gameIdle();
    },

    async servers() {
      await nav('servers');
      await wait(2500); // live pings come in
      rec = startRecording('servers');
      await wait(300);
      await hover('.server-row', 0, 800, 0.4, 0.5);
      await wait(400);
      await hover('.server-row', 1, 500, 0.45, 0.5);
      await wait(350);
      await click('.server-row .btn.primary', 1, 600);
      await wait(4200);
      await rec.stop();
      gameIdle();
    },

    async mods() {
      await nav('mods');
      await wait(600);
      rec = startRecording('mods');
      await wait(300);
      const catalog = await js("document.querySelectorAll('.page-head .actions .tabs button').length - 1");
      await click('.page-head .actions .tabs button', catalog, 800);
      await wait(1400);
      await click('.toolbar input', 0, 600, 0.3, 0.5);
      await type('sodium');
      await wait(1600);
      await hover('.mod-card .btn.primary', 0, 700);
      await wait(700);
      await click('.tabs.kinds button', 1, 700);
      await wait(1500);
      await click('.tabs.kinds button', 2, 600);
      await wait(1600);
      await rec.stop();
    },

    async skin() {
      await nav('home');
      rec = startRecording('skin');
      await click('.account-card', 0, 800);
      await wait(400);
      await click('.popover .icon-btn:not(.danger)', 0, 500);
      await waitFor('.skin-stage');
      await wait(900);
      const swatches = await js("document.querySelectorAll('.skin-swatch').length");
      if (swatches > 3) {
        await click('.skin-swatch', Math.min(5, swatches - 1), 700);
        await wait(250);
      }
      // A few strokes on the body.
      const stage = await rect('.skin-stage');
      const cx = stage.x + stage.w * 0.5;
      const cy = stage.y + stage.h * 0.46;
      await moveTo(cx - 18, cy - 20, 500);
      rec.mark('paint');
      win.webContents.sendInputEvent({ type: 'mouseDown', x: Math.round(mouse.x * ZOOM), y: Math.round(mouse.y * ZOOM), button: 'left', clickCount: 1 });
      for (const [dx, dy] of [[18, -20], [16, 0], [-14, 6], [12, 18], [-16, 24], [20, 26]]) await moveTo(cx + dx, cy + dy, 260, true);
      win.webContents.sendInputEvent({ type: 'mouseUp', x: Math.round(mouse.x * ZOOM), y: Math.round(mouse.y * ZOOM), button: 'left', clickCount: 1 });
      await wait(500);
      await click('.skin-right .tabs button', 1, 700);
      await wait(2600);
      await hover('.skin-tile', 2, 600);
      await wait(900);
      await rec.stop();
      await escape();
      await escape();
    },

    async installs() {
      await nav('installs');
      rec = startRecording('installs');
      await wait(300);
      await click('.inst-card .icon-btn', 0, 900);
      await wait(500);
      // Over "Desktop shortcut", without clicking it: that would make a real shortcut.
      await hover('.popover .menu-item', 1, 500);
      await wait(900);
      await hover('.popover .menu-item', 0, 400);
      await click(null);
      await waitFor('.icon-pick');
      await wait(500);
      await click('.icon-pick', 0, 700);
      await wait(700);
      const tiles = await js("document.querySelectorAll('.icon-cell').length");
      if (tiles > 8) {
        await hover('.icon-cell', 6, 600);
        await wait(250);
        await hover('.icon-cell', 9, 400);
        await wait(250);
        await click('.icon-cell', 12, 500);
      }
      await wait(1400);
      await rec.stop();
      await escape();
      await escape();
    },

    async settings() {
      await nav('settings');
      await js("document.querySelector('.page').scrollTo(0, 0); true");
      rec = startRecording('settings');
      await wait(300);
      await js("(() => { const row = [...document.querySelectorAll('.set-row')].find((r) => /Discord/.test(r.textContent)); row && row.scrollIntoView({ behavior: 'smooth', block: 'center' }); return true; })()");
      await wait(1200);
      const toggle = await js("(() => { const row = [...document.querySelectorAll('.set-row')].find((r) => /Discord/.test(r.textContent)); if (!row) return null; const b = row.querySelector('.switch').getBoundingClientRect(); return { x: b.x + b.width / 2, y: b.y + b.height / 2 }; })()");
      if (toggle) {
        await moveTo(toggle.x - 6, toggle.y, 900);
        await wait(1600);
      }
      await rec.stop();
    },
  };

  // ------------------------------------------------------------------ run

  win.webContents.once('did-finish-load', async () => {
    try {
      fs.mkdirSync(OUT, { recursive: true });
      ipcMain.removeHandler('game:launch');
      ipcMain.handle('game:launch', (event, opts) => {
        fakeLaunch(opts || {});
        return { ok: true, value: null };
      });
      win.setSize(W * ZOOM, H * ZOOM);
      win.webContents.setZoomFactor(ZOOM);
      win.webContents.setFrameRate(FPS);
      // The player's own name on the account card, the interface in English, then a fresh page.
      await js(`(async () => {
        await window.aurora.accounts.addOffline('K1llSofix');
        const res = await window.aurora.accounts.list();
        const value = res && res.value !== undefined ? res.value : res;
        const list = Array.isArray(value) ? value : (value && value.accounts) || [];
        for (const a of list) if (a.name !== 'K1llSofix') await window.aurora.accounts.remove(a.id);
        await window.aurora.settings.set({ language: 'en' });
        return true;
      })()`);
      await new Promise((resolve) => {
        win.webContents.once('did-finish-load', resolve);
        win.webContents.reload();
      });
      await wait(2500);
      await js('window.__scene && (window.__scene.fps = 61); true');
      await js(`(() => {
        if (document.getElementById('trailer-cursor')) return true;
        const style = document.createElement('style');
        style.textContent = '#trailer-cursor{position:fixed;left:0;top:0;z-index:2147483647;pointer-events:none;opacity:0;transition:opacity .3s;filter:drop-shadow(0 3px 6px rgba(0,0,0,.5))}' +
          '#trailer-ring{position:fixed;left:0;top:0;z-index:2147483646;pointer-events:none;width:44px;height:44px;margin:-22px 0 0 -22px;border-radius:50%;border:2px solid rgba(94,234,212,.9);opacity:0}' +
          '#trailer-ring.go{animation:trailer-ring .5s ease-out}' +
          '@keyframes trailer-ring{0%{opacity:.9;transform:scale(.3)}100%{opacity:0;transform:scale(1.25)}}' +
          '*{cursor:none!important}';
        document.head.appendChild(style);
        const c = document.createElement('div');
        c.id = 'trailer-cursor';
        c.innerHTML = '<svg width="24" height="24" viewBox="0 0 24 24"><path d="M5 2.5v17.2l4.6-4.5 3.1 6.6 3-1.4-3.1-6.5h6.5z" fill="#fff" stroke="#0b0d12" stroke-width="1.3" stroke-linejoin="round"/></svg>';
        const ring = document.createElement('div');
        ring.id = 'trailer-ring';
        document.body.append(ring, c);
        addEventListener('mousemove', (e) => { c.style.transform = 'translate(' + (e.clientX - 5) + 'px,' + (e.clientY - 2) + 'px)'; }, true);
        addEventListener('mousedown', (e) => { ring.style.left = e.clientX + 'px'; ring.style.top = e.clientY + 'px'; ring.classList.remove('go'); void ring.offsetWidth; ring.classList.add('go'); }, true);
        return true;
      })()`);
      while (!latest) await wait(50);
      for (const name of SCENES) {
        if (!scenes[name]) continue;
        console.log(`[trailer] scene ${name}`);
        try {
          await scenes[name]();
        } catch (e) {
          console.log(`[trailer] scene ${name} failed: ${e && e.stack || e}`);
          if (rec && !rec.stopped) await rec.stop();
        }
        rec = null;
        await cursor(true);
      }
    } catch (e) {
      console.error('[trailer] failed', e);
    }
    if (!process.env.AURORA_KEEP) app.quit();
  });
};
