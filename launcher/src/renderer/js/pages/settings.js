import { h, fill, btn, clear, toast, toggle, segmented, menu } from '../dom.js';
import { icon } from '../icons.js';
import { t, formatMemory, errorText } from '../i18n.js';
import { api, state, set, on, selectedInstall, showError } from '../store.js';
import { clientIdDialog } from '../accounts.js';

async function save(patch, quiet = false) {
  try {
    set('settings', await api.settings.set(patch));
    if (!quiet) toast(t('settings.saved'), { icon: 'check', duration: 1600 });
  } catch (e) {
    showError(e);
  }
}

function setRow(title, description, control) {
  return h('div.set-row', h('div.text', h('div.t', title), description ? h('div.d', description) : null), h('div.control', control));
}

function selectRow(options, value, onpick) {
  const el = h('button.select', { type: 'button' });
  const render = (v) => fill(el, h('span', options.find(([k]) => k === v)[1]), icon('chevronDown'));
  render(value);
  el.addEventListener('click', () => menu(el, options.map(([key, label]) => ({
    label,
    checked: key === value,
    onclick: () => {
      value = key;
      render(key);
      onpick(key);
    },
  })), { minWidth: 220 }));
  return el;
}

function memoryCard() {
  const s = state.settings;
  const info = state.info;
  const total = Math.floor(info.totalMemory / 1024 ** 2);
  const max = Math.max(2048, Math.min(32768, Math.floor((total - 1024) / 512) * 512));
  const value = h('div.value');
  const slider = h('input.slider', { type: 'range', min: 1024, max, step: 512 });
  slider.value = s.memory;
  const show = () => {
    const v = Number(slider.value);
    fill(value, formatMemory(v), v === info.recommendedMemory ? h('small', '✓') : null);
    const pct = ((v - 1024) / (max - 1024)) * 100;
    slider.style.background = `linear-gradient(90deg, #2dd4bf, #4fa3f7 ${pct / 2}%, #9b6cff ${pct}%, rgba(255,255,255,.1) ${pct}%)`;
  };
  slider.addEventListener('input', show);
  slider.addEventListener('change', () => save({ memory: Number(slider.value) }));
  show();
  return h('div.set-row', h('div.memory',
    h('div.row', h('div.text.grow', h('div.t', t('settings.memory')), h('div.d', t('settings.memoryHint', { value: formatMemory(info.recommendedMemory), total: formatMemory(Math.round(total / 1024) * 1024) }))), value),
    slider,
    h('div.scale', h('span', formatMemory(1024)), h('span', formatMemory(Math.round(max / 2 / 512) * 512)), h('span', formatMemory(max)))));
}

function resolutionControl() {
  const res = state.settings.resolution;
  const w = h('input.input', { type: 'number', min: 320, placeholder: '1280', value: res ? res.width : '', style: { width: '100px' } });
  const hh = h('input.input', { type: 'number', min: 240, placeholder: '720', value: res ? res.height : '', style: { width: '100px' } });
  const commit = () => {
    const width = parseInt(w.value, 10);
    const height = parseInt(hh.value, 10);
    if (!w.value && !hh.value) save({ resolution: null });
    else if (width >= 320 && height >= 240) save({ resolution: { width, height } });
  };
  w.addEventListener('change', commit);
  hh.addEventListener('change', commit);
  return h('div.row', w, h('span.dim', '×'), hh);
}

function javaCard() {
  const s = state.settings;
  const custom = h('div.col', { style: { width: '100%' } });
  const status = h('div.d');
  const probe = async (p) => {
    clear(status);
    if (!p) return;
    status.append(h('span.row', h('div.spinner', { style: { width: '12px', height: '12px', borderWidth: '2px' } })));
    const info = await api.java.probe(p).catch(() => null);
    fill(status, info
      ? h('span', { style: { color: 'var(--ok)' } }, `✓ ${t('settings.javaFound', { version: info.version })}${info.vendor ? ' · ' + info.vendor : ''}`)
      : h('span', { style: { color: 'var(--bad)' } }, t('settings.javaBad')));
  };
  const renderCustom = (mode) => {
    clear(custom);
    if (mode !== 'custom') return;
    const path = h('input.input.mono', { value: state.settings.javaPath || '', placeholder: 'C:\\Program Files\\Java\\bin\\javaw.exe' });
    path.addEventListener('change', () => {
      save({ javaPath: path.value.trim() });
      probe(path.value.trim());
    });
    custom.append(h('div.row', path, btn(t('settings.javaPick'), {
      icon: 'folder',
      onclick: async () => {
        const picked = await api.java.pick().catch(() => null);
        if (!picked) return;
        path.value = picked.path;
        save({ javaPath: picked.path });
        probe(picked.path);
      },
    })), status);
    probe(state.settings.javaPath);
  };
  renderCustom(s.javaMode);
  return h('div.card.set-card',
    h('h3', icon('coffee'), t('settings.java')),
    setRow(t('settings.javaMode'), t('settings.javaAutoHint'), segmented([['auto', t('settings.javaAuto')], ['custom', t('settings.javaCustom')]], s.javaMode, (mode) => {
      save({ javaMode: mode });
      renderCustom(mode);
    })),
    custom);
}

