// Installing a modpack: one from the Modrinth catalog (the player picks a version) or a .mrpack
// file. It becomes a new installation with its own folder; the dialog stays open with the
// download until the pack is in place, then selects it for Play.

import { h, fill, btn, toast, modal, modalHead, toggle, popover } from './dom.js';
import { icon, installIcon, squarePicture } from './icons.js';
import { t, tp, formatBytes } from './i18n.js';
import { api, state, refreshInstalls, showError } from './store.js';
import { selectInstall } from './app.js';

/** A pack's picture from Modrinth (often WebP or GIF) kept as an installation icon; null when it fails. */
async function packIcon(url) {
  if (!url) return null;
  try {
    const data = await api.modpacks.icon(url);
    return data ? await api.icons.saveCustom(await squarePicture(data)) : null;
  } catch {
    return null;
  }
}

/** The newest release, or the newest version when a pack only has betas. */
function defaultVersion(versions) {
  return versions.find((v) => v.type === 'release') || versions[0] || null;
}

/** The pack's versions, searchable by number or Minecraft version. */
function versionPicker(anchor, versions, current, onpick) {
  popover(anchor, (close) => {
    const search = h('input.input', { placeholder: t('modpack.searchVersion'), style: { height: '36px' } });
    const list = h('div.menu-scroll', { style: { maxHeight: '300px' } });
    const redraw = () => {
      const q = search.value.trim().toLowerCase();
      const shown = versions.filter((v) => !q || v.number.toLowerCase().includes(q) || v.minecraft.some((m) => m.includes(q))).slice(0, 150);
      fill(list, shown.length ? shown.map((v) => h('button.menu-item', {
        type: 'button',
        onclick: () => {
          onpick(v);
          close();
        },
      },
      h('div.grow', { style: { minWidth: 0 } }, h('div.ellipsis', v.number), h('div.sub.ellipsis', `Minecraft ${v.minecraft.join(', ')}`)),
      v.type !== 'release' ? h('span.badge', v.type) : null,
      v.id === current.id ? h('span.check', icon('check')) : null)) : h('div.menu-item.dim', t('mods.noResults')));
    };
    search.addEventListener('input', redraw);
    redraw();
    return h('div', { style: { width: '320px', padding: '6px' } }, h('div.row', { style: { marginBottom: '8px' } }, search), list);
  }, { minWidth: 320 });
}

/**
 * Opens the install dialog for {project} (a catalog item) or {pack} (a .mrpack file as the main
 * process read it). {@code onDone} gets the new installation.
 */
