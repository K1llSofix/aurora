import { h, fill, append, btn, iconBtn, clear, toast, toggle, menu, motd, motdText } from '../dom.js';
import { icon, installIcon } from '../icons.js';
import { t, compactNumber, timeAgo, formatBytes } from '../i18n.js';
import { api, state, on, selectedInstall, refreshInstalls, showError } from '../store.js';
import { navigate, selectInstall } from '../app.js';
import { installModpack } from '../modpack-install.js';

const SORTS = ['relevance', 'downloads', 'follows', 'updated', 'newest'];

/**
 * The kinds of content the page manages. Mods are jars in mods/; resource packs and shader packs
 * are zips that are switched on through options.txt and Iris respectively. Modpacks only have a
 * catalog: each one is installed as an installation of its own.
 */
const KINDS = {
  mod: {
    icon: 'puzzle',
    files: /\.jar$/i,
    folder: 'mods',
    list: (id) => api.mods.list(id),
    remove: (id, file) => api.mods.remove(id, file),
    pick: (id) => api.mods.pick(id),
    addPaths: (id, paths) => api.mods.addPaths(id, paths),
    search: (query) => api.mods.search(query),
    install: (id, project) => api.mods.install(id, project),
    installed: (id) => api.mods.installed(id),
  },
  resourcepack: {
    icon: 'image',
    files: /\.zip$/i,
    folder: 'resourcepacks',
    list: async (id) => (await api.packs.list(id, 'resourcepack')).list,
    remove: (id, file) => api.packs.remove(id, 'resourcepack', file),
    pick: (id) => api.packs.pick(id, 'resourcepack'),
    addPaths: (id, paths) => api.packs.addPaths(id, 'resourcepack', paths),
    search: (query) => api.packs.search('resourcepack', query),
    install: (id, project) => api.packs.install(id, 'resourcepack', project),
    installed: (id) => api.packs.installed(id, 'resourcepack'),
  },
  shader: {
    icon: 'sun',
    files: /\.zip$/i,
    folder: 'shaderpacks',
    list: async (id) => (await api.packs.list(id, 'shader')).list,
    remove: (id, file) => api.packs.remove(id, 'shader', file),
    pick: (id) => api.packs.pick(id, 'shader'),
    addPaths: (id, paths) => api.packs.addPaths(id, 'shader', paths),
    search: (query) => api.packs.search('shader', query),
    install: (id, project) => api.packs.install(id, 'shader', project),
    installed: (id) => api.packs.installed(id, 'shader'),
  },
  modpack: {
    icon: 'box',
    catalogOnly: true,
    search: (query) => api.modpacks.search(query),
    installed: async () => state.installs.filter((i) => i.modpack && i.modpack.project).map((i) => i.modpack.project),
  },
};

function itemIcon(src, kind, size = '') {
  const cls = `mod-icon${size ? '.' + size : ''}`;
  if (!src) return h(`div.${cls}`, icon(KINDS[kind].icon));
  const img = h(`img.${cls}`, { src, alt: '', loading: 'lazy' });
  img.addEventListener('error', () => img.replaceWith(h(`div.${cls}`, icon(KINDS[kind].icon))));
  return img;
}

// ------------------------------------------------------------------ installed

