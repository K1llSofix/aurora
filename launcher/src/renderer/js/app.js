import { h, fill, btn, iconBtn, clear, toast, modal, modalHead, popover, menu, forgetFaces } from './dom.js';
import { icon, logo, installIcon, setIconCatalog } from './icons.js';
import { t, tp, setLanguage, resolveLanguage, formatBytes, formatMemory, errorText, formatNumber } from './i18n.js';
import { api, state, set, on, selectedInstall, refreshInstalls, showError } from './store.js';
import { accountCard, addAccountDialog } from './accounts.js';
import { AuroraBackdrop } from './scene.js';
import * as home from './pages/home.js';
import * as installs from './pages/installs.js';
import * as servers from './pages/servers.js';
import * as mods from './pages/mods.js';
import * as settings from './pages/settings.js';
import * as about from './pages/about.js';

const PAGES = { home, installs, servers, mods, settings, about };
const NAV = [
  ['home', 'home'],
  ['installs', 'layers'],
  ['servers', 'server'],
  ['mods', 'puzzle'],
  ['settings', 'sliders'],
  ['about', 'info'],
];

let scene = null;
let current = null;
let shell = null;

// ------------------------------------------------------------------ navigation

export function navigate(page, params) {
  if (!PAGES[page]) page = 'home';
  current?.destroy?.();
  state.page = page;
  document.body.dataset.page = page;
  for (const item of shell.nav.children) item.classList.toggle('active', item.dataset.page === page);
  const view = clear(shell.view);
  current = PAGES[page].render(params || {});
  view.append(current.el);
}

// ------------------------------------------------------------------ play

/** Starts the selected installation (optionally straight onto a server). */
export async function play(opts = {}) {
  if (!state.accounts.length) {
    addAccountDialog();
    return;
  }
  if (state.game.state !== 'idle') return;
  try {
    await api.game.launch({ installId: state.settings.selectedInstall, ...opts });
  } catch (e) {
    showError(e);
  }
}

function playButton() {
  const label = h('span');
  const ic = h('span');
  const bar = h('i.fill');
  const el = h('button.play-btn', { type: 'button' }, bar, ic, label);
  el.addEventListener('click', () => {
    const s = state.game.state;
    if (s === 'idle') play();
    else if (s === 'running') api.game.kill().catch(showError);
  });
  return { el, label, ic, bar };
}

function stageText(p) {
  if (!p) return t('stage.resolve');
  return t(`stage.${p.stage}`);
}

function eta(p) {
  if (!p || !p.speed || !p.totalBytes) return '';
  const left = (p.totalBytes - p.doneBytes) / p.speed;
  if (!Number.isFinite(left) || left <= 0) return '';
  return t('progress.left', { time: left < 60 ? t('time.left.s', { n: Math.ceil(left) }) : t('time.left.m', { n: Math.ceil(left / 60) }) });
}

