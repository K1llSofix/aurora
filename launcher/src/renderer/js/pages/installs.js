import { h, fill, btn, iconBtn, clear, toast, modal, modalHead, menu, toggle, segmented, confirmDialog, popover } from '../dom.js';
import { icon, installIcon, iconById, iconId, iconCatalog, iconPngs, squarePicture } from '../icons.js';
import { t, tp, timeAgo, formatMemory } from '../i18n.js';
import { api, state, set, on, refreshInstalls, showError } from '../store.js';
import { play, installSubtitle, selectInstall, navigate } from '../app.js';
import { installModpack } from '../modpack-install.js';

function card(inst) {
  const selected = inst.id === state.settings.selectedInstall;
  const more = iconBtn('more', { tip: t('installs.edit') });
  const windows = state.info.platform === 'win32';
  more.addEventListener('click', () => menu(more, [
    { icon: 'edit', label: t('installs.edit'), onclick: () => editor(inst) },
    windows ? { icon: 'link', label: t('installs.shortcutDesktop'), sub: t('installs.shortcutHint'), onclick: () => shortcut(inst, 'desktop') } : null,
    windows ? { icon: 'layers', label: t('installs.shortcutStart'), onclick: () => shortcut(inst, 'startmenu') } : null,
    { icon: 'folder', label: t('installs.folder'), onclick: () => api.app.openFolder('game', inst.id) },
    { icon: 'shield', label: t('installs.repair'), onclick: () => repair(inst) },
    'sep',
    { icon: 'trash', label: t('installs.delete'), danger: true, onclick: () => remove(inst) },
  ], { alignRight: true, minWidth: 280 }));
  const chips = h('div.chips',
    selected ? h('span.chip.accent', icon('check'), t('installs.selected')) : null,
    h('span.chip', icon('grass'), inst.version),
    inst.loader === 'fabric' ? h('span.chip', icon('puzzle'), 'Fabric') : h('span.chip', t('editor.vanilla')),
    inst.auroraVersion ? h('span.chip.accent', icon('sparkle'), 'Aurora') : null,
    inst.modpack ? h('span.chip.violet', icon('box'), t('installs.modpack', { version: inst.modpack.version || '' }).trim()) : null,
    h('span.chip', icon('chip'), formatMemory(inst.memory || state.settings.memory)),
    inst.loader === 'fabric' ? h('span.chip', tp('installs.mods', inst.modCount)) : null);
  const el = h(`div.card.inst-card${selected ? '.selected' : ''}`,
    h('div.top',
      h('div.inst-icon.lg', installIcon(inst)),
      h('div.grow', { style: { minWidth: 0 } },
        h('div.name.ellipsis', inst.name),
        h('div.ver.ellipsis', installSubtitle(inst))),
      more),
    chips,
    h('div.bottom',
      h('div.dim.grow', { style: { fontSize: '12.5px' } }, icon('clock'), ' ',
        inst.lastPlayed ? t('installs.lastPlayed', { when: timeAgo(inst.lastPlayed) }) : inst.installed ? t('installs.never') : t('installs.notInstalled')),
      selected ? null : btn(t('installs.select'), { class: 'sm', onclick: () => selectInstall(inst.id) }),
      btn(t('home.play'), {
        class: 'sm primary',
        icon: 'play',
        onclick: async () => {
          await selectInstall(inst.id);
          play();
        },
      })));
  el.querySelector('.bottom .dim svg').style.cssText = 'display:inline;width:13px;height:13px;vertical-align:-2px';
  return el;
}

/** A shortcut that starts this installation straight away, with the installation's icon. */
export async function shortcut(inst, where) {
  try {
    // If the icon cannot be drawn, the shortcut still works with the launcher's own icon.
    const pngs = await iconPngs(inst).catch(() => []);
    const file = await api.installs.shortcut(inst.id, where, pngs);
    toast(t(where === 'startmenu' ? 'installs.shortcutStartDone' : 'installs.shortcutDone', { name: inst.name }), { icon: 'link', detail: file });
    await refreshInstalls();
  } catch (e) {
    showError(e);
  }
}

async function remove(inst) {
  const yes = await confirmDialog({ title: t('installs.deleteTitle'), text: t('installs.deleteText', { name: inst.name }), confirm: t('common.delete'), danger: true });
  if (!yes) return;
  try {
    await api.installs.remove(inst.id);
    await refreshInstalls();
    set('settings', await api.settings.get());
    toast(t('installs.deleted', { name: inst.name }), { icon: 'trash' });
  } catch (e) {
    showError(e);
  }
}

async function repair(inst) {
  await selectInstall(inst.id);
  try {
    await api.game.repair(inst.id);
  } catch (e) {
    showError(e);
  }
}