function installedTab(inst, kind) {
  const k = KINDS[kind];
  const filter = h('input.input', { placeholder: t(`content.${kind}.filter`) });
  const list = h('div.card.list-card');
  const banner = h('div');
  const updatesBanner = h('div');
  const wrap = h('div', { style: { position: 'relative' } });
  let items = [];
  // Newer versions found on Modrinth, by file name.
  let updates = new Map();
  let updating = false;

  const applyUpdates = async (list) => {
    if (updating || !list.length) return;
    updating = true;
    renderUpdates();
    redraw();
    try {
      const done = await api.content.update(inst.id, kind, list);
      for (const u of list) updates.delete(u.file);
      toast(t('updates.done', { n: done.length }), { icon: 'download' });
    } catch (e) {
      showError(e);
    }
    updating = false;
    await load();
    renderUpdates();
  };

  const renderUpdates = () => {
    clear(updatesBanner);
    if (!updates.size) return;
    const all = btn(t('updates.all'), { class: 'primary sm', icon: 'download', disabled: updating, onclick: () => applyUpdates([...updates.values()]) });
    updatesBanner.append(h('div.note.banner', { style: { marginBottom: '14px' } },
      icon('download'),
      h('div.grow', h('b', t('updates.found', { n: updates.size })), h('div', t(`updates.hint.${kind}`))),
      updating ? h('div.spinner') : all));
  };

  const checkUpdates = async (button) => {
    button.disabled = true;
    try {
      const list = await api.content.check(inst.id, kind);
      updates = new Map(list.map((u) => [u.file, u]));
      if (!list.length) toast(t('updates.none'), { icon: 'check' });
    } catch (e) {
      showError(e);
    }
    button.disabled = false;
    renderUpdates();
    redraw();
  };

  const toggleFor = (item) => {
    if (kind === 'mod') {
      if (item.managed) return h('span.icon-btn', { 'data-tip': t('mods.managed') }, icon('shield'));
      return toggle(item.enabled, async (enabled) => {
        try {
          await api.mods.setEnabled(inst.id, item.file, enabled);
          toast(t(enabled ? 'mods.enabled' : 'mods.disabled', { name: item.name }), { icon: enabled ? 'check' : 'x' });
        } catch (e) {
          showError(e);
        }
        await load();
      });
    }
    return toggle(item.enabled, async (enabled) => {
      try {
        await api.packs.setEnabled(inst.id, kind, item.file, enabled);
        toast(t(enabled ? `content.${kind}.on` : `content.${kind}.off`, { name: item.name }), { icon: enabled ? 'check' : 'x' });
      } catch (e) {
        showError(e);
      }
      await load();
    });
  };

  const rowFor = (item) => {
    const managed = kind === 'mod' && item.managed;
    const badges = [];
    if (item.version) badges.push(h('span.ver', item.version));
    if (managed) badges.push(h('span.chip.accent', { style: { height: '20px', fontSize: '11px' } }, icon('shield'), t('mods.managed')));
    if (kind === 'shader' && item.enabled) badges.push(h('span.chip.accent', { style: { height: '20px', fontSize: '11px' } }, icon('sun'), t('content.shader.active')));
    if (kind === 'resourcepack' && item.folder) badges.push(h('span.badge', t('content.folder')));
    let desc;
    if (kind === 'mod') {
      desc = item.broken ? t('mods.broken') : item.loader === 'forge' ? t('mods.forge') : item.description || (item.authors && item.authors.join(', ')) || item.jar;
    } else if (kind === 'resourcepack') {
      desc = item.description ? motd(item.description, { singleLine: true, color: '#A9B0C8' }) : item.size ? formatBytes(item.size) : '';
    } else {
      desc = item.size ? formatBytes(item.size) : t('content.folder');
    }
    const update = updates.get(item.file);
    if (update) badges.push(h('span.chip.accent', { style: { height: '20px', fontSize: '11px' } }, icon('download'), update.to));
    return h(`div.mod-row${item.enabled ? '' : '.off'}`,
      itemIcon(item.icon, kind),
      h('div.grow', { style: { minWidth: 0 } }, h('div.title', h('span.ellipsis', item.name), badges), h('div.desc.ellipsis', desc)),
      update ? iconBtn('download', { tip: t('updates.one', { version: update.to }), disabled: updating, onclick: () => applyUpdates([update]) }) : null,
      toggleFor(item),
      managed ? h('div', { style: { width: '34px' } }) : iconBtn('trash', {
        class: 'danger',
        tip: t('common.delete'),
        onclick: async () => {
          try {
            await k.remove(inst.id, item.file);
            toast(t('mods.removed', { name: item.name }), { icon: 'trash' });
            await load();
          } catch (e) {
            showError(e);
          }
        },
      }));
  };

  const redraw = () => {
    const q = filter.value.trim().toLowerCase();
    const shown = items.filter((m) => !q || `${m.name} ${m.id || ''} ${typeof m.description === 'string' ? m.description : motdText(m.description)}`.toLowerCase().includes(q));
    clear(list);
    if (!items.length) {
      list.append(h('div.empty-state', h('div.ic', icon(k.icon)), h('h3', t(`content.${kind}.empty`)), h('p', t(`content.${kind}.emptyHint`))));
      return;
    }
    append(list, shown.map(rowFor));
  };
  const load = async () => {
    try {
      items = await k.list(inst.id);
      redraw();
      if (kind === 'mod') refreshInstalls().catch(() => {});
      if (kind === 'shader') {
        const { iris } = await api.packs.list(inst.id, 'shader');
        fill(banner, iris ? null : irisBanner(inst, load));
      }
    } catch (e) {
      showError(e);
    }
  };
  filter.addEventListener('input', redraw);

  // Dropping files from Explorer copies them into this installation.
  let depth = 0;
  let zone = null;
  const hasFiles = (e) => Array.from(e.dataTransfer?.types || []).includes('Files');
  wrap.addEventListener('dragenter', (e) => {
    if (!hasFiles(e)) return;
    depth++;
    if (!zone) {
      zone = h('div.drop-zone', t(`content.${kind}.drop`));
      wrap.append(zone);
    }
  });
  wrap.addEventListener('dragleave', () => {
    depth = Math.max(0, depth - 1);
    if (!depth) {
      zone?.remove();
      zone = null;
    }
  });
  wrap.addEventListener('drop', async (e) => {
    depth = 0;
    zone?.remove();
    zone = null;
    const paths = Array.from(e.dataTransfer.files).map((f) => window.aurora.files.pathOf(f)).filter((p) => k.files.test(p));
    if (!paths.length) return;
    try {
      const added = await k.addPaths(inst.id, paths);
      toast(t('mods.added', { n: added.length }), { icon: 'plus' });
      await load();
    } catch (err) {
      showError(err);
    }
  });

  load();
  wrap.append(list);
  const el = h('div',
    banner,
    updatesBanner,
    h('div.toolbar',
      h('div.input-wrap', { style: { flex: 1, maxWidth: '420px' } }, icon('search'), filter),
      h('div.grow'),
      (() => {
        const check = iconBtn('refresh', { tip: t('updates.check'), class: 'boxed' });
        check.addEventListener('click', () => checkUpdates(check));
        return check;
      })(),
      btn(t('mods.openFolder'), { icon: 'folder', onclick: () => api.app.openFolder(k.folder, inst.id) }),
      btn(t('mods.addFiles'), {
        icon: 'plus',
        onclick: async () => {
          try {
            const added = await k.pick(inst.id);
            if (added.length) {
              toast(t('mods.added', { n: added.length }), { icon: 'plus' });
              await load();
            }
          } catch (e) {
            showError(e);
          }
        },
      })),
    kind === 'resourcepack' ? h('div.hint.dim', { style: { margin: '-6px 0 12px 2px', fontSize: '12px' } }, t('content.resourcepack.order')) : null,
    wrap);
  return { el };
}

