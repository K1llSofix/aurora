'use strict';

// Development only: checks what the trailer capture can do. Run from launcher/ with
//   AURORA_SCRIPT=trailer/probe.js AURORA_HIDDEN=1 npx electron . --force-device-scale-factor=1.5
// Writes trailer/out/probe.json (frame size, frame rate reached, page structure).

const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const { ffmpegPath } = require('./ffmpeg');

const OUT = path.join(__dirname, 'out');
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

module.exports = ({ win, app }) => {
  const js = (code) => win.webContents.executeJavaScript(code, true);
  const report = {};
  win.webContents.once('did-finish-load', async () => {
    try {
      fs.mkdirSync(OUT, { recursive: true });
      win.setSize(1280, 780);
      win.webContents.setFrameRate(60);
      await wait(2500);
      let latest = null;
      let paints = 0;
      win.webContents.on('paint', (event, dirty, image) => {
        latest = image;
        paints++;
      });
      await wait(1500);
      report.frame = latest ? latest.getSize() : null;
      report.scale = await js('window.devicePixelRatio');

      // Five seconds to the encoder at a steady 60 fps while the page animates.
      const size = latest.getSize();
      const ff = spawn(ffmpegPath(), ['-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'bgra', '-s', `${size.width}x${size.height}`, '-r', '60', '-i', '-',
        '-c:v', 'h264_nvenc', '-preset', 'p4', '-cq', '16', '-pix_fmt', 'yuv420p', path.join(OUT, 'probe.mp4')]);
      let errors = '';
      ff.stderr.on('data', (d) => { errors += d; });
      const start = Date.now();
      let written = 0;
      let blocked = 0;
      paints = 0;
      await new Promise((resolve) => {
        const tick = () => {
          const due = Math.floor(((Date.now() - start) / 1000) * 60);
          while (written < due && written < 300) {
            const ok = ff.stdin.write(latest.toBitmap());
            if (!ok) blocked++;
            written++;
          }
          if (written >= 300) return resolve();
          setTimeout(tick, 4);
        };
        tick();
      });
      await new Promise((r) => { ff.on('close', r); ff.stdin.end(); });
      report.capture = { seconds: (Date.now() - start) / 1000, written, paints, blocked, errors };

      // What the pages look like, for the director's selectors.
      report.dom = {};
      for (const page of ['home', 'installs', 'servers', 'mods', 'settings']) {
        await js(`document.querySelector('.nav-item[data-page="${page}"]').click()`);
        await wait(1500);
        report.dom[page] = await js(`(() => {
          const seen = new Set();
          const out = [];
          for (const el of document.querySelectorAll('#root *')) {
            if (!el.className || typeof el.className !== 'string') continue;
            const key = el.tagName.toLowerCase() + '.' + el.className.trim().split(/\\s+/).join('.');
            if (seen.has(key)) continue;
            seen.add(key);
            const r = el.getBoundingClientRect();
            if (r.width < 4 || r.height < 4) continue;
            out.push(key + ' @' + Math.round(r.x) + ',' + Math.round(r.y) + ' ' + Math.round(r.width) + 'x' + Math.round(r.height));
          }
          return out.slice(0, 160);
        })()`);
      }
    } catch (e) {
      report.error = String(e && e.stack || e);
    }
    fs.writeFileSync(path.join(OUT, 'probe.json'), JSON.stringify(report, null, 2));
    app.quit();
  });
};
