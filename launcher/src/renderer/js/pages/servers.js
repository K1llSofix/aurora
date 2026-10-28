import { h, fill, append, btn, iconBtn, clear, toast, modal, modalHead, motd } from '../dom.js';
import { icon } from '../icons.js';
import { t, language, formatNumber, errorText } from '../i18n.js';
import { api, state, ping, pingResult, showError } from '../store.js';
import { play } from '../app.js';

function quality(ms) {
  if (ms == null) return 0;
  if (ms < 80) return 4;
  if (ms < 150) return 3;
  if (ms < 300) return 2;
  return 1;
}

function bars(q) {
  return h(`span.bars.q${q}`, h('i'), h('i'), h('i'), h('i'));
}

function row(server, onchange) {
  const favicon = h('div.favicon.lg.empty', icon('server'));
  const motdBox = h('div.motd', h('div.skeleton', { style: { height: '14px', width: '60%', marginTop: '4px' } }));
  const stats = h('div.stats', h('div.spinner', { style: { marginLeft: 'auto' } }));
  const tagline = server.tagline ? (server.tagline[language()] || server.tagline.en) : null;
  const addr = h('span.addr', { 'data-tip': t('servers.copy') }, server.address, icon('copy'));
  addr.addEventListener('click', () => {
    api.app.copy(server.address);
    toast(t('servers.copied'), { icon: 'copy' });
  });
  const el = h('div.card.server-row',
    favicon,
    h('div.info',
      h('div.name', server.name, tagline ? h('span.chip', tagline) : null),
      addr,
      motdBox),
    h('div.tools',
      iconBtn(server.favorite ? 'starFilled' : 'star', {
        class: server.favorite ? 'fav on' : 'fav',
        tip: t(server.favorite ? 'servers.unfavorite' : 'servers.favorite'),
        onclick: async () => {
          try {
            state.servers = await api.servers.save({ id: server.id, name: server.name, address: server.address, favorite: !server.favorite });
            onchange();
          } catch (e) {
            showError(e);
          }
        },
      }),
      iconBtn('edit', { tip: t('servers.edit'), onclick: () => editDialog(server, onchange) }),
      iconBtn('trash', {
        class: 'danger',
        tip: t('servers.remove'),
        onclick: async () => {
          try {
            state.servers = await api.servers.remove(server.id);
            onchange();
            toast(t('servers.removed', { name: server.name }), { icon: 'trash' });
          } catch (e) {
            showError(e);
          }
        },
      })),
    stats,
    btn(t('servers.play'), {
      class: 'primary',
      icon: 'play',
      onclick: () => {
        toast(t('servers.joining', { name: server.name }), { icon: 'play' });
        play({ server: server.address });
      },
    }));
  let faviconEl = favicon;
  let motdEl = motdBox;
  const swap = (next, which) => {
    if (which === 'favicon') {
      faviconEl.replaceWith(next);
      faviconEl = next;
    } else {
      motdEl.replaceWith(next);
      motdEl = next;
    }
  };
  const update = (s) => {
    if (s === undefined) return;
    clear(stats);
    if (s && s.online) {
      if (s.favicon) swap(h('img.favicon.lg', { src: s.favicon, alt: '' }), 'favicon');
      swap(motd(s.motd), 'motd');
      stats.append(
        h('div.players', s.players ? formatNumber(s.players.online) : '—', s.players && s.players.max ? h('small', ` / ${formatNumber(s.players.max)}`) : null),
        h('div.ping', bars(quality(s.latency)), s.latency != null ? t('servers.ms', { n: s.latency }) : ''));
      if (s.version && s.version.name) el.querySelector('.name').title = t('servers.version', { v: s.version.name });
    } else {
      swap(h('div.motd', h('span.dim', t('servers.offline'))), 'motd');
      stats.append(h('div.ping', bars(0), h('span', t('servers.offline'))));
    }
  };
  update(pingResult(server.address));
  ping(server.address).then(update);
  el.refresh = () => {
    fill(stats, h('div.spinner', { style: { marginLeft: 'auto' } }));
    ping(server.address, true).then(update);
  };
  return el;
}

function editDialog(server, onchange) {
  modal((close) => {
    const name = h('input.input', { value: server ? server.name : '', placeholder: t('servers.namePlaceholder'), maxlength: 40 });
    const address = h('input.input.mono', { value: server ? server.address : '', placeholder: 'play.example.com', spellcheck: false });
    const preview = h('div');
    const save = btn(server ? t('common.save') : t('common.add'), { class: 'primary', icon: server ? 'check' : 'plus' });
    let timer = null;
    address.addEventListener('input', () => {
      clearTimeout(timer);
      clear(preview);
      const value = address.value.trim();
      if (!value) return;
      timer = setTimeout(async () => {
        preview.append(h('div.row.dim', h('div.spinner'), t('servers.pinging')));
        const s = await ping(value, true);
        if (address.value.trim() !== value) return;
        fill(preview, s && s.online
          ? h('div.card.server-row', { style: { margin: 0 } },
            s.favicon ? h('img.favicon.lg', { src: s.favicon }) : h('div.favicon.lg.empty', icon('server')),
            h('div.info', motd(s.motd)),
            h('div.stats', h('div.players', s.players ? formatNumber(s.players.online) : '—'), h('div.ping', bars(quality(s.latency)), t('servers.ms', { n: s.latency }))))
          : h('div.note.warn', t('servers.offline')));
      }, 600);
    });
    const submit = async () => {
      save.disabled = true;
      try {
        state.servers = await api.servers.save({ id: server && server.id, name: name.value, address: address.value });
        onchange();
        close();
      } catch (e) {
        save.disabled = false;
        address.classList.add('invalid');
        toast(errorText(e.info), { error: true });
      }
    };
    save.addEventListener('click', submit);
    address.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') submit();
    });
    return [
      modalHead(server ? t('servers.edit') : t('servers.add'), null, close),
      h('div.modal-body',
        h('div.field', h('label', t('servers.name')), name),
        h('div.field', h('label', t('servers.address')), address, h('div.hint', t('servers.addressHint'))),
        preview),
      h('div.modal-foot', btn(t('common.cancel'), { onclick: () => close() }), save),
    ];
  });
}

export function render() {
  const list = h('div');
  const rebuild = () => {
    clear(list);
    if (!state.servers.length) {
      list.append(h('div.card', h('div.empty-state', h('div.ic', icon('server')), h('h3', t('servers.empty')), h('p', t('servers.emptyHint')),
        btn(t('servers.add'), { class: 'primary', icon: 'plus', onclick: () => editDialog(null, rebuild) }))));
      return;
    }
    // Favourites first, the rest in the order the player arranged them.
    const ordered = [...state.servers.filter((s) => s.favorite), ...state.servers.filter((s) => !s.favorite)];
    append(list, ordered.map((s) => row(s, rebuild)));
  };
  api.servers.list().then((s) => {
    state.servers = s;
    rebuild();
  }).catch(showError);
  const refresh = btn(t('common.refresh'), {
    icon: 'refresh',
    onclick: () => {
      for (const r of list.children) r.refresh?.();
    },
  });
  const el = h('div.page',
    h('div.page-head',
      h('div', h('h1', t('servers.title')), h('p', t('servers.subtitle'))),
      h('div.actions', refresh, btn(t('servers.add'), { class: 'primary', icon: 'plus', onclick: () => editDialog(null, rebuild) }))),
    list);
  return { el };
}
