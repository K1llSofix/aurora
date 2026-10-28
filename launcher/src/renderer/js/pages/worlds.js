import { h, fill, append, btn, iconBtn, clear, toast, menu, confirmDialog } from '../dom.js';
import { icon, installIcon } from '../icons.js';
import { t, timeAgo, formatBytes } from '../i18n.js';
import { api, state, on, selectedInstall, showError } from '../store.js';
import { play, selectInstall } from '../app.js';

/**
 * Singleplayer worlds of the selected installation: play one straight away, back it up to a zip
 * (the same kind the game's "Make Backup" writes) and bring a backup back as a new world.
 */

function worldIcon(world) {
  if (!world.icon) return h('div.mod-icon', icon('globe'));
  return h('img.mod-icon.pixel', { src: world.icon, alt: '' });
}

function worldRow(inst, world, reload) {
  const size = h('span');
  api.worlds.size(inst.id, world.folder).then((bytes) => {
    size.textContent = ` · ${formatBytes(bytes)}`;
  }).catch(() => {});
  const meta = [t(`worlds.mode.${world.mode}`), world.version, t('worlds.played', { when: timeAgo(world.lastPlayed) })].filter(Boolean).join(' · ');
  const backup = iconBtn('archive', { tip: t('worlds.backup') });
  backup.addEventListener('click', async () => {
    backup.disabled = true;
    backup.classList.add('busy');
    const off = api.worlds.onProgress((p) => {
      if (p.folder === world.folder && p.total) backup.dataset.tip = `${Math.floor((p.done / p.total) * 100)}%`;
    });
    try {
      await api.worlds.backup(inst.id, world.folder);
      toast(t('worlds.backedUp', { name: world.name }), { icon: 'archive' });
      reload();
    } catch (e) {
      showError(e);
    }
    off();
    backup.disabled = false;
    backup.classList.remove('busy');
    backup.dataset.tip = t('worlds.backup');
  });
  return h('div.mod-row',
    worldIcon(world),
    h('div.grow', { style: { minWidth: 0 } },
      h('div.title', h('span.ellipsis', world.name), world.folder !== world.name ? h('span.ver', world.folder) : null,
        world.cheats ? h('span.badge', t('worlds.cheats')) : null),
      h('div.desc.ellipsis', meta, size)),
    backup,
    iconBtn('folder', { tip: t('worlds.folder'), onclick: () => api.worlds.open(inst.id, world.folder).catch(showError) }),
    iconBtn('trash', {
      class: 'danger',
      tip: t('worlds.delete'),
      onclick: async () => {
        const yes = await confirmDialog({ title: t('worlds.deleteTitle'), text: t('worlds.deleteText', { name: world.name }), confirm: t('common.delete'), danger: true });
        if (!yes) return;
        try {
          await api.worlds.remove(inst.id, world.folder);
          toast(t('worlds.deleted', { name: world.name }), { icon: 'trash' });
          reload();
        } catch (e) {
          showError(e);
        }
      },
    }),
    btn(t('worlds.play'), {
      class: 'sm primary',
      icon: 'play',
      onclick: async () => {
        if (inst.id !== state.settings.selectedInstall) await selectInstall(inst.id);
        toast(t('worlds.starting', { name: world.name }), { icon: 'play' });
        play({ world: world.folder });
      },
    }));
}

function backupRow(inst, backup, reload) {
  const restore = btn(t('worlds.restore'), { class: 'sm', icon: 'undo' });
  restore.addEventListener('click', async () => {
    restore.disabled = true;
    try {
      const folder = await api.worlds.restore(inst.id, backup.file, t('worlds.restoredLabel'));
      toast(t('worlds.restored', { name: folder }), { icon: 'undo' });
      reload();
    } catch (e) {
      showError(e);
    }
    restore.disabled = false;
  });
  return h('div.mod-row',
    h('div.mod-icon', icon('archive')),
    h('div.grow', { style: { minWidth: 0 } },
      h('div.title', h('span.ellipsis', backup.world)),
      h('div.desc.ellipsis', `${new Date(backup.created).toLocaleString()} · ${formatBytes(backup.size)}`)),
    iconBtn('folder', { tip: t('worlds.showBackup'), onclick: () => api.worlds.showBackup(inst.id, backup.file).catch(showError) }),
    iconBtn('trash', {
      class: 'danger',
      tip: t('worlds.deleteBackup'),
      onclick: async () => {
        const yes = await confirmDialog({ title: t('worlds.deleteBackupTitle'), text: t('worlds.deleteBackupText', { name: backup.file }), confirm: t('common.delete'), danger: true });
        if (!yes) return;
        try {
          await api.worlds.removeBackup(inst.id, backup.file);
          reload();
        } catch (e) {
          showError(e);
        }
      },
    }),
    restore);
}

export function render() {
  const head = h('div.page-head');
  const body = h('div');

  const show = async () => {
    const inst = selectedInstall();
    const instPick = h('button.chip', { type: 'button', style: { cursor: 'pointer', height: '30px' } },
      h('div.inst-icon', { style: { width: '18px', height: '18px', borderRadius: '5px' } }, installIcon(inst)),
      t('mods.for', { name: inst.name }), icon('chevronDown'));
    instPick.addEventListener('click', () => menu(instPick, state.installs.map((i) => ({
      label: i.name,
      checked: i.id === inst.id,
      onclick: () => selectInstall(i.id),
    }))));
    fill(head,
      h('div', h('h1', t('worlds.title')), h('p', t('worlds.subtitle')), h('div.row', { style: { marginTop: '10px' } }, instPick)),
      h('div.actions', btn(t('worlds.openSaves'), { icon: 'folder', onclick: () => api.app.openFolder('saves', inst.id) })));
    fill(body, h('div.card', h('div.empty-state', h('div.spinner'))));
    let worlds = [];
    let backups = [];
    try {
      [worlds, backups] = await Promise.all([api.worlds.list(inst.id), api.worlds.backups(inst.id)]);
    } catch (e) {
      showError(e);
    }
    const reload = () => show();
    clear(body);
    const list = h('div.card.list-card');
    if (!worlds.length) {
      list.append(h('div.empty-state', h('div.ic', icon('globe')), h('h3', t('worlds.empty')), h('p', t('worlds.emptyHint'))));
    } else {
      append(list, worlds.map((w) => worldRow(inst, w, reload)));
    }
    body.append(list);
    body.append(h('div.section-title', t('worlds.backups')));
    const backupList = h('div.card.list-card');
    if (!backups.length) backupList.append(h('div.mod-row', h('div.desc', { style: { padding: '6px 4px' } }, t('worlds.noBackups'))));
    else append(backupList, backups.map((b) => backupRow(inst, b, reload)));
    body.append(backupList);
  };
  show();
  const off = on('settings', (s) => {
    if (s.selectedInstall !== body.dataset.inst) {
      body.dataset.inst = s.selectedInstall;
      show();
    }
  });
  body.dataset.inst = state.settings.selectedInstall;
  return { el: h('div.page', head, body), destroy: off };
}