/** Version picker: a searchable list of releases (and snapshots on request). */
function versionPicker(anchor, current, onpick) {
  let snapshots = state.settings.showSnapshots;
  popover(anchor, (close) => {
    const list = h('div.menu-scroll', { style: { maxHeight: '300px' } }, h('div.menu-item.dim', t('editor.loadingVersions')));
    const search = h('input.input', { placeholder: t('editor.searchVersion'), style: { height: '36px' } });
    let versions = [];
    const redraw = () => {
      const q = search.value.trim().toLowerCase();
      fill(list, versions.filter((v) => !q || v.id.toLowerCase().includes(q)).slice(0, 200).map((v) => h('button.menu-item', {
        type: 'button',
        onclick: () => {
          onpick(v);
          close();
        },
      }, h('div.grow', v.id), v.aurora ? h('span.chip.accent', { style: { height: '20px' } }, 'Aurora') : v.type === 'snapshot' ? h('span.badge', 'snapshot') : null,
      v.id === current ? h('span.check', icon('check')) : null)));
    };
    const load = () => api.installs.versions(snapshots).then((v) => {
      versions = v;
      redraw();
    }).catch((e) => {
      fill(list, h('div.menu-item.dim', t('error.network')));
      showError(e);
    });
    search.addEventListener('input', redraw);
    load();
    const snap = toggle(snapshots, (on) => {
      snapshots = on;
      api.settings.set({ showSnapshots: on }).catch(() => {});
      load();
    });
    return h('div', { style: { width: '300px', padding: '6px' } },
      h('div.row', { style: { marginBottom: '8px' } }, search),
      list,
      h('div.menu-sep'),
      h('div.row', { style: { padding: '4px 8px' } }, h('div.grow.muted', { style: { fontSize: '13px' } }, t('editor.snapshots')), snap));
  }, { minWidth: 300 });
}

/**
 * Icon picker: the Aurora mark and colours always, textures from the installed game once it has
 * been downloaded, and any picture the player has.
 */
function iconPicker(anchor, current, onpick) {
  popover(anchor, (close) => {
    const pick = (id) => {
      onpick(id);
      close();
    };
    const cell = (id) => h(`button.icon-cell${id === current ? '.on' : ''}`, { type: 'button', onclick: () => pick(id) }, iconById(id));
    const catalog = iconCatalog();
    const file = h('input', { type: 'file', accept: 'image/png,image/jpeg,image/webp,image/gif,image/bmp', style: { display: 'none' } });
    file.addEventListener('change', async () => {
      const chosen = file.files && file.files[0];
      if (!chosen) return;
      try {
        pick(await api.icons.saveCustom(await squarePicture(chosen)));
      } catch (e) {
        showError(e);
      }
    });
    return h('div', { style: { width: '332px', padding: '4px' } },
      h('div.menu-label', t('icons.title')),
      h('div.icon-grid', cell('aurora'), ['grass', 'sky', 'ember', 'stone', 'fabric'].map((c) => cell(`color:${c}`))),
      h('div.menu-label', t('icons.minecraft')),
      catalog.length
        ? h('div.icon-grid.scroll', catalog.map((e) => cell(`mc:${e.id}`)))
        : h('div.hint.dim', { style: { padding: '0 10px 8px', fontSize: '12px' } }, t('icons.afterInstall')),
      h('div.menu-sep'),
      file,
      h('button.menu-item', { type: 'button', onclick: () => file.click() }, icon('plus'), h('div.grow', t('icons.upload'))));
  }, { minWidth: 340 });
}