function buildPlaybar() {
  const pick = h('button.install-pick', { type: 'button' });
  const modsStat = h('button.stat', { type: 'button', onclick: () => navigate('mods') });
  const memStat = h('button.stat.optional', { type: 'button' });
  const status = h('div.status');
  const line = h('div.progress-line', h('i'));
  const play = playButton();
  const consoleBtn = iconBtn('terminal', { tip: t('bar.console'), onclick: () => toggleConsole() });
  const cancel = iconBtn('x', { tip: t('play.cancel'), class: 'hidden', onclick: () => api.game.cancel() });
  const el = h('footer.playbar', line, pick, h('div.sep'), modsStat, memStat, status, consoleBtn, cancel, play.el);

  memStat.addEventListener('click', () => memoryPopover(memStat));
  pick.addEventListener('click', () => {
    menu(pick, [
      { header: true, label: t('nav.installs') },
      ...state.installs.map((i) => ({
        icon: h('div.inst-icon', { style: { width: '28px', height: '28px', borderRadius: '8px' } }, installIcon(i)),
        label: i.name,
        sub: installSubtitle(i),
        checked: i.id === state.settings.selectedInstall,
        onclick: () => selectInstall(i.id),
      })),
      'sep',
      { icon: 'layers', label: t('installs.title'), onclick: () => navigate('installs') },
    ], { above: true, minWidth: 300 });
  });

  const render = () => {
    const inst = selectedInstall();
    clear(pick);
    if (inst) {
      pick.append(h('div.inst-icon', installIcon(inst)), h('div.grow', { style: { minWidth: 0 } }, h('div.name.ellipsis', inst.name), h('div.sub.ellipsis', installSubtitle(inst))), icon('chevronUpDown'));
      pick.lastChild.classList.add('chev');
    }
    fill(modsStat, h('div.ic', icon('puzzle')), h('div', h('div.label', t('bar.mods')), h('div.value', inst ? formatNumber(inst.modCount) : '—')));
    const memory = (inst && inst.memory) || state.settings.memory;
    fill(memStat, h('div.ic', icon('chip')), h('div', h('div.label', t('bar.memory')), h('div.value', formatMemory(memory))));
    renderStatus();
  };

  const renderStatus = () => {
    const g = state.game;
    const p = state.progress;
    const inst = selectedInstall();
    play.el.className = 'play-btn';
    cancel.classList.add('hidden');
    el.classList.remove('busy');
    el.classList.toggle('idle', g.state === 'idle');
    let pct = 0;
    clear(play.ic);
    clear(status);
    if (g.state === 'preparing') {
      el.classList.add('busy');
      play.el.classList.add('busy');
      cancel.classList.remove('hidden');
      if (p && p.stage === 'download' && p.totalBytes) {
        pct = Math.min(100, Math.floor((p.doneBytes / p.totalBytes) * 100));
        play.label.textContent = t('play.installing', { p: pct });
        status.append(
          h('div.line1.ellipsis', stageText(p)),
          h('div.line2.ellipsis', `${formatBytes(p.doneBytes)} / ${formatBytes(p.totalBytes)} · ${formatBytes(p.speed || 0)}${t('common.perSecond')}${eta(p) ? ' · ' + eta(p) : ''}`));
      } else {
        play.label.textContent = t('play.preparing');
        play.ic.append(h('div.spinner'));
        status.append(h('div.line1.ellipsis', stageText(p)), h('div.line2.ellipsis', inst ? inst.name : ''));
      }
    } else if (g.state === 'starting') {
      el.classList.add('busy');
      play.el.classList.add('busy');
      play.label.textContent = t('play.starting');
      play.ic.append(h('div.spinner'));
      pct = 100;
      status.append(h('div.line1', t('stage.starting')), h('div.line2', inst ? inst.name : ''));
    } else if (g.state === 'running') {
      play.el.classList.add('running');
      play.label.textContent = t('play.running');
      play.ic.append(h('span.pulse'));
      play.el.dataset.tip = t('play.stop');
      status.append(h('div.line1', t('stage.running')), h('div.line2', t('stage.runningSub')));
    } else {
      delete play.el.dataset.tip;
      play.label.textContent = state.accounts.length ? t('play.play') : t('play.signIn');
      play.ic.append(icon(state.accounts.length ? 'play' : 'user'));
      if (inst && !inst.installed) status.append(h('div.line1', t('bar.firstRun')), h('div.line2', t('bar.firstRunSub')));
      else status.append(h('div.line1', t('bar.ready')), h('div.line2', inst ? installSubtitle(inst) : ''));
    }
    play.bar.style.width = `${pct}%`;
    line.firstChild.style.width = `${pct}%`;
  };

  return { el, render, renderStatus };
}

export function installSubtitle(inst) {
  const parts = [inst.loader === 'fabric' ? `Fabric ${inst.version}` : `Minecraft ${inst.version}`];
  if (inst.auroraVersion) parts.push(`Aurora ${inst.auroraVersion.split('+')[0]}`);
  return parts.join(' · ');
}

export async function selectInstall(id) {
  try {
    set('settings', await api.installs.select(id));
  } catch (e) {
    showError(e);
  }
}

function memoryPopover(anchor) {
  const total = Math.floor(state.info.totalMemory / 1024 ** 2);
  const max = Math.max(2048, Math.min(32768, Math.floor((total - 1024) / 512) * 512));
  popover(anchor, () => {
    const value = h('div.value');
    const slider = h('input.slider', { type: 'range', min: 1024, max, step: 512 });
    slider.value = state.settings.memory;
    const show = () => {
      value.textContent = formatMemory(Number(slider.value));
    };
    slider.addEventListener('input', show);
    slider.addEventListener('change', async () => {
      try {
        set('settings', await api.settings.set({ memory: Number(slider.value) }));
      } catch (e) {
        showError(e);
      }
    });
    show();
    return h('div.memory', { style: { padding: '14px 14px 10px', width: '300px' } },
      h('div.row', h('div.grow', h('div.label.dim', t('settings.memory'))), value),
      slider,
      h('div.scale', h('span', formatMemory(1024)), h('span', formatMemory(max))),
      h('div.hint.dim', { style: { fontSize: '12px' } }, t('settings.memoryHint', { value: formatMemory(state.info.recommendedMemory), total: formatMemory(Math.round(total / 1024) * 1024) })));
  }, { above: true });
}

// ------------------------------------------------------------------ console

let consoleEl = null;