/** The launcher's version and its update: checked, being downloaded, or ready to install. */
function updateRow() {
  const text = h('div.d');
  const control = h('div.control');
  const el = h('div.set-row', h('div.text', h('div.t', t('settings.version', { version: state.info.version })), text), control);
  const render = () => {
    const u = state.update || { status: 'off' };
    const lines = {
      off: t('update.off'),
      idle: t('update.idle'),
      checking: t('update.checking'),
      none: t('update.none'),
      downloading: t('update.downloading', { version: u.version, p: Math.round((u.progress || 0) * 100) }),
      ready: t('update.readyLong', { version: u.version }),
      error: `${t('update.error')}. ${u.error ? errorText(u.error) : ''}`.trim(),
    };
    text.textContent = lines[u.status] || '';
    if (u.status === 'ready') {
      fill(control, btn(t('update.restartButton'), { class: 'primary', icon: 'refresh', onclick: () => api.updates.install().catch(showError) }));
    } else {
      const busy = ['off', 'checking', 'downloading'].includes(u.status);
      fill(control, btn(t('update.check'), { icon: 'refresh', disabled: busy, onclick: () => api.updates.check().catch(showError) }));
    }
  };
  render();
  return { el, render };
}

function microsoftCard() {
  const field = h('input.input.mono', { placeholder: '00000000-0000-0000-0000-000000000000', spellcheck: false, style: { width: '330px' } });
  api.accounts.clientId().then((id) => { field.value = id || ''; }).catch(() => {});
  field.addEventListener('change', async () => {
    try {
      await api.accounts.setClientId(field.value.trim());
      field.classList.remove('invalid');
      toast(t('settings.clientIdSaved'), { icon: 'key' });
    } catch (e) {
      field.classList.add('invalid');
      toast(errorText(e.info), { error: true });
    }
  });
  return h('div.card.set-card',
    h('h3', icon('microsoft'), t('settings.microsoft')),
    setRow(t('settings.clientId'), t('settings.clientIdHint'), h('div.row', field, btn('', { icon: 'info', class: 'icon', 'data-tip': t('accounts.clientTitle'), onclick: () => clientIdDialog() }))));
}

export function render() {
  const s = state.settings;
  const jvm = h('input.input.mono', { value: s.jvmArgs || '', placeholder: '-XX:+UseZGC', style: { width: '330px' } });
  jvm.addEventListener('change', () => save({ jvmArgs: jvm.value.trim() }));
  const updates = updateRow();
  const el = h('div.page',
    h('div.page-head', h('div', h('h1', t('settings.title')), h('p', t('settings.subtitle')))),
    h('div.settings',
      h('div.card.set-card',
        h('h3', icon('grass'), t('settings.game')),
        memoryCard(),
        setRow(t('settings.resolution'), t('settings.resolutionHint'), resolutionControl()),
        setRow(t('settings.jvm'), t('settings.jvmHint'), jvm)),
      javaCard(),
      h('div.card.set-card',
        h('h3', icon('sparkle'), t('settings.launcher')),
        setRow(t('settings.after'), t('settings.afterHint'), selectRow(
          ['hide', 'minimize', 'keep', 'close'].map((k) => [k, t(`settings.after.${k}`)]), s.afterLaunch, (v) => save({ afterLaunch: v }))),
        setRow(t('settings.language'), null, selectRow([['auto', t('settings.lang.auto')], ['ru', 'Русский'], ['en', 'English']], s.language, (v) => save({ language: v }, true))),
        setRow(t('settings.motion'), t('settings.motionHint'), toggle(s.motion, (on) => save({ motion: on }, true))),
        setRow(t('settings.discord'), t('settings.discordHint'), toggle(s.discord !== false, (on) => save({ discord: on }))),
        setRow(t('settings.autoUpdate'), t('settings.autoUpdateHint'), toggle(s.autoUpdate !== false, (on) => save({ autoUpdate: on }))),
        updates.el),
      microsoftCard(),
      h('div.card.set-card',
        h('h3', icon('folder'), t('settings.files')),
        setRow(t('settings.root'), state.info.root, btn(t('settings.open'), { icon: 'folder', onclick: () => api.app.openFolder('root') })),
        setRow(t('settings.logs'), null, btn(t('settings.open'), { icon: 'terminal', onclick: () => api.app.openFolder('launcherLogs') })),
        setRow(t('settings.verify'), t('settings.verifyHint'), btn(t('settings.run'), {
          icon: 'shield',
          onclick: () => api.game.repair(selectedInstall().id).catch(showError),
        })))));
  return { el, destroy: on('update', updates.render) };
}