export function installModpack({ project = null, pack = null }, { onDone } = {}) {
  const auroraMc = state.info.aurora ? state.info.aurora.minecraft : null;
  // modal() checks locked on every Escape and outside click: set while the pack downloads.
  const opts = { locked: false, wide: true };
  modal((close) => {
    const title = project ? project.title : pack.name;
    let chosen = null;
    let versions = [];
    let aurora = true;
    let busy = false;

    const picture = project && project.icon ? h('img.mod-icon.lg', { src: project.icon, alt: '' }) : h('div.mod-icon.lg', icon('box'));
    if (picture.tagName === 'IMG') picture.addEventListener('error', () => picture.replaceWith(h('div.mod-icon.lg', icon('box'))));
    const about = project ? project.description : pack.summary;
    const name = h('input.input', { value: title.slice(0, 40), placeholder: title.slice(0, 40), maxlength: 40 });
    const versionBtn = h('button.select', { type: 'button', disabled: true }, h('span.dim', t('modpack.loadingVersions')));
    const auroraRow = h('div');
    const status = h('div.grow');
    const amount = h('div.amount');
    const bar = h('i');
    const progressBox = h('div.pack-progress', { style: { display: 'none' } }, h('div.row', status, amount), h('div.track', bar));
    const install = btn(t('modpack.install'), { class: 'primary', icon: 'download', disabled: Boolean(project) });
    const cancel = btn(t('common.cancel'));

    const minecraft = () => (chosen ? chosen.minecraft : pack ? [pack.minecraft] : []);
    const auroraOk = () => Boolean(auroraMc) && minecraft().includes(auroraMc) && (!pack || pack.loader === 'fabric');
    const renderAurora = () => {
      fill(auroraRow, auroraOk() ? h('div.set-row', { style: { padding: '4px 0' } },
        h('div.inst-icon', { style: { width: '38px', height: '38px' } }, installIcon({ aurora: true })),
        h('div.text', h('div.t', t('editor.aurora')), h('div.d', t('modpack.auroraHint'))),
        toggle(aurora, (on) => {
          aurora = on;
        })) : null);
    };
    const renderVersion = () => {
      fill(versionBtn, h('span.ellipsis', chosen.number), h('span.dim', `· ${chosen.minecraft.join(', ')}`), h('div.grow'), icon('chevronDown'));
    };
    versionBtn.addEventListener('click', () => {
      if (busy || !chosen) return;
      versionPicker(versionBtn, versions, chosen, (v) => {
        chosen = v;
        renderVersion();
        renderAurora();
      });
    });

    const setProgress = (p) => {
      let ratio = 0;
      if (p.stage === 'pack') {
        status.textContent = t('modpack.stage.pack');
        ratio = p.totalBytes ? p.doneBytes / p.totalBytes : 0;
      } else if (p.stage === 'files') {
        status.textContent = t('modpack.stage.files', { done: p.doneFiles || 0, total: p.totalFiles || 0 });
        ratio = p.totalBytes ? p.doneBytes / p.totalBytes : p.totalFiles ? p.doneFiles / p.totalFiles : 0;
      } else if (p.stage === 'extract') {
        status.textContent = t('modpack.stage.extract');
        ratio = 1;
      }
      amount.textContent = p.totalBytes && p.stage !== 'extract' ? `${formatBytes(p.doneBytes || 0)} / ${formatBytes(p.totalBytes)}` : '';
      bar.style.width = `${Math.round(Math.min(1, ratio) * 100)}%`;
    };
    const setBusy = (on) => {
      busy = on;
      opts.locked = on;
      name.disabled = on;
      versionBtn.disabled = on || !chosen;
      install.disabled = on || (project && !chosen);
      progressBox.style.display = on ? '' : 'none';
      if (on) requestAnimationFrame(() => progressBox.scrollIntoView({ block: 'nearest', behavior: 'smooth' }));
      fill(install, on ? h('div.spinner') : icon('download'), h('span', t(on ? 'modpack.installing' : 'modpack.install')));
    };
    // While the pack downloads, closing the dialog cancels the download.
    const dismiss = () => (busy ? api.modpacks.cancel().catch(() => {}) : close());
    cancel.addEventListener('click', dismiss);

    install.addEventListener('click', async () => {
      if (busy) return;
      setBusy(true);
      setProgress({ stage: project ? 'pack' : 'files' });
      const off = api.modpacks.onProgress(setProgress);
      try {
        const iconId = project ? await packIcon(project.icon) : null;
        const saved = await api.modpacks.install({
          versionId: chosen ? chosen.id : undefined,
          file: pack ? pack.file : undefined,
          name: name.value.trim() || title,
          aurora: auroraOk() && aurora,
          icon: iconId || undefined,
        });
        await refreshInstalls();
        await selectInstall(saved.id);
        toast(t('modpack.done', { name: saved.name }), { icon: 'check' });
        onDone?.(saved);
        busy = false;
        opts.locked = false;
        close(saved);
      } catch (e) {
        setBusy(false);
        showError(e);
      } finally {
        off();
      }
    });

    if (project) {
      api.modpacks.versions(project.id).then((list) => {
        versions = list;
        chosen = defaultVersion(list);
        if (!chosen) {
          fill(versionBtn, h('span.dim', t('modpack.noVersions')));
          return;
        }
        versionBtn.disabled = busy;
        install.disabled = busy;
        renderVersion();
        renderAurora();
      }).catch((e) => {
        fill(versionBtn, h('span.dim', t('error.network')));
        showError(e);
      });
    } else {
      renderAurora();
    }

    const contents = pack ? h('div.field', h('label', t('modpack.contents')), h('div.muted', { style: { fontSize: '13.5px' } },
      [`Minecraft ${pack.minecraft}`, pack.loader === 'fabric' ? `Fabric ${pack.loaderVersion}` : t('editor.vanilla'), tp('modpack.files', pack.files), pack.size ? formatBytes(pack.size) : null].filter(Boolean).join(' · '))) : null;
    return [
      modalHead(t('modpack.title'), t('modpack.subtitle'), dismiss),
      h('div.modal-body',
        h('div.row', { style: { gap: '14px', alignItems: 'center' } }, picture,
          h('div.grow', { style: { minWidth: 0 } },
            h('div.ellipsis', { style: { fontWeight: 700, fontSize: '16px' } }, title),
            h('div.dim.ellipsis', { style: { fontSize: '12.5px', marginTop: '2px' } }, project ? t('mods.by', { author: project.author }) : pack.version ? t('modpack.packVersion', { version: pack.version }) : ''),
            about ? h('div.muted.clamp2', { style: { fontSize: '12.5px', marginTop: '6px' } }, about) : null)),
        h('div.field-row',
          project ? h('div.field', h('label', t('modpack.version')), versionBtn) : contents,
          h('div.field', h('label', t('modpack.name')), name)),
        auroraRow,
        progressBox),
      h('div.modal-foot', cancel, install),
    ];
  }, opts);
}