const LEVEL_FILTER = { all: null, warn: ['WARN', 'ERROR', 'FATAL'], error: ['ERROR', 'FATAL'] };

function logLine(entry) {
  return h(`div.${entry.level || 'INFO'}`,
    entry.time ? h('span.l-time', `[${entry.time}] `) : null,
    entry.thread ? h('span.l-thread', `[${entry.thread}] `) : null,
    entry.message);
}

export function toggleConsole(open) {
  if (consoleEl && open !== true) {
    const el = consoleEl;
    consoleEl = null;
    el.classList.add('closing');
    setTimeout(() => el.remove(), 250);
    return;
  }
  if (consoleEl) return;
  let filter = 'all';
  let follow = true;
  const lines = h('div.lines');
  const redraw = () => {
    clear(lines);
    const wanted = LEVEL_FILTER[filter];
    const list = state.logs.filter((e) => !wanted || wanted.includes(e.level)).slice(-1500);
    if (!list.length) lines.append(h('div.dim', t('console.empty')));
    for (const e of list) lines.append(logLine(e));
    lines.scrollTop = lines.scrollHeight;
  };
  lines.addEventListener('scroll', () => {
    follow = lines.scrollHeight - lines.scrollTop - lines.clientHeight < 40;
  });
  const filters = h('div.segmented',
    ...['all', 'warn', 'error'].map((key) => {
      const b = h('button', { type: 'button', class: key === filter ? 'on' : null }, t(`console.${key}`));
      b.addEventListener('click', () => {
        filter = key;
        for (const other of filters.children) other.classList.toggle('on', other === b);
        redraw();
      });
      return b;
    }));
  consoleEl = h('div.console',
    h('div.bar', icon('terminal'), h('h3', t('console.title')), filters, h('div.grow'),
      iconBtn('copy', { tip: t('crash.copy'), onclick: () => { api.app.copy(state.logs.map((e) => `[${e.time || ''}] [${e.thread}/${e.level}] ${e.message}`).join('\n')); toast(t('crash.copied'), { icon: 'copy' }); } }),
      iconBtn('folder', { tip: t('console.folder'), onclick: () => api.app.openFolder('logs') }),
      iconBtn('x', { tip: t('common.close'), onclick: () => toggleConsole() })),
    lines);
  consoleEl.appendLines = (entries) => {
    const wanted = LEVEL_FILTER[filter];
    if (lines.firstChild && lines.firstChild.classList.contains('dim')) clear(lines);
    for (const e of entries) if (!wanted || wanted.includes(e.level)) lines.append(logLine(e));
    while (lines.childElementCount > 2000) lines.firstChild.remove();
    if (follow) lines.scrollTop = lines.scrollHeight;
  };
  shell.view.append(consoleEl);
  redraw();
}

function crashDialog(exit) {
  modal((close) => {
    const tail = h('div.console', { style: { position: 'static', height: '260px', margin: 0, borderRadius: '14px', border: '1px solid rgba(255,255,255,.1)', animation: 'none' } },
      h('div.lines', exit.tail.map(logLine)));
    requestAnimationFrame(() => {
      const l = tail.firstChild;
      l.scrollTop = l.scrollHeight;
    });
    return [
      modalHead(t('crash.title'), t('crash.text', { code: exit.code }), close),
      h('div.modal-body', tail),
      h('div.modal-foot',
        exit.report ? btn(t('crash.report'), { icon: 'folder', onclick: () => api.app.openFolder('crash') }) : null,
        btn(t('crash.copy'), { icon: 'copy', onclick: () => { api.app.copy(exit.tail.map((e) => e.message).join('\n')); toast(t('crash.copied'), { icon: 'copy' }); } }),
        h('div.grow'),
        btn(t('crash.console'), { icon: 'terminal', onclick: () => { close(); toggleConsole(true); } }),
        btn(t('common.close'), { class: 'primary', onclick: () => close() })),
    ];
  }, { wide: true });
}

// ------------------------------------------------------------------ shell

function buildShell() {
  const nav = h('nav.nav', NAV.map(([page, ic]) => h('button.nav-item', { type: 'button', dataset: { page }, onclick: () => navigate(page) }, icon(ic), h('span', t(`nav.${page}`)))));
  const account = accountCard();
  const maxIcon = h('span', icon(state.settings.window && state.settings.window.maximized ? 'restore' : 'maximize'));
  const sidebar = h('aside.sidebar',
    h('div.brand', logo(), h('div', h('div.name', 'Aurora'), h('div.sub', 'MINECRAFT LAUNCHER'))),
    nav,
    h('div.spacer'),
    account.el);
  const view = h('section', { id: 'view' });
  const playbar = buildPlaybar();
  const titlebar = h('header.titlebar',
    h('div.win-controls',
      h('button.win-btn', { type: 'button', onclick: () => api.window.minimize(), 'aria-label': 'Minimize' }, icon('minimize')),
      h('button.win-btn', { type: 'button', onclick: () => api.window.toggleMaximize(), 'aria-label': 'Maximize' }, maxIcon),
      h('button.win-btn.close', { type: 'button', onclick: () => api.window.close(), 'aria-label': 'Close' }, icon('x'))));
  const main = h('main.main', titlebar, view, playbar.el);
  const app = h('div.app', sidebar, main);
  return { app, nav, view, account, playbar, maxIcon };
}

