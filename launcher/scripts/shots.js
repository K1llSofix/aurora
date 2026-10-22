'use strict';

// Development only: drives the launcher window and saves screenshots of each page, so the
// interface can be checked without touching the mouse. Run with
//   AURORA_SCRIPT=scripts/shots.js electron .
// Screenshots go to AURORA_SHOTS (default: launcher/shots).

const fs = require('fs');
const path = require('path');

const OUT = process.env.AURORA_SHOTS || path.join(__dirname, '..', 'shots');
const PLAN = (process.env.AURORA_PLAN || 'home,installs,servers,mods,mods-catalog,settings,about').split(',');

const wait = (ms) => new Promise((r) => setTimeout(r, ms));

module.exports = ({ win, app }) => {
  const js = (code) => win.webContents.executeJavaScript(code, true);
  const shot = async (name) => {
    const image = await win.webContents.capturePage();
    fs.mkdirSync(OUT, { recursive: true });
    fs.writeFileSync(path.join(OUT, `${name}.png`), image.toPNG());
    console.log('[shots] saved', name);
  };
  win.webContents.once('did-finish-load', async () => {
    try {
      win.setSize(Number(process.env.AURORA_W || 1280), Number(process.env.AURORA_H || 780));
      await wait(2500);
      for (const step of PLAN) {
        const [page, extra] = step.split('-');
        await js(`document.querySelector('.nav-item[data-page="${page}"]')?.click()`);
        await wait(900);
        if (extra === 'catalog') {
          await js("[...document.querySelectorAll('.tabs button')].pop()?.click()");
          await wait(3500);
        }
        if (extra === 'editor') {
          await js("document.querySelector('.inst-card .icon-btn')?.click()");
          await wait(300);
          await js("document.querySelector('.popover .menu-item')?.click()");
          await wait(900);
        }
        if (extra === 'accounts') {
          await js("document.querySelector('.account-card')?.click()");
          await wait(700);
        }
        if (extra === 'add') {
          await js("document.querySelector('.account-card')?.click()");
          await wait(400);
          await js("[...document.querySelectorAll('.popover .menu-item')].pop()?.click()");
          await wait(700);
        }
        if (page === 'launch') {
          // Full launch through the interface: an offline account, Play, the game, then stop it.
          await js("window.aurora.accounts.addOffline('AuroraTester')");
          await wait(800);
          await js("document.querySelector('.play-btn').click()");
          for (let i = 0; i < 40; i++) {
            await wait(500);
            const s = await js("document.querySelector('.play-btn').className + ' | ' + document.querySelector('.playbar .status').innerText.replace(/\\n/g, ' / ')");
            console.log('[shots] state', s);
            if (i === 1) await shot('launch-preparing');
            if (s.includes('running')) break;
          }
          await shot('launch-running');
          await wait(Number(process.env.AURORA_PLAY_SECONDS || 20) * 1000);
          await js("window.aurora.game.kill()");
          await wait(4000);
          await shot('launch-after');
          continue;
        }
        if (page === 'iconpicker') {
          await js("document.querySelector('.nav-item[data-page=\"installs\"]').click()");
          await wait(700);
          await js("document.querySelector('.inst-card .icon-btn').click()");
          await wait(300);
          await js("document.querySelector('.popover .menu-item').click()");
          await wait(800);
          await js("document.querySelector('.icon-pick').click()");
          await wait(900);
          await shot('iconpicker');
          await js("document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}))");
          await wait(200);
          await js("document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}))");
          await wait(400);
          continue;
        }
        if (page === 'packs') {
          await js("document.querySelector('.nav-item[data-page=\"mods\"]').click()");
          await wait(700);
          await js(`[...document.querySelectorAll('.tabs.kinds button')][${extra === 'shader' ? 2 : 1}].click()`);
          await wait(1200);
          await shot(`packs-${extra}-installed`);
          await js("[...document.querySelectorAll('.page-head .actions .tabs button')].pop().click()");
          await wait(4000);
          await shot(`packs-${extra}-catalog`);
          continue;
        }
        if (page === 'skin') {
          await js("document.querySelector('.account-card').click()");
          await wait(500);
          await js("document.querySelector('.popover .icon-btn:not(.danger)').click()");
          await wait(2500);
          await shot('skin-draw');
          await js("[...document.querySelectorAll('.skin-right .tabs button')][1].click()");
          await wait(6000);
          await shot('skin-gallery');
          await js("[...document.querySelectorAll('.skin-right .tabs button')][2].click()");
          await wait(400);
          await js("const i = document.querySelector('.skin-name-tab input'); i.value = 'Notch'; i.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter'})); true");
          await wait(4000);
          await shot('skin-byname');
          await js("document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}))");
          await wait(400);
          continue;
        }
        if (page === 'lang') {
          await js(`window.aurora.settings.set({ language: '${extra}' })`);
          await wait(1200);
          continue;
        }
        if (page === 'msflow') {
          // Microsoft sign-in without a Client ID stops at the Client ID form.
          await js("document.querySelector('.account-card').click()");
          await wait(500);
          await js("[...document.querySelectorAll('.popover .menu-item')].pop().click()");
          await wait(600);
          await js("document.querySelector('.choice').click()");
          await wait(900);
          await shot('msflow-client');
          await js("const i = document.querySelector('.modal input'); i.value = 'not-a-guid'; true");
          await js("[...document.querySelectorAll('.modal-foot .btn')].pop().click()");
          await wait(600);
          await shot('msflow-invalid');
          continue;
        }
        if (page === 'modinstall') {
          await js("document.querySelector('.nav-item[data-page=\"mods\"]').click()");
          await wait(800);
          await js("[...document.querySelectorAll('.tabs button')].pop().click()");
          await wait(1500);
          await js("const i = document.querySelector('.toolbar input'); i.value = 'sodium'; i.dispatchEvent(new Event('input')); true");
          await wait(3500);
          await js("document.querySelector('.mod-card .btn.primary')?.click()");
          await wait(9000);
          await shot('modinstall-catalog');
          await js("document.querySelector('.tabs button').click()");
          await wait(1500);
          await shot('modinstall-installed');
          continue;
        }
        if (page === 'modpacks') {
          // The modpack catalog, the install dialog and (with AURORA_PACK_INSTALL) a whole install.
          await js("document.querySelector('.nav-item[data-page=\"mods\"]').click()");
          await wait(700);
          await js("[...document.querySelectorAll('.tabs.kinds button')][3].click()");
          await wait(4500);
          await shot('modpacks-catalog');
          await js("document.querySelector('.mod-card .btn.primary')?.click()");
          await wait(3500);
          await shot('modpack-dialog');
          if (process.env.AURORA_PACK_MC) {
            // Another version through the picker, found by its Minecraft version.
            await js("document.querySelector('.modal .select').click()");
            await wait(500);
            await js(`const i = document.querySelector('.popover input'); i.value = ${JSON.stringify(process.env.AURORA_PACK_MC)}; i.dispatchEvent(new Event('input')); true`);
            await wait(400);
            await shot('modpack-versions');
            await js("document.querySelector('.popover .menu-item').click()");
            await wait(600);
            await shot('modpack-dialog-picked');
          }
          if (!process.env.AURORA_PACK_INSTALL) {
            await js("document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}))");
            await wait(400);
            continue;
          }
          await js("document.querySelector('.modal-foot .btn.primary').click()");
          for (let i = 0; i < 240; i++) {
            await wait(1000);
            const status = await js("(document.querySelector('.pack-progress')?.innerText || '').replace(/\\n/g, ' | ') + ' modal=' + Boolean(document.querySelector('.modal'))");
            if (i % 5 === 0) console.log('[shots] pack', status);
            if (i === 1) await shot('modpack-progress');
            if (!status.includes('modal=true')) break;
          }
          await wait(500);
          await shot('modpack-done');
          await js("document.querySelector('.nav-item[data-page=\"installs\"]').click()");
          await wait(1500);
          await shot('modpack-installs');
          console.log('[shots] installs', await js("(async () => JSON.stringify((await window.aurora.installs.list()).value.map((i) => ({ name: i.name, version: i.version, loader: i.loader, loaderVersion: i.loaderVersion, aurora: i.aurora, icon: i.icon, modpack: i.modpack, mods: i.modCount }))))()"));
          continue;
        }
        if (page === 'update') {
          // A launcher update from AURORA_UPDATE_FEED: the sidebar note, the settings row, then
          // the restart (with AURORA_UPDATE_DRY the installer is only logged). Put it last.
          for (let i = 0; i < 60; i++) {
            const status = await js('(async () => (await window.aurora.updates.state()).value.status)()');
            if (i % 5 === 0) console.log('[shots] update', status);
            if (status === 'ready' || status === 'error') break;
            await wait(1000);
          }
          await js("document.querySelector('.nav-item[data-page=\"home\"]').click()");
          await wait(1500);
          await shot('update-sidebar');
          await js("document.querySelector('.nav-item[data-page=\"settings\"]').click()");
          await wait(900);
          await js("[...document.querySelectorAll('.set-row')].find((r) => r.innerText.includes('Aurora Launcher'))?.scrollIntoView({ block: 'center' })");
          await wait(500);
          await shot('update-settings');
          console.log('[shots] restarting through the sidebar note');
          await js("document.querySelector('.update-note')?.click()");
          await wait(3000);
          continue;
        }
        if (page === 'packcard') {
          // The "Install a modpack" card on the installations page and its menu.
          await js("document.querySelector('.nav-item[data-page=\"installs\"]').click()");
          await wait(900);
          await js("[...document.querySelectorAll('.inst-card.add')].pop().scrollIntoView({ block: 'center' })");
          await wait(400);
          await js("[...document.querySelectorAll('.inst-card.add')].pop().click()");
          await wait(500);
          await shot('installs-packcard');
          await js("document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}))");
          await wait(300);
          continue;
        }
        if (page === 'mrpack') {
          // Importing .mrpack files (AURORA_MRPACKS: paths separated by ;), each opened like a drop.
          for (const [i, file] of (process.env.AURORA_MRPACKS || '').split(';').filter(Boolean).entries()) {
            await js("document.querySelector('.nav-item[data-page=\"installs\"]').click()");
            await wait(700);
            await js(`import('./js/pages/installs.js').then((m) => m.openPack(${JSON.stringify(file)}))`);
            await wait(1500);
            await shot(`mrpack-${i}`);
            await js("document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}))");
            await wait(400);
          }
          continue;
        }
        if (page === 'crash') {
          await js("window.aurora.settings.set({ jvmArgs: '-XX:+ThisFlagDoesNotExist' })");
          await wait(500);
          await js("document.querySelector('.play-btn').click()");
          await wait(9000);
          await shot('crash');
          await js("window.aurora.settings.set({ jvmArgs: '' })");
          continue;
        }
        if (page === 'scene') {
          await js("document.getElementById('root').style.display='none';document.getElementById('shade').style.display='none';true");
          if (extra) await js(`window.__scene.debug=${JSON.stringify(extra)};window.__scene.draw(0);true`);
          await wait(300);
          await shot(step);
          await js("document.getElementById('root').style.display='';document.getElementById('shade').style.display='';window.__scene.debug=null;true");
          continue;
        }
        if (extra === 'console') {
          await js("document.querySelector('.playbar .icon-btn')?.click()");
          await wait(700);
        }
        await wait(page === 'servers' || page === 'home' ? 2500 : 600);
        await shot(step);
        await js("document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}))");
        await wait(300);
      }
      const errors = await js('window.__errors || []');
      if (errors.length) console.log('[shots] page errors:', JSON.stringify(errors));
    } catch (e) {
      console.error('[shots] failed', e);
    }
    if (!process.env.AURORA_KEEP) app.quit();
  });
  win.webContents.on('console-message', (event) => {
    const { level, message, lineNumber, sourceId } = event;
    if (level === 'error' || level === 'warning' || level === 3 || level === 2) console.log(`[renderer ${level}] ${message} (${sourceId}:${lineNumber})`);
  });
};
