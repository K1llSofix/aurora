// The small card a game shortcut shows while it gets the installation ready. The launcher closes
// it once the game window is up; if something fails it explains why and offers the launcher.

import { h, fill, btn } from './dom.js';
import { logo, installIcon, setIconCatalog } from './icons.js';
import { t, setLanguage, resolveLanguage, formatBytes, errorText } from './i18n.js';
import { api } from './store.js';

const installId = new URLSearchParams(location.search).get('install');

const stage = h('div.stage', t('stage.resolve'));
const bar = h('div.bar.indeterminate', h('i'));
const meta = h('div.meta');
const body = h('div.col', { style: { gap: '10px' } }, bar, meta);
const card = h('div.splash-card');

function progress(p) {
  if (!p) return;
  stage.textContent = t(`stage.${p.stage}`);
  if (p.stage === 'download' && p.totalBytes) {
    const pct = Math.min(100, Math.floor((p.doneBytes / p.totalBytes) * 100));
    bar.classList.remove('indeterminate');
    bar.firstChild.style.width = `${pct}%`;
    fill(meta, h('span', `${formatBytes(p.doneBytes)} / ${formatBytes(p.totalBytes)}`), h('span', `${pct}%`));
  } else {
    bar.classList.add('indeterminate');
    fill(meta);
  }
}

function failure(error) {
  stage.textContent = t('splash.failed');
  fill(body,
    h('div.err', errorText(error)),
    h('div.actions',
      btn(t('common.close'), { class: 'sm', onclick: () => api.app.quit() }),
      btn(t('splash.openLauncher'), { class: 'sm primary', onclick: () => api.app.showLauncher() })));
}

async function boot() {
  const [info, settings, installs, catalog, game] = await Promise.all([
    api.app.info(),
    api.settings.get(),
    api.installs.list(),
    api.icons.catalog().catch(() => null),
    api.game.state(),
  ]);
  setLanguage(resolveLanguage(settings.language, info.locale));
  setIconCatalog(catalog);
  const inst = installs.find((i) => i.id === installId) || installs[0];
  stage.textContent = t('stage.resolve');
  card.append(
    h('div.top',
      h('div.inst-icon', installIcon(inst)),
      h('div.grow', { style: { minWidth: 0 } }, h('div.name.ellipsis', inst.name), stage),
      h('div.brand', logo(), 'AURORA')),
    body);
  document.body.append(card);
  api.game.onProgress(progress);
  api.game.onState((s) => {
    if (s.state === 'starting') {
      stage.textContent = t('stage.starting');
      bar.classList.add('indeterminate');
      fill(meta);
    }
    if (s.state === 'idle' && s.error) failure(s.error);
  });
  if (game.state === 'idle' && game.error) failure(game.error);
  else if (game.state === 'starting') stage.textContent = t('stage.starting');
}

boot().catch((e) => {
  document.body.append(h('div.splash-card', h('div.err', String(e && e.message))));
});