function mountShell() {
  const root = document.getElementById('root');
  const page = state.page;
  current?.destroy?.();
  current = null;
  consoleEl = null;
  shell = buildShell();
  fill(root, shell.app);
  shell.playbar.render();
  navigate(page);
}

function applySettings(next, previous) {
  const lang = resolveLanguage(next.language, state.info.locale);
  scene?.setMotion(next.motion);
  if (!previous || resolveLanguage(previous.language, state.info.locale) !== lang) {
    setLanguage(lang);
    if (shell) mountShell();
  }
}

async function boot() {
  const [info, settingsValue, installList, accountList, game, logs, catalog] = await Promise.all([
    api.app.info(),
    api.settings.get(),
    api.installs.list(),
    api.accounts.list(),
    api.game.state(),
    api.game.logs(),
    api.icons.catalog().catch(() => null),
  ]);
  setIconCatalog(catalog);
  state.info = info;
  state.settings = settingsValue;
  state.installs = installList;
  state.accounts = accountList;
  state.game = game;
  state.logs = logs;
  setLanguage(resolveLanguage(settingsValue.language, info.locale));
  scene = new AuroraBackdrop(document.getElementById('scene'));
  if (info.dev) window.__scene = scene;
  scene.setMotion(settingsValue.motion);
  scene.start();
  mountShell();

  let previousSettings = settingsValue;
  on('settings', (s) => {
    shell.playbar.render();
    applySettings(s, previousSettings);
    previousSettings = s;
  });
  on('installs', () => shell.playbar.render());
  on('accounts', () => {
    shell.account.render();
    shell.playbar.renderStatus();
  });
  on('game', () => shell.playbar.renderStatus());
  on('progress', () => shell.playbar.renderStatus());

  api.settings.onChanged((s) => set('settings', s));
  api.accounts.onChanged((list) => set('accounts', list));
  api.window.onState(({ maximized }) => {
    fill(shell.maxIcon, icon(maximized ? 'restore' : 'maximize'));
  });
  api.game.onProgress((p) => set('progress', p));
  api.game.onLog((entries) => {
    state.logs.push(...entries);
    if (state.logs.length > 4000) state.logs.splice(0, state.logs.length - 4000);
    consoleEl?.appendLines(entries);
  });
  api.game.onState((g) => {
    const previous = state.game.state;
    if (g.state === 'preparing' && previous === 'idle') {
      state.logs = [];
      set('progress', null);
    }
    set('game', g);
    if (g.state === 'idle') {
      set('progress', null);
      // The first install brings the game's textures for icons and default skins.
      api.icons.catalog().then((list) => {
        setIconCatalog(list);
        return refreshInstalls();
      }).then(() => forgetFaces()).catch(() => {});
      if (g.error) {
        if (g.error.code === 'cancelled') toast(errorText(g.error), { icon: 'x' });
        else showError({ info: g.error });
      }
      if (g.exit && g.exit.crashed) crashDialog(g.exit);
      if (g.error || (g.exit && g.exit.crashed)) api.game.ackExit().catch(() => {});
    }
  });
  // The window may open after the fact: a shortcut start that crashed or failed.
  if (game.state === 'idle' && game.exit && game.exit.crashed && !game.exit.seen) crashDialog(game.exit);
  else if (game.state === 'idle' && game.error) showError({ info: game.error });
  if (game.error || (game.exit && !game.exit.seen)) api.game.ackExit().catch(() => {});
  api.app.onEvent((event) => {
    if (event && event.type === 'needAccount') addAccountDialog();
    // A newer Aurora found on Modrinth: the installations show its version.
    if (event && event.type === 'installsChanged') refreshInstalls().catch(() => {});
  });

  // Mods dropped anywhere on the window go to the selected installation.
  window.addEventListener('dragover', (e) => e.preventDefault());
  window.addEventListener('drop', (e) => e.preventDefault());
}

boot().catch((e) => {
  console.error(e);
  document.getElementById('root').append(h('div.err-box', { style: { margin: '60px' } }, String(e && e.message)));
});
