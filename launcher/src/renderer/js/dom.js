import { icon } from './icons.js';
import { t } from './i18n.js';

/**
 * Builds an element: h('div.card.glass', {onclick, title}, child, 'text', [children]).
 * Text always goes in as text, never as HTML, so names and MOTDs from servers are safe.
 */
export function h(tag, props, ...children) {
  const [name, ...classes] = tag.split('.');
  const el = document.createElement(name || 'div');
  if (classes.length) el.className = classes.join(' ');
  if (props && (typeof props !== 'object' || props instanceof Node || Array.isArray(props))) {
    children.unshift(props);
    props = null;
  }
  for (const [key, value] of Object.entries(props || {})) {
    if (value === undefined || value === null || value === false) continue;
    if (key === 'class') el.className = `${el.className} ${value}`.trim();
    else if (key === 'style' && typeof value === 'object') Object.assign(el.style, value);
    else if (key === 'dataset') Object.assign(el.dataset, value);
    else if (key.startsWith('on') && typeof value === 'function') el.addEventListener(key.slice(2).toLowerCase(), value);
    else if (key === 'html') el.innerHTML = value;
    else if (key in el && typeof value !== 'string') el[key] = value;
    else el.setAttribute(key, value === true ? '' : value);
  }
  append(el, children);
  return el;
}

export function append(el, children) {
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return el;
}

export function clear(el) {
  while (el.firstChild) el.firstChild.remove();
  return el;
}

/** Replaces an element's content; arrays are flattened and empty values skipped. */
export function fill(el, ...children) {
  return append(clear(el), children);
}

/** A button with an optional icon: btn('Играть', {icon: 'play', class: 'primary', onclick}). */
export function btn(label, opts = {}) {
  const { icon: ic, class: cls = '', ...rest } = opts;
  return h(`button.btn${cls ? '.' + cls.split(' ').join('.') : ''}`, { type: 'button', ...rest }, ic ? icon(ic) : null, label ? h('span', label) : null);
}

export function iconBtn(name, opts = {}) {
  const { class: cls = '', tip, ...rest } = opts;
  return h(`button.icon-btn${cls ? '.' + cls : ''}`, { type: 'button', 'data-tip': tip, 'aria-label': tip, ...rest }, icon(name));
}

export function toggle(on, onchange, opts = {}) {
  const el = h('button.switch', { type: 'button', role: 'switch', disabled: opts.disabled, 'aria-checked': String(Boolean(on)) });
  if (on) el.classList.add('on');
  el.addEventListener('click', () => {
    const next = !el.classList.contains('on');
    el.classList.toggle('on', next);
    el.setAttribute('aria-checked', String(next));
    onchange(next);
  });
  return el;
}

export function segmented(options, value, onchange) {
  const el = h('div.segmented');
  for (const [key, label] of options) {
    const b = h('button', { type: 'button', class: key === value ? 'on' : null }, label);
    b.addEventListener('click', () => {
      for (const other of el.children) other.classList.toggle('on', other === b);
      onchange(key);
    });
    el.append(b);
  }
  return el;
}

// ------------------------------------------------------------------ overlays

const layer = () => document.getElementById('layer');

export function toast(message, opts = {}) {
  let box = document.getElementById('toasts');
  if (!box) {
    box = h('div', { id: 'toasts' });
    document.body.append(box);
  }
  const el = h(`div.toast${opts.error ? '.error' : ''}`,
    h('div.ic', icon(opts.icon || (opts.error ? 'alert' : 'check'))),
    h('div.grow', h('div.msg', message), opts.detail ? h('div.detail', opts.detail) : null));
  box.append(el);
  const close = () => {
    el.classList.add('out');
    setTimeout(() => el.remove(), 300);
  };
  el.addEventListener('click', close);
  setTimeout(close, opts.duration || (opts.error ? 6000 : 3200));
}

/**
 * Opens a modal dialog. {@code build} gets a close function and returns the dialog's content
 * (head, body, foot). Escape and a click outside close it unless {@code locked}.
 */
export function modal(build, opts = {}) {
  let closed = false;
  const backdrop = h('div.backdrop');
  const box = h(`div.modal${opts.wide ? '.wide' : ''}${opts.className ? '.' + opts.className : ''}`, { role: 'dialog', 'aria-modal': 'true' });
  const close = (result) => {
    if (closed) return;
    closed = true;
    document.removeEventListener('keydown', onKey, true);
    backdrop.classList.add('closing');
    setTimeout(() => backdrop.remove(), 200);
    opts.onClose?.(result);
  };
  const onKey = (e) => {
    if (e.key === 'Escape' && !opts.locked) {
      e.stopPropagation();
      close();
    }
  };
  document.addEventListener('keydown', onKey, true);
  backdrop.addEventListener('mousedown', (e) => {
    if (e.target === backdrop && !opts.locked) close();
  });
  append(box, [build(close)]);
  backdrop.append(box);
  layer().append(backdrop);
  requestAnimationFrame(() => box.querySelector('input:not([type=range]), [autofocus]')?.focus());
  return { close, box };
}

