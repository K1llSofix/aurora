import { h, fill, btn, iconBtn, clear, toast, modal, menu, confirmDialog } from '../dom.js';
import { icon } from '../icons.js';
import { t, timeAgo, formatBytes } from '../i18n.js';
import { api, state, showError } from '../store.js';

/**
 * Screenshots of every installation in one place, newest first: thumbnails load as they scroll
 * into view, a click opens one large with copy, the folder and delete; the arrow keys flip.
 */

const thumbs = new Map();

function thumb(shot, observer) {
  const img = h('img', { alt: '', draggable: false });
  const box = h('div.shot-thumb', { dataset: { file: shot.file } }, img, h('div.shot-meta', h('span.ellipsis', shot.installName), h('span', timeAgo(shot.modified))));
  if (thumbs.has(shot.file)) img.src = thumbs.get(shot.file);
  else {
    box.classList.add('loading');
    box.load = async () => {
      try {
        const url = await api.shots.thumb(shot.file);
        if (url) {
          thumbs.set(shot.file, url);
          img.src = url;
        }
      } catch {
        // A file that went away meanwhile.
      }
      box.classList.remove('loading');
    };
    observer.observe(box);
  }
  return box;
}

function viewer(list, index, onremoved) {
  let i = index;
  let onKey = null;
  modal((close) => {
    const img = h('img', { alt: '' });
    const title = h('div.shot-title');
    const counter = h('span.dim');
    const stage = h('div.shot-stage', img);
    const prev = iconBtn('chevronLeft', { class: 'shot-nav prev', tip: t('shots.prev') });
    const next = iconBtn('chevronRight', { class: 'shot-nav next', tip: t('shots.next') });
    stage.append(prev, next);
    const show = async () => {
      const shot = list[i];
      fill(title, h('b.ellipsis', shot.name), h('span.dim', `· ${shot.installName} · ${new Date(shot.modified).toLocaleString()} · ${formatBytes(shot.size)}`));
      counter.textContent = `${i + 1} / ${list.length}`;
      prev.style.visibility = i > 0 ? '' : 'hidden';
      next.style.visibility = i < list.length - 1 ? '' : 'hidden';
      img.src = thumbs.get(shot.file) || '';
      stage.classList.add('loading');
      const full = await api.shots.image(shot.file).catch(() => null);
      if (list[i] === shot && full) img.src = full.url;
      stage.classList.remove('loading');
    };
    const step = (d) => {
      const n = i + d;
      if (n >= 0 && n < list.length) {
        i = n;
        show();
      }
    };
    prev.addEventListener('click', () => step(-1));
    next.addEventListener('click', () => step(1));
    onKey = (e) => {
      if (e.key === 'ArrowLeft') step(-1);
      if (e.key === 'ArrowRight') step(1);
    };
    document.addEventListener('keydown', onKey);
    const remove = async () => {
      const shot = list[i];
      const yes = await confirmDialog({ title: t('shots.deleteTitle'), text: t('shots.deleteText', { name: shot.name }), confirm: t('common.delete'), danger: true });
      if (!yes) return;
      try {
        await api.shots.remove(shot.file);
        list.splice(i, 1);
        onremoved(shot);
        if (!list.length) {
          close();
          return;
        }
        i = Math.min(i, list.length - 1);
        show();
      } catch (e) {
        showError(e);
      }
    };
    show();
    const head = h('div.modal-head', h('div.grow', { style: { minWidth: 0 } }, title), counter, iconBtn('x', { tip: t('common.close'), onclick: () => close() }));
    const foot = h('div.modal-foot',
      btn(t('shots.copy'), {
        icon: 'copy',
        class: 'primary',
        onclick: async () => {
          try {
            await api.shots.copy(list[i].file);
            toast(t('shots.copied'), { icon: 'copy' });
          } catch (e) {
            showError(e);
          }
        },
      }),
      btn(t('shots.show'), { icon: 'folder', onclick: () => api.app.showItem(list[i].file) }),
      h('div.grow'),
      btn(t('common.delete'), { icon: 'trash', class: 'danger', onclick: remove }));
    return [head, h('div.modal-body', { style: { paddingTop: 0 } }, stage), foot];
  }, { className: 'shot-viewer', onClose: () => document.removeEventListener('keydown', onKey) });
}

export function render() {
  const grid = h('div.shot-grid');
  const count = h('p');
  const filterBtn = h('button.chip', { type: 'button', style: { cursor: 'pointer', height: '30px' } });
  let all = [];
  let filter = null;
  const observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting && entry.target.load) {
        observer.unobserve(entry.target);
        entry.target.load();
      }
    }
  }, { rootMargin: '300px' });

  const shown = () => (filter ? all.filter((s) => s.installId === filter) : all);
  const redraw = () => {
    const list = shown();
    const inst = filter && state.installs.find((i) => i.id === filter);
    fill(filterBtn, icon('layers'), inst ? inst.name : t('shots.allInstalls'), icon('chevronDown'));
    count.textContent = t('shots.count', { n: list.length });
    clear(grid);
    if (!list.length) {
      grid.append(h('div.card.shot-empty', h('div.empty-state', h('div.ic', icon('image')), h('h3', t('shots.empty')), h('p', t('shots.emptyHint')))));
      return;
    }
    list.forEach((shot, index) => {
      const el = thumb(shot, observer);
      el.addEventListener('click', () => viewer(list, index, (gone) => {
        all = all.filter((s) => s !== gone);
        redraw();
      }));
      grid.append(el);
    });
  };
  filterBtn.addEventListener('click', () => menu(filterBtn, [
    { label: t('shots.allInstalls'), checked: !filter, onclick: () => { filter = null; redraw(); } },
    'sep',
    ...state.installs.map((i) => ({ label: i.name, checked: filter === i.id, onclick: () => { filter = i.id; redraw(); } })),
  ]));
  const load = () => api.shots.list().then((list) => {
    all = list;
    redraw();
  }).catch(showError);
  load();
  const el = h('div.page',
    h('div.page-head',
      h('div', h('h1', t('shots.title')), count, h('div.row', { style: { marginTop: '10px' } }, filterBtn)),
      h('div.actions',
        btn(t('common.refresh'), { icon: 'refresh', onclick: load }),
        btn(t('shots.folder'), { icon: 'folder', onclick: () => api.app.openFolder('screenshots', filter || state.settings.selectedInstall) }))),
    grid);
  return { el, destroy: () => observer.disconnect() };
}