/** The installation editor, for a new installation or an existing one. */
export function editor(existing) {
  const auroraMc = state.info.aurora ? state.info.aurora.minecraft : null;
  // The list reports the folder in use; only a folder the player chose is kept as custom.
  const draft = existing
    ? { ...existing, gameDir: existing.customDir ? existing.gameDir : null, icon: iconId(existing) }
    : { name: '', version: auroraMc || '26.3', loader: 'fabric', aurora: Boolean(auroraMc), memory: null, resolution: null, gameDir: null, jvmArgs: '', icon: auroraMc ? 'aurora' : 'mc:grass' };
  // Until the player picks an icon, it follows whether the installation runs Aurora.
  let iconChosen = Boolean(existing);
  modal((close) => {
    const name = h('input.input', { value: draft.name, placeholder: draft.aurora ? 'Aurora' : `Minecraft ${draft.version}`, maxlength: 40 });
    const iconButton = h('button.icon-pick', { type: 'button', 'data-tip': t('icons.change') });
    const renderIcon = () => fill(iconButton, iconById(draft.icon), h('span.edit', icon('edit')));
    iconButton.addEventListener('click', () => iconPicker(iconButton, draft.icon, (id) => {
      draft.icon = id;
      iconChosen = true;
      renderIcon();
    }));
    renderIcon();
    const versionBtn = h('button.select', { type: 'button' });
    const auroraRow = h('div.set-row', { style: { padding: '4px 0' } });
    const memoryRow = h('div.col');
    const resRow = h('div.row');
    const dirRow = h('div.row');
    const jvm = h('input.input.mono', { value: draft.jvmArgs || '', placeholder: '-XX:+UseZGC' });

    const renderVersion = () => {
      fill(versionBtn, h('span', draft.version), icon('chevronDown'));
    };
    const renderAurora = () => {
      const supported = auroraMc && draft.version === auroraMc;
      if (!supported || draft.loader !== 'fabric') draft.aurora = false;
      fill(auroraRow, 
        h('div.inst-icon', { style: { width: '38px', height: '38px' } }, installIcon({ aurora: true })),
        h('div.text', h('div.t', t('editor.aurora')), h('div.d', supported ? t('editor.auroraHint') : t('editor.auroraUnsupported', { version: auroraMc || '—' }))),
        toggle(draft.aurora, (on) => {
          draft.aurora = on;
          if (on) draft.loader = 'fabric';
          renderLoader();
        }, { disabled: !supported }));
      if (!iconChosen) {
        draft.icon = draft.aurora ? 'aurora' : 'mc:grass';
        renderIcon();
      }
    };
    const loaderWrap = h('div');
    const renderLoader = () => {
      fill(loaderWrap, segmented([['fabric', 'Fabric'], ['vanilla', t('editor.vanilla')]], draft.loader, (v) => {
        draft.loader = v;
        renderAurora();
      }));
      renderAurora();
    };
    const renderMemory = () => {
      const custom = draft.memory !== null;
      const total = Math.floor(state.info.totalMemory / 1024 ** 2);
      const max = Math.max(2048, Math.min(32768, Math.floor((total - 1024) / 512) * 512));
      const value = h('div.value', formatMemory(draft.memory || state.settings.memory));
      const slider = h('input.slider', { type: 'range', min: 1024, max, step: 512 });
      slider.value = draft.memory || state.settings.memory;
      slider.addEventListener('input', () => {
        draft.memory = Number(slider.value);
        value.textContent = formatMemory(draft.memory);
      });
      fill(memoryRow, 
        h('div.row', segmented([['default', t('editor.memoryDefault')], ['custom', t('editor.custom')]], custom ? 'custom' : 'default', (v) => {
          draft.memory = v === 'custom' ? state.settings.memory : null;
          renderMemory();
        }), h('div.grow'), custom ? value : h('div.muted', formatMemory(state.settings.memory))),
        custom ? slider : null);
    };
    const renderRes = () => {
      const custom = draft.resolution !== null;
      const w = h('input.input', { type: 'number', min: 320, value: custom ? draft.resolution.width : 1280, style: { width: '110px' } });
      const hh = h('input.input', { type: 'number', min: 240, value: custom ? draft.resolution.height : 720, style: { width: '110px' } });
      const sync = () => {
        draft.resolution = { width: Number(w.value) || 1280, height: Number(hh.value) || 720 };
      };
      w.addEventListener('input', sync);
      hh.addEventListener('input', sync);
      fill(resRow, segmented([['default', t('editor.default')], ['custom', t('editor.custom')]], custom ? 'custom' : 'default', (v) => {
        draft.resolution = v === 'custom' ? { width: 1280, height: 720 } : null;
        renderRes();
      }), custom ? w : null, custom ? h('span.dim', '×') : null, custom ? hh : null);
    };
    const renderDir = () => {
      fill(dirRow, 
        h('div.input.ellipsis', { style: { display: 'flex', alignItems: 'center', flex: 1, color: draft.gameDir ? 'var(--text)' : 'var(--text-3)' } }, draft.gameDir || t('editor.gameDirDefault')),
        btn(t('editor.choose'), {
          icon: 'folder',
          onclick: async () => {
            const dir = await api.dialog.pickFolder(t('editor.gameDir')).catch(() => null);
            if (dir) {
              draft.gameDir = dir;
              renderDir();
            }
          },
        }),
        draft.gameDir ? iconBtn('x', { tip: t('editor.default'), onclick: () => { draft.gameDir = null; renderDir(); } }) : null);
    };
    versionBtn.addEventListener('click', () => versionPicker(versionBtn, draft.version, (v) => {
      draft.version = v.id;
      if (v.aurora && draft.loader === 'fabric') draft.aurora = true;
      name.placeholder = draft.aurora ? 'Aurora' : `Minecraft ${draft.version}`;
      renderVersion();
      renderAurora();
    }));
    renderVersion();
    renderLoader();
    renderMemory();
    renderRes();
    renderDir();

    const save = btn(existing ? t('editor.save') : t('editor.create'), { class: 'primary', icon: existing ? 'check' : 'plus' });
    save.addEventListener('click', async () => {
      save.disabled = true;
      try {
        const saved = await api.installs.save({
          ...draft,
          name: name.value.trim() || name.placeholder,
          jvmArgs: jvm.value.trim(),
        });
        // Shortcuts on the desktop take the new icon and description too.
        if (existing && saved.shortcuts && saved.shortcuts.length) {
          iconPngs(saved).catch(() => []).then((pngs) => api.installs.refreshShortcuts(saved.id, pngs)).catch(() => {});
        }
        await refreshInstalls();
        if (!existing) await selectInstall(saved.id);
        toast(t('installs.saved'), { icon: 'check' });
        close();
      } catch (e) {
        save.disabled = false;
        showError(e);
      }
    });
    return [
      modalHead(existing ? t('editor.titleEdit') : t('editor.titleNew'), t('editor.subtitle'), close),
      h('div.modal-body',
        h('div.row', { style: { gap: '14px', alignItems: 'flex-end' } },
          iconButton,
          h('div.field.grow', h('label', t('editor.name')), name)),
        h('div.row', { style: { gap: '18px', alignItems: 'flex-start' } },
          h('div.field', h('label', t('editor.version')), versionBtn),
          h('div.field', h('label', t('editor.loader')), loaderWrap)),
        auroraRow,
        h('div.field-row',
          h('div.field', h('label', t('editor.memory')), memoryRow),
          h('div.field', h('label', t('editor.resolution')), resRow)),
        h('div.field', h('label', t('editor.gameDir')), dirRow),
        h('div.field', h('label', t('editor.jvm')), jvm)),
      h('div.modal-foot', btn(t('common.cancel'), { onclick: () => close() }), save),
    ];
  }, { wide: true });
}