export function modalHead(title, subtitle, close) {
  return h('div.modal-head', h('div.grow', h('h2', title), subtitle ? h('p', subtitle) : null), close ? iconBtn('x', { onclick: () => close(), tip: t('common.close') }) : null);
}

export function confirmDialog({ title, text, confirm, danger }) {
  return new Promise((resolve) => {
    modal((close) => [
      modalHead(title, null, close),
      h('div.modal-body', h('p.muted', { style: { margin: 0 } }, text)),
      h('div.modal-foot',
        btn(t('common.cancel'), { onclick: () => close(false) }),
        btn(confirm, { class: danger ? 'danger' : 'primary', onclick: () => close(true) })),
    ], { onClose: (r) => resolve(Boolean(r)) });
  });
}

let openPopover = null;

/** A floating menu next to {@code anchor}; returns a close function. */
export function popover(anchor, build, opts = {}) {
  openPopover?.();
  const el = h('div.popover', { style: { minWidth: `${opts.minWidth || 240}px` } });
  const close = () => {
    if (!el.isConnected) return;
    el.remove();
    document.removeEventListener('mousedown', outside, true);
    document.removeEventListener('keydown', onKey, true);
    window.removeEventListener('resize', close);
    if (openPopover === close) openPopover = null;
  };
  const outside = (e) => {
    if (!el.contains(e.target) && !anchor.contains(e.target)) close();
  };
  const onKey = (e) => {
    if (e.key === 'Escape') {
      e.stopPropagation();
      close();
    }
  };
  append(el, [build(close)]);
  layer().append(el);
  const a = anchor.getBoundingClientRect();
  const r = el.getBoundingClientRect();
  const gap = 8;
  let top = opts.above ? a.top - r.height - gap : a.bottom + gap;
  if (!opts.above && top + r.height > window.innerHeight - 8) top = a.top - r.height - gap;
  if (opts.above && top < 8) top = a.bottom + gap;
  let left = opts.alignRight ? a.right - r.width : a.left;
  left = Math.max(8, Math.min(window.innerWidth - r.width - 8, left));
  el.style.top = `${Math.max(8, top)}px`;
  el.style.left = `${left}px`;
  el.style.transformOrigin = opts.above ? 'bottom left' : 'top left';
  setTimeout(() => {
    document.addEventListener('mousedown', outside, true);
    document.addEventListener('keydown', onKey, true);
    window.addEventListener('resize', close);
  });
  openPopover = close;
  return close;
}

/** A popover with menu items: [{icon, label, sub, onclick, danger, checked}] or 'sep'. */
export function menu(anchor, items, opts = {}) {
  return popover(anchor, (close) => items.filter(Boolean).map((item) => {
    if (item === 'sep') return h('div.menu-sep');
    if (item.label && item.header) return h('div.menu-label', item.label);
    return h(`button.menu-item${item.danger ? '.danger' : ''}`, {
      type: 'button',
      onclick: () => {
        close();
        item.onclick?.();
      },
    }, item.icon ? (item.icon instanceof Node ? item.icon : icon(item.icon)) : null,
    h('div.grow', h('div.ellipsis', item.label), item.sub ? h('div.sub.ellipsis', item.sub) : null),
    item.checked ? h('span.check', icon('check')) : null);
  }), opts);
}

// Tooltips for anything with data-tip.
let tipEl = null;
let tipTimer = null;
document.addEventListener('mouseover', (e) => {
  const target = e.target.closest?.('[data-tip]');
  clearTimeout(tipTimer);
  tipEl?.remove();
  tipEl = null;
  if (!target || !target.dataset.tip) return;
  tipTimer = setTimeout(() => {
    if (!target.isConnected) return;
    tipEl = h('div.tooltip', target.dataset.tip);
    document.body.append(tipEl);
    const a = target.getBoundingClientRect();
    const r = tipEl.getBoundingClientRect();
    let top = a.top - r.height - 8;
    if (top < 4) top = a.bottom + 8;
    tipEl.style.top = `${top}px`;
    tipEl.style.left = `${Math.max(6, Math.min(window.innerWidth - r.width - 6, a.left + a.width / 2 - r.width / 2))}px`;
  }, 450);
});
document.addEventListener('mousedown', () => {
  clearTimeout(tipTimer);
  tipEl?.remove();
  tipEl = null;
});

// ------------------------------------------------------------------ Minecraft text

const COLORS = {
  black: '#000000', dark_blue: '#0000AA', dark_green: '#00AA00', dark_aqua: '#00AAAA', dark_red: '#AA0000',
  dark_purple: '#AA00AA', gold: '#FFAA00', gray: '#AAAAAA', dark_gray: '#555555', blue: '#5555FF', green: '#55FF55',
  aqua: '#55FFFF', red: '#FF5555', light_purple: '#FF55FF', yellow: '#FFFF55', white: '#FFFFFF',
};
const CODES = '0123456789abcdef';
const CODE_COLORS = Object.values(COLORS);
const OBFUSCATED = 'ABCDEFGHJKLMNOPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz0123456789#$%&';

