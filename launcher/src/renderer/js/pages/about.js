import { h, btn } from '../dom.js';
import { icon, logo } from '../icons.js';
import { t, formatMemory } from '../i18n.js';
import { api, state } from '../store.js';
import { whatsNew } from '../app.js';

const LINKS = [
  ['Fabric', 'https://fabricmc.net/'],
  ['Modrinth', 'https://modrinth.com/'],
  ['Minecraft', 'https://www.minecraft.net/'],
];

export function render() {
  const info = state.info;
  const os = info.platform === 'win32' ? `Windows ${info.osRelease}` : `${info.platform} ${info.osRelease}`;
  const el = h('div.page',
    h('div.page-head', h('div', h('h1', t('about.title')))),
    h('div.card.about-hero',
      logo(),
      h('div.grow',
        h('h2', 'Aurora Launcher'),
        h('div.row', { style: { margin: '6px 0' } },
          h('span.chip.accent', `v${info.version}`),
          info.aurora ? h('span.chip.violet', icon('sparkle'), `Aurora ${info.aurora.version.split('+')[0]} · Minecraft ${info.aurora.minecraft}`) : null),
        h('p', t('about.tagline'))),
      btn(t('whatsnew.button'), { icon: 'sparkle', onclick: () => whatsNew(true) })),
    h('div.about-grid',
      h('div.card',
        h('h3', t('about.features')),
        h('ul.features', ['f1', 'f2', 'f3', 'f4', 'f5', 'f6'].map((k) => h('li', icon('check'), h('span', t(`about.${k}`)))))),
      h('div.col', { style: { gap: '18px' } },
        h('div.card',
          h('h3', t('about.system')),
          h('dl.kv',
            h('dt', t('about.version')), h('dd', info.version + (info.dev ? ' (dev)' : '')),
            h('dt', t('about.mod')), h('dd', info.aurora ? info.aurora.version : '—'),
            h('dt', t('about.os')), h('dd', `${os} · ${info.arch}`),
            h('dt', t('about.memory')), h('dd', `${formatMemory(Math.round(info.totalMemory / 1024 ** 3) * 1024)} · ${info.cpus} CPU`),
            h('dt', t('about.engine')), h('dd', `Electron ${info.electron} · Chromium ${info.chrome.split('.')[0]}`),
            h('dt', t('about.folder')), h('dd', { title: info.root }, info.root))),
        h('div.card',
          h('h3', t('about.links')),
          h('div.row', { style: { flexWrap: 'wrap' } }, LINKS.map(([label, url]) => btn(label, { class: 'sm', icon: 'external', onclick: () => api.app.openExternal(url) })))))),
    h('p.dim', { style: { fontSize: '11.5px', marginTop: '22px', letterSpacing: '0.02em' } }, t('about.legal')));
  return { el };
}