/** Reads a .mrpack (picked, dropped or given by path) and offers to install it. */
export async function openPack(file) {
  try {
    const pack = file ? await api.modpacks.read(file) : await api.modpacks.pick();
    if (pack) installModpack({ pack });
  } catch (e) {
    showError(e);
  }
}

/** The second "add" card: a modpack from the Modrinth catalog or from a .mrpack file. */
function packCard() {
  const el = h('div.card.inst-card.add', { role: 'button', tabindex: 0 }, h('div.plus', icon('box')), h('div', t('modpack.add')));
  el.addEventListener('click', () => menu(el, [
    { icon: 'search', label: t('modpack.fromCatalog'), onclick: () => navigate('mods', { kind: 'modpack' }) },
    { icon: 'folder', label: t('modpack.fromFile'), sub: t('modpack.fromFileHint'), onclick: () => openPack(null) },
  ], { minWidth: 280 }));
  return el;
}

export function render() {
  const grid = h('div.grid');
  const rebuild = () => {
    fill(grid,
      state.installs.map(card),
      h('div.card.inst-card.add', { role: 'button', tabindex: 0, onclick: () => editor(null) }, h('div.plus', icon('plus')), h('div', t('installs.new'))),
      packCard());
  };
  rebuild();
  refreshInstalls().catch(() => {});
  const offs = [on('installs', rebuild), on('settings', rebuild)];
  const el = h('div.page',
    h('div.page-head',
      h('div', h('h1', t('installs.title')), h('p', t('installs.subtitle'))),
      h('div.actions', btn(t('installs.new'), { class: 'primary', icon: 'plus', onclick: () => editor(null) }))),
    grid);

  // A .mrpack dropped from Explorer installs as a new installation.
  let depth = 0;
  let zone = null;
  const hasFiles = (e) => Array.from(e.dataTransfer?.types || []).includes('Files');
  const hide = () => {
    depth = 0;
    zone?.remove();
    zone = null;
  };
  el.addEventListener('dragenter', (e) => {
    if (!hasFiles(e)) return;
    depth++;
    if (!zone) {
      zone = h('div.drop-zone', t('modpack.drop'));
      el.append(zone);
    }
  });
  el.addEventListener('dragleave', () => {
    depth = Math.max(0, depth - 1);
    if (!depth) hide();
  });
  el.addEventListener('drop', (e) => {
    hide();
    const file = Array.from(e.dataTransfer.files).map((f) => window.aurora.files.pathOf(f)).find((p) => /\.mrpack$/i.test(p));
    if (file) openPack(file);
  });
  return { el, destroy: () => offs.forEach((off) => off()) };
}
