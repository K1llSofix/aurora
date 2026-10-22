import { h, fill, btn, clear, motdText } from '../dom.js';
import { icon, logo } from '../icons.js';
import { t, language, formatNumber, formatDate } from '../i18n.js';
import { api, state, on, selectedInstall, ping, pingResult } from '../store.js';
import { navigate, play } from '../app.js';

const ROTATE_MS = 8000;

function localized(value) {
  if (value && typeof value === 'object') return value[language()] || value.en || '';
  return value || '';
}

function newsCard() {
  const el = h('div.card.news');
  let items = state.news || [];
  let index = 0;
  let timer = null;
  let paused = false;

  const show = (i) => {
    if (!items.length) return;
    index = (i + items.length) % items.length;
    const item = items[index];
    const media = h('div.media',
      item.source === 'aurora'
        ? h('div.aurora-art', logo())
        : h('img', { src: item.image, alt: '', loading: 'lazy', onerror: (e) => { e.target.style.opacity = 0; } }),
      h(`span.chip.tag.${item.source === 'aurora' ? 'accent' : 'violet'}`, item.source === 'aurora' ? 'Aurora' : 'Minecraft'));
    const dots = h('div.dots', items.map((_, k) => h('button', { type: 'button', class: k === index ? 'on' : null, onclick: () => { show(k); restart(); } })));
    const more = h('button.link', { type: 'button' }, t('home.more'), icon('arrowRight'));
    more.addEventListener('click', () => {
      if (item.action) navigate(item.action);
      else if (item.link) api.app.openExternal(item.link);
    });
    fill(el, h('div.slide', media, h('div.body',
      h('div.label', `${t('home.news')} · ${formatDate(item.date)}`),
      h('h4', localized(item.title)),
      h('p', localized(item.text)),
      h('div.foot', more, dots))));
  };
  const restart = () => {
    clearInterval(timer);
    timer = setInterval(() => {
      if (!paused && document.hasFocus()) show(index + 1);
    }, ROTATE_MS);
  };
  el.addEventListener('mouseenter', () => { paused = true; });
  el.addEventListener('mouseleave', () => { paused = false; });

  if (items.length) {
    show(0);
  } else {
    el.append(h('div.media.skeleton', { style: { borderRadius: '20px 20px 0 0' } }), h('div.body', h('div.skeleton', { style: { height: '14px', width: '40%' } }), h('div.skeleton', { style: { height: '18px', margin: '12px 0', width: '80%' } }), h('div.skeleton', { style: { height: '34px' } })));
    api.news.get().then((list) => {
      state.news = list;
      items = list;
      show(0);
    }).catch(() => {
      clear(el);
    });
  }
  restart();
  return { el, destroy: () => clearInterval(timer) };
}

function serverRow(server) {
  const icon_ = h('div.favicon.empty', icon('server'));
  const desc = h('div.desc.ellipsis', localized(server.tagline) || server.address);
  const online = h('div.online', h('div.spinner', { style: { width: '12px', height: '12px', borderWidth: '2px' } }));
  const row = h('div.server-mini', icon_, h('div.info', h('div.name.ellipsis', server.name), desc), online,
    h('button.btn.sm.primary.play', { type: 'button', onclick: () => play({ server: server.address }) }, icon('play'), h('span', t('servers.play'))));
  const update = (s) => {
    if (s === undefined) return;
    clear(online);
    if (s && s.online) {
      if (s.favicon) {
        const img = h('img.favicon', { src: s.favicon, alt: '' });
        icon_.replaceWith(img);
      }
      if (!server.tagline && s.motd) desc.textContent = motdText(s.motd).split('  ')[0] || server.address;
      online.append(h('span.dot.ok'), s.players ? formatNumber(s.players.online) : '—');
    } else {
      online.append(h('span.dot.bad'), h('span.dim', t('servers.offline')));
    }
  };
  update(pingResult(server.address));
  ping(server.address).then(update);
  return row;
}

function serversCard() {
  const list = h('div.list');
  const el = h('div.card.servers-card',
    h('div.card-head', h('h3', t('home.servers')), h('button.link', { type: 'button', onclick: () => navigate('servers') }, t('home.allServers'), icon('arrowRight'))),
    list);
  const rows = () => {
    fill(list, state.servers.slice(0, 3).map(serverRow));
  };
  if (state.servers.length) rows();
  else api.servers.list().then((s) => {
    state.servers = s;
    rows();
  });
  return el;
}

export function render() {
  const news = newsCard();
  const meta = h('div.meta');
  const fillMeta = () => {
    const i = selectedInstall();
    clear(meta);
    if (!i) return;
    meta.append(h('span.chip', icon('grass'), `Minecraft ${i.version}`));
    if (i.loader === 'fabric') meta.append(h('span.chip', icon('puzzle'), 'Fabric'));
    if (i.auroraVersion) meta.append(h('span.chip.accent', icon('sparkle'), `Aurora ${i.auroraVersion.split('+')[0]}`));
  };
  fillMeta();
  const off = on('installs', fillMeta);
  const offSettings = on('settings', fillMeta);
  const el = h('div.page', h('div.home',
    h('section.hero',
      h('div.over', t('home.over')),
      h('div.title', 'Aurora'),
      h('div.tagline', t('home.tagline')),
      h('div.cta',
        btn(t('home.play'), { class: 'primary lg', icon: 'play', onclick: () => play() }),
        btn(t('home.customize'), { class: 'lg', icon: 'sliders', onclick: () => navigate('settings') })),
      meta),
    h('aside.side', news.el, serversCard())));
  return {
    el,
    destroy: () => {
      news.destroy();
      off();
      offSettings();
    },
  };
}