/** Flattens a chat component (or legacy § string) into styled text runs. */
function runs(component, inherited, out) {
  if (component === null || component === undefined) return out;
  if (typeof component === 'string' || typeof component === 'number') {
    legacy(String(component), inherited, out);
    return out;
  }
  if (Array.isArray(component)) {
    // In a component list the first entry is the parent: the rest inherit its style.
    if (!component.length) return out;
    const parent = styleOf(component[0], inherited);
    runs(component[0], inherited, out);
    for (const child of component.slice(1)) runs(child, parent, out);
    return out;
  }
  const style = styleOf(component, inherited);
  const text = component.text ?? component.translate ?? '';
  legacy(String(text), style, out);
  for (const child of component.extra || []) runs(child, style, out);
  return out;
}

function styleOf(component, inherited) {
  if (!component || typeof component !== 'object' || Array.isArray(component)) return inherited;
  const style = { ...inherited };
  if (component.color) style.color = COLORS[component.color] || (/^#[0-9a-f]{6}$/i.test(component.color) ? component.color : style.color);
  for (const key of ['bold', 'italic', 'underlined', 'strikethrough', 'obfuscated']) {
    if (component[key] !== undefined) style[key] = Boolean(component[key]);
  }
  return style;
}

function legacy(text, style, out) {
  let current = { ...style };
  let buffer = '';
  const flush = () => {
    if (buffer) out.push({ text: buffer, style: current });
    buffer = '';
  };
  for (let i = 0; i < text.length; i++) {
    if (text[i] === '§' && i + 1 < text.length) {
      const code = text[i + 1].toLowerCase();
      flush();
      const ci = CODES.indexOf(code);
      if (ci >= 0) current = { color: CODE_COLORS[ci] };
      else if (code === 'l') current = { ...current, bold: true };
      else if (code === 'o') current = { ...current, italic: true };
      else if (code === 'n') current = { ...current, underlined: true };
      else if (code === 'm') current = { ...current, strikethrough: true };
      else if (code === 'k') current = { ...current, obfuscated: true };
      else if (code === 'r') current = { ...style, color: undefined, bold: false, italic: false, underlined: false, strikethrough: false, obfuscated: false };
      i++;
      continue;
    }
    buffer += text[i];
  }
  flush();
}

/** A server MOTD with Minecraft colours and formatting, built from text nodes only. */
export function motd(component, opts = {}) {
  const el = h('div.motd');
  const parts = runs(component, { color: opts.color || '#AAAAAA' }, []);
  let lines = 0;
  for (const part of parts) {
    let text = part.text;
    if (opts.singleLine) text = text.replace(/\s*\n\s*/g, '  ');
    else if (lines >= 2) break;
    if (!opts.singleLine) {
      const nl = text.split('\n');
      if (lines + nl.length - 1 >= 2) text = nl.slice(0, 2 - lines).join('\n');
      lines += nl.length - 1;
    }
    if (part.style.obfuscated) text = text.replace(/\S/g, () => OBFUSCATED[Math.floor(Math.random() * OBFUSCATED.length)]);
    const span = h('span', text);
    const s = part.style;
    if (s.color) {
      span.style.color = s.color;
      span.style.textShadow = `0.08em 0.08em 0 ${shadow(s.color)}`;
    }
    if (s.bold) span.style.fontWeight = '700';
    if (s.italic) span.style.fontStyle = 'italic';
    const deco = [s.underlined && 'underline', s.strikethrough && 'line-through'].filter(Boolean).join(' ');
    if (deco) span.style.textDecoration = deco;
    el.append(span);
  }
  return el;
}

/** Plain text of a MOTD, without colours: for one-line descriptions. */
export function motdText(component) {
  return runs(component, {}, []).map((p) => p.text).join('').replace(/\s+/g, ' ').trim();
}

function shadow(hex) {
  const n = parseInt(hex.slice(1), 16);
  const r = ((n >> 16) & 255) >> 2;
  const g = ((n >> 8) & 255) >> 2;
  const b = (n & 255) >> 2;
  return `rgba(${r},${g},${b},0.9)`;
}

// ------------------------------------------------------------------ player heads

const faceCache = new Map();

/** A player head (face plus hat layer) cut from a skin, or the name's initial while loading. */
export function head(account, size = '') {
  const el = h(`div.head${size ? '.' + size : ''}`, (account?.name || '?').slice(0, 1).toUpperCase());
  if (!account) return el;
  const cached = faceCache.get(account.id + (account.skin?.url || ''));
  const draw = (url) => {
    if (!url) return;
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = 8;
      canvas.height = 8;
      const g = canvas.getContext('2d');
      g.imageSmoothingEnabled = false;
      g.drawImage(img, 8, 8, 8, 8, 0, 0, 8, 8);
      g.drawImage(img, 40, 8, 8, 8, 0, 0, 8, 8);
      fill(el, canvas);
      el.style.background = 'transparent';
    };
    img.src = url;
  };
  if (cached) {
    cached.then(draw);
  } else {
    const p = window.aurora.accounts.skin(account.id).then((r) => (r.ok ? r.value : null)).catch(() => null);
    faceCache.set(account.id + (account.skin?.url || ''), p);
    p.then(draw);
  }
  return el;
}

export function forgetFaces() {
  faceCache.clear();
}