/** Shader packs need Iris (with Sodium), which the catalog installs in one click. */
function irisBanner(inst, reload) {
  const install = btn(t('content.shader.installIris'), {
    class: 'primary sm',
    icon: 'download',
    onclick: async () => {
      install.disabled = true;
      try {
        await api.mods.install(inst.id, 'iris');
        toast(t('mods.installedToast', { name: 'Iris + Sodium' }), { icon: 'download' });
        refreshInstalls().catch(() => {});
        reload();
      } catch (e) {
        install.disabled = false;
        showError(e);
      }
    },
  });
  return h('div.note.banner', { style: { marginBottom: '14px' } },
    icon('sun'), h('div.grow', h('b', t('content.shader.needIris')), h('div', t('content.shader.needIrisHint'))), install);
}

// ------------------------------------------------------------------ catalog

function catalogTab(inst, kind) {
  const k = KINDS[kind];
  const search = h('input.input', { placeholder: t(`content.${kind}.search`) });
  const sortBtn = h('button.select', { type: 'button', style: { minWidth: '200px', marginLeft: 'auto' } });
  const grid = h('div.catalog');
  const moreWrap = h('div', { style: { display: 'flex', justifyContent: 'center', marginTop: '18px' } });
  let sort = kind === 'mod' ? 'relevance' : 'downloads';
  let offset = 0;
  let total = 0;
  let installed = new Set();
  let token = 0;
  const installing = new Set();

  k.installed(inst.id).then((ids) => {
    installed = new Set(ids);
    for (const card of grid.children) card.sync?.();
  }).catch(() => {});

  const renderSort = () => fill(sortBtn, h('span', t(`mods.sort.${sort}`)), icon('chevronDown'));
  sortBtn.addEventListener('click', () => menu(sortBtn, SORTS.map((key) => ({
    label: t(`mods.sort.${key}`),
    checked: key === sort,
    onclick: () => {
      sort = key;
      renderSort();
      run(true);
    },
  }))));
  renderSort();

  const card = (item) => {
    const action = h('div');
    const sync = () => {
      clear(action);
      if (installed.has(item.id)) action.append(h('span.chip.accent', icon('check'), t('mods.installedBadge')));
      else if (installing.has(item.id)) action.append(btn(t('mods.installing'), { class: 'sm', disabled: true }));
      else if (kind === 'modpack') action.append(btn(t('mods.install'), {
        class: 'sm primary',
        icon: 'download',
        // The dialog asks for the version and the installation's name, then shows the download.
        onclick: () => installModpack({ project: item }, {
          onDone: () => {
            installed.add(item.id);
            sync();
          },
        }),
      }));
      else action.append(btn(t('mods.install'), {
        class: 'sm primary',
        icon: 'download',
        onclick: async () => {
          installing.add(item.id);
          sync();
          try {
            const file = await k.install(inst.id, item.id);
            installed.add(item.id);
            // A pack you just picked is one you want to see: switch it on (shaders only if none is).
            if (kind === 'resourcepack') await api.packs.setEnabled(inst.id, kind, file, true).catch(() => {});
            if (kind === 'shader') {
              const { list } = await api.packs.list(inst.id, 'shader');
              if (!list.some((s) => s.enabled)) await api.packs.setEnabled(inst.id, kind, file, true).catch(() => {});
            }
            toast(t(kind === 'mod' ? 'mods.installedToast' : `content.${kind}.installed`, { name: item.title }), { icon: 'download' });
            if (kind === 'mod') refreshInstalls().catch(() => {});
          } catch (e) {
            showError(e);
          } finally {
            installing.delete(item.id);
            sync();
          }
        },
      }));
    };
    sync();
    const el = h('div.card.mod-card',
      itemIcon(item.icon, kind, 'lg'),
      h('div.grow', { style: { minWidth: 0 } },
        h('div.title.ellipsis', item.title),
        h('div.by.ellipsis', t('mods.by', { author: item.author })),
        h('div.desc', item.description),
        h('div.foot',
          h('span.stats', icon('download'), compactNumber(item.downloads)),
          kind === 'modpack' && item.minecraft
            ? h('span.stats', icon('grass'), item.minecraft)
            : h('span.stats', icon('clock'), timeAgo(new Date(item.updated).getTime())),
          h('div.grow'),
          action)));
    el.sync = sync;
    return el;
  };

  const run = async (reset) => {
    const mine = ++token;
    if (reset) {
      offset = 0;
      fill(grid, Array.from({ length: 6 }, () => h('div.card.mod-card', h('div.mod-icon.lg.skeleton'), h('div.grow', h('div.skeleton', { style: { height: '16px', width: '50%' } }), h('div.skeleton', { style: { height: '12px', width: '30%', marginTop: '8px' } }), h('div.skeleton', { style: { height: '30px', marginTop: '10px' } })))));
      clear(moreWrap);
    }
    try {
      const res = await k.search({ query: search.value.trim(), offset, limit: 20, index: sort, installId: inst.id });
      if (mine !== token) return;
      if (reset) clear(grid);
      total = res.total;
      offset += res.hits.length;
      if (!res.hits.length && reset) {
        grid.append(h('div.card', { style: { gridColumn: '1 / -1' } }, h('div.empty-state', h('div.ic', icon('search')), h('h3', t('mods.noResults')), h('p', t('mods.noResultsHint')))));
      }
      append(grid, res.hits.map(card));
      clear(moreWrap);
      if (offset < total) moreWrap.append(btn(t('mods.more'), { icon: 'chevronDown', onclick: () => run(false) }));
    } catch (e) {
      if (mine !== token) return;
      clear(grid);
      showError(e);
    }
  };
  let debounce = null;
  search.addEventListener('input', () => {
    clearTimeout(debounce);
    debounce = setTimeout(() => run(true), 350);
  });
  run(true);
  const el = h('div',
    h('div.toolbar', h('div.input-wrap', { style: { flex: 1, maxWidth: '520px' } }, icon('search'), search), sortBtn),
    grid,
    moreWrap);
  return { el };
}

// ------------------------------------------------------------------ page

let rememberedKind = 'mod';

export function render(params) {
  let tab = params.tab || 'installed';
  let kind = params.kind || rememberedKind;
  const body = h('div');
  const head = h('div.page-head');
  const show = () => {
    const inst = selectedInstall();
    rememberedKind = kind;
    const instPick = h('button.chip', { type: 'button', style: { cursor: 'pointer', height: '30px' } },
      h('div.inst-icon', { style: { width: '18px', height: '18px', borderRadius: '5px' } }, installIcon(inst)),
      t('mods.for', { name: inst.name }), icon('chevronDown'));
    instPick.addEventListener('click', () => menu(instPick, state.installs.map((i) => ({
      label: i.name,
      sub: i.loader === 'fabric' ? `Fabric ${i.version}` : `Minecraft ${i.version}`,
      checked: i.id === inst.id,
      onclick: () => selectInstall(i.id),
    }))));
    const kinds = h('div.tabs.kinds',
      ...Object.keys(KINDS).map((key) => h('button', {
        type: 'button',
        class: key === kind ? 'on' : null,
        onclick: () => {
          kind = key;
          show();
        },
      }, icon(KINDS[key].icon), t(`content.${key}.title`))));
    const tabs = h('div.tabs',
      ...[['installed', 'mods.installed', 'box'], ['catalog', 'mods.catalog', 'search']].map(([key, label, ic]) => h('button', {
        type: 'button',
        class: key === tab ? 'on' : null,
        onclick: () => {
          tab = key;
          show();
        },
      }, icon(ic), t(label))));
    // Modpacks do not go into the selected installation: no picker, no installed list.
    const catalogOnly = Boolean(KINDS[kind].catalogOnly);
    fill(head,
      h('div', h('h1', t(`content.${kind}.title`)),
        h('div.row', { style: { marginTop: '10px', gap: '10px', flexWrap: 'wrap' } }, kinds, catalogOnly ? null : instPick),
        catalogOnly ? h('p', { style: { marginTop: '10px' } }, t(`content.${kind}.hint`)) : null),
      catalogOnly ? null : h('div.actions', tabs));
    clear(body);
    if (catalogOnly) {
      body.append(catalogTab(inst, kind).el);
      return;
    }
    // Mods and shaders need a mod loader; resource packs work in any installation.
    if (inst.loader !== 'fabric' && kind !== 'resourcepack') {
      body.append(h('div.card', h('div.empty-state', h('div.ic', icon(KINDS[kind].icon)), h('h3', t(kind === 'shader' ? 'content.shader.needFabric' : 'mods.needFabric')),
        btn(t('installs.title'), { icon: 'layers', onclick: () => navigate('installs') }))));
      return;
    }
    body.append((tab === 'installed' ? installedTab(inst, kind) : catalogTab(inst, kind)).el);
  };
  show();
  const off = on('settings', (s) => {
    if (s.selectedInstall !== body.dataset.inst) {
      body.dataset.inst = s.selectedInstall;
      // The modpack catalog does not depend on the installation (installing a pack selects it).
      if (!KINDS[kind].catalogOnly) show();
    }
  });
  body.dataset.inst = state.settings.selectedInstall;
  return { el: h('div.page', head, body), destroy: off };
}
