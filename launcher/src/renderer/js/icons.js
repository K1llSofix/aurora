// Line icons drawn on a 24px grid (stroke 1.8, round joins) so they match at every size.

const STROKE = 'fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"';

const PATHS = {
  home: '<path d="M3.5 10.5 12 3.5l8.5 7"/><path d="M5.5 9v10.5a1 1 0 0 0 1 1H10v-6h4v6h3.5a1 1 0 0 0 1-1V9"/>',
  layers: '<path d="m12 3.5 8.5 4.75L12 13 3.5 8.25 12 3.5Z"/><path d="m3.5 12.25 8.5 4.75 8.5-4.75"/><path d="m3.5 16.25 8.5 4.75 8.5-4.75"/>',
  server: '<rect x="3.5" y="4" width="17" height="7" rx="2"/><rect x="3.5" y="13" width="17" height="7" rx="2"/><path d="M7.5 7.5h.01M7.5 16.5h.01M11 7.5h2M11 16.5h2"/>',
  box: '<path d="m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Z"/><path d="m4 7.5 8 4.5 8-4.5M12 12v9"/>',
  sliders: '<path d="M4 6.5h9M17 6.5h3M4 12h3M11 12h9M4 17.5h11M19 17.5h1"/><circle cx="15" cy="6.5" r="2"/><circle cx="9" cy="12" r="2"/><circle cx="17" cy="17.5" r="2"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.5M12 7.8h.01"/>',
  play: '<path d="M8 5.6v12.8a1 1 0 0 0 1.52.85l10.2-6.4a1 1 0 0 0 0-1.7L9.52 4.75A1 1 0 0 0 8 5.6Z" fill="currentColor" stroke="none"/>',
  stop: '<rect x="6.5" y="6.5" width="11" height="11" rx="2.5" fill="currentColor" stroke="none"/>',
  chevronDown: '<path d="m6.5 9.5 5.5 5.5 5.5-5.5"/>',
  chevronUp: '<path d="m6.5 14.5 5.5-5.5 5.5 5.5"/>',
  chevronUpDown: '<path d="m8 9.5 4-4 4 4M8 14.5l4 4 4-4"/>',
  chevronLeft: '<path d="m14.5 6.5-5.5 5.5 5.5 5.5"/>',
  chevronRight: '<path d="m9.5 6.5 5.5 5.5-5.5 5.5"/>',
  arrowRight: '<path d="M5 12h14M13 6l6 6-6 6"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  x: '<path d="M6.5 6.5l11 11M17.5 6.5l-11 11"/>',
  minimize: '<path d="M5 12h14"/>',
  maximize: '<rect x="5.5" y="5.5" width="13" height="13" rx="1.5"/>',
  restore: '<rect x="5" y="8.5" width="10.5" height="10.5" rx="1.5"/><path d="M8.5 8.5V6.5A1.5 1.5 0 0 1 10 5h7.5A1.5 1.5 0 0 1 19 6.5V14a1.5 1.5 0 0 1-1.5 1.5h-2"/>',
  folder: '<path d="M3.5 7.5a2 2 0 0 1 2-2h3.8l2 2h7.2a2 2 0 0 1 2 2v7.5a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2V7.5Z"/>',
  chip: '<rect x="6.5" y="6.5" width="11" height="11" rx="2"/><path d="M9.5 3v3.5M14.5 3v3.5M9.5 17.5V21M14.5 17.5V21M3 9.5h3.5M3 14.5h3.5M17.5 9.5H21M17.5 14.5H21"/><rect x="9.5" y="9.5" width="5" height="5" rx="1"/>',
  puzzle: '<path d="M10 4.5a2 2 0 1 1 4 0V6h3.5a1 1 0 0 1 1 1v3.5H17a2 2 0 1 0 0 4h1.5V18a1 1 0 0 1-1 1H14v-1.5a2 2 0 1 0-4 0V19H6.5a1 1 0 0 1-1-1v-3.5H7a2 2 0 1 0 0-4H5.5V7a1 1 0 0 1 1-1H10V4.5Z"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4.5 20.5a7.5 7.5 0 0 1 15 0"/>',
  users: '<circle cx="9" cy="8.5" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 5a3.5 3.5 0 0 1 0 7M18.5 20a6.5 6.5 0 0 0-2.8-5.3"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="m20 20-4.3-4.3"/>',
  download: '<path d="M12 4v11M7 10.5l5 5 5-5M5 20h14"/>',
  trash: '<path d="M4.5 7h15M10 11v6M14 11v6M6.5 7l.9 11.5a2 2 0 0 0 2 1.8h5.2a2 2 0 0 0 2-1.8L17.5 7M9.5 7V4.5h5V7"/>',
  copy: '<rect x="8.5" y="8.5" width="11" height="11" rx="2"/><path d="M15.5 8.5V6.5a2 2 0 0 0-2-2h-7a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h2"/>',
  edit: '<path d="M4.5 19.5h4l10-10-4-4-10 10v4Z"/><path d="m13 7 4 4"/>',
  terminal: '<rect x="3.5" y="4.5" width="17" height="15" rx="2.5"/><path d="m7.5 9.5 3 2.5-3 2.5M13 15h4"/>',
  refresh: '<path d="M19.5 11.5a7.5 7.5 0 1 0-2.2 5.3"/><path d="M19.5 4.5v7h-7"/>',
  check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
  external: '<path d="M14 4.5h5.5V10M19.5 4.5 11 13"/><path d="M17.5 13.5v5a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1v-11a1 1 0 0 1 1-1h5"/>',
  globe: '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.4 2.4 3.5 5.2 3.5 8.5s-1.1 6.1-3.5 8.5c-2.4-2.4-3.5-5.2-3.5-8.5S9.6 5.9 12 3.5Z"/>',
  coffee: '<path d="M5 9h11v5.5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5V9Z"/><path d="M16 10h1.5a2.5 2.5 0 0 1 0 5H16M8.5 3.5v2.5M11.5 3.5v2.5"/>',
  monitor: '<rect x="3.5" y="4.5" width="17" height="11.5" rx="2"/><path d="M8.5 20h7M12 16v4"/>',
  clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  wand: '<path d="m4.5 19.5 10-10M14.5 9.5l2-2M18 3.5v3M16.5 5h3M19.5 10v2M18.5 11h2M10.5 3.5v2M9.5 4.5h2"/>',
  sparkle: '<path d="M12 3.5c.6 4.4 2.1 5.9 6.5 6.5-4.4.6-5.9 2.1-6.5 6.5-.6-4.4-2.1-5.9-6.5-6.5 4.4-.6 5.9-2.1 6.5-6.5Z"/><path d="M18.5 15.5c.3 1.9 1 2.6 2.5 2.9-1.5.3-2.2 1-2.5 2.9-.3-1.9-1-2.6-2.5-2.9 1.5-.3 2.2-1 2.5-2.9Z"/>',
  shield: '<path d="M12 3.5 19 6v5.5c0 4.2-2.9 7.6-7 9-4.1-1.4-7-4.8-7-9V6l7-2.5Z"/><path d="m9 12 2.2 2.2L15.5 10"/>',
  more: '<circle cx="5.5" cy="12" r="1.3" fill="currentColor"/><circle cx="12" cy="12" r="1.3" fill="currentColor"/><circle cx="18.5" cy="12" r="1.3" fill="currentColor"/>',
  logout: '<path d="M14.5 4.5h3a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-3M10 16.5l4.5-4.5L10 7.5M14.5 12H4.5"/>',
  heart: '<path d="M12 19.5s-7.5-4.3-7.5-9.6A4.4 4.4 0 0 1 12 7.2a4.4 4.4 0 0 1 7.5 2.7c0 5.3-7.5 9.6-7.5 9.6Z"/>',
  alert: '<path d="M12 4 21 19.5H3L12 4Z"/><path d="M12 10v4.5M12 17h.01"/>',
  link: '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
  key: '<circle cx="8" cy="15.5" r="4"/><path d="m10.8 12.7 8.7-8.7M16.5 7l2.5 2.5M14 9.5l2 2"/>',
  image: '<rect x="3.5" y="4.5" width="17" height="15" rx="2.5"/><circle cx="9" cy="9.5" r="1.8"/><path d="m4.5 17.5 5-5 3.5 3.5 2.5-2.5 4 4"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M4.6 4.6 6 6M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4 6 18M18 6l1.4-1.4"/>',
  palette: '<path d="M12 3.5a8.5 8.5 0 1 0 0 17c1.2 0 1.8-.8 1.8-1.7 0-1-.8-1.4-.8-2.3 0-1 .8-1.8 1.8-1.8h2a4.2 4.2 0 0 0 4.2-4.2C21 6.9 17 3.5 12 3.5Z"/><circle cx="7.5" cy="11.5" r="1.2" fill="currentColor"/><circle cx="10.5" cy="7.5" r="1.2" fill="currentColor"/><circle cx="15" cy="7.8" r="1.2" fill="currentColor"/>',
  pencil: '<path d="M4.5 19.5h4l10-10-4-4-10 10v4Z"/><path d="m13 7 4 4"/>',
  eraser: '<path d="M8.5 19.5 3.8 14.8a1.5 1.5 0 0 1 0-2.1l8.9-8.9a1.5 1.5 0 0 1 2.1 0l5.3 5.3a1.5 1.5 0 0 1 0 2.1L12 19.5H8.5Z"/><path d="m8 9 7 7M12 19.5h8"/>',
  bucket: '<path d="m5 11 6.5-6.5L19 12l-6.5 6.5L5 11Z"/><path d="M5 11h13.5M20 15s1.5 2 1.5 3a1.5 1.5 0 0 1-3 0c0-1 1.5-3 1.5-3Z"/>',
  pipette: '<path d="m13 6.5 4.5 4.5M15.5 8.5 7 17l-2.5.5L5 15l8.5-8.5"/><path d="M14.5 5.5 16 4a2.1 2.1 0 0 1 3 3l-1.5 1.5"/>',
  undo: '<path d="M9 7.5 4.5 12 9 16.5"/><path d="M4.5 12h10a4.5 4.5 0 0 1 0 9H12"/>',
  redo: '<path d="m15 7.5 4.5 4.5-4.5 4.5"/><path d="M19.5 12h-10a4.5 4.5 0 0 0 0 9H12"/>',
  shirt: '<path d="M8.5 4 4 6.5l1.5 4 2-1V20h9V9.5l2 1 1.5-4L15.5 4a3.5 3.5 0 0 1-7 0Z"/>',
  grid: '<rect x="4" y="4" width="16" height="16" rx="2"/><path d="M4 9.3h16M4 14.7h16M9.3 4v16M14.7 4v16"/>',
  mirror: '<path d="M12 3v18M8.5 7.5 4 12l4.5 4.5M15.5 7.5 20 12l-4.5 4.5"/>',
  walk: '<circle cx="13" cy="4.5" r="2"/><path d="m10 21 2.5-6.5L15 17v4M9 11l2-4h3l2 4 2.5 1M8.5 13.5 11 7"/>',
  grass: '<path d="M4 9.5 12 5l8 4.5v7L12 21l-8-4.5v-7Z"/><path d="M4 9.5 12 14l8-4.5M12 14v7"/><path d="M4 9.5v2.6l2.4 1.2.9-1 1.3 1.6L12 15.6l3.4-1.7 1.3-1.6.9 1 2.4-1.2V9.5"/>',
};

const BRAND = {
  microsoft: '<svg viewBox="0 0 24 24"><rect x="3" y="3" width="8.5" height="8.5" fill="#f25022"/><rect x="12.5" y="3" width="8.5" height="8.5" fill="#7fba00"/><rect x="3" y="12.5" width="8.5" height="8.5" fill="#00a4ef"/><rect x="12.5" y="12.5" width="8.5" height="8.5" fill="#ffb900"/></svg>',
};

let gradientId = 0;

/** The Aurora mark: a teal-to-violet tile with a four-point spark, as drawn in game. */
export function logoSvg() {
  const id = `aurora-grad-${gradientId++}`;
  return `<svg viewBox="0 0 48 48" class="logo"><defs><linearGradient id="${id}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#2dd4bf"/><stop offset="1" stop-color="#8b5cf6"/></linearGradient></defs>`
    + `<rect width="48" height="48" rx="14.4" fill="url(#${id})"/>`
    + '<path d="M24 6.72 27.05 20.95 41.28 24 27.05 27.05 24 41.28 20.95 27.05 6.72 24 20.95 20.95Z" fill="#fff"/>'
    + '<path d="M35.52 7.2 36.54 11.46 40.8 12.48 36.54 13.5 35.52 17.76 34.5 13.5 30.24 12.48 34.5 11.46Z" fill="#fff" opacity=".85"/></svg>';
}

function parse(markup) {
  const tpl = document.createElement('template');
  tpl.innerHTML = markup.trim();
  return tpl.content.firstChild;
}

export function icon(name) {
  if (BRAND[name]) return parse(BRAND[name]);
  return parse(`<svg viewBox="0 0 24 24" ${STROKE}>${PATHS[name] || PATHS.info}</svg>`);
}

export function logo() {
  return parse(logoSvg());
}

// ------------------------------------------------------------------ installation icons

/** Textures from the installed game for installation icons, set once the launcher has them. */
let catalog = [];

export function setIconCatalog(list) {
  catalog = Array.isArray(list) ? list : [];
}

export function iconCatalog() {
  return catalog;
}

const LEGACY = {
  grass: ['#4ade80', '#15803d'],
  fabric: ['#e0c9a6', '#8f7353'],
  stone: ['#9ca3af', '#4b5563'],
  sky: ['#60a5fa', '#4338ca'],
  ember: ['#fb923c', '#be123c'],
};

/** The icon id an installation uses: its own, or the default for its kind. */
export function iconId(inst) {
  if (inst.icon && inst.icon !== 'aurora' && !(inst.icon in LEGACY)) return inst.icon;
  if (inst.icon === 'aurora' || (!inst.icon && inst.aurora)) return 'aurora';
  if (inst.icon in LEGACY) return `color:${inst.icon}`;
  return 'mc:grass';
}

/** A 16×16 canvas with a catalog texture (or the cropped face of an entity texture). */
function pixelCanvas(entry, size = 16) {
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const img = new Image();
  img.onload = () => {
    const g = canvas.getContext('2d');
    g.imageSmoothingEnabled = false;
    const [sx, sy, sw, sh] = entry.crop || [0, 0, Math.min(img.width, img.height), Math.min(img.width, img.height)];
    g.drawImage(img, sx, sy, sw, sh, 0, 0, size, size);
  };
  img.src = entry.src;
  canvas.className = 'pixel';
  return canvas;
}

function colorTile(colors) {
  const id = `inst-grad-${gradientId++}`;
  return parse(`<svg viewBox="0 0 48 48"><defs><linearGradient id="${id}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${colors[0]}"/><stop offset="1" stop-color="${colors[1]}"/></linearGradient></defs>`
    + `<rect width="48" height="48" rx="14" fill="url(#${id})"/>`
    + `<g transform="translate(12 12)" fill="none" stroke="#fff" stroke-width="1.8" stroke-linejoin="round" opacity=".95">${PATHS.box}</g></svg>`);
}

/** An installation icon element by id: the Aurora mark, a game texture, a picture or a colour. */
export function iconById(id) {
  if (id === 'aurora') return logo();
  if (id.startsWith('custom:')) {
    const img = document.createElement('img');
    img.src = `app://launcher/user-icons/${id.slice(7)}`;
    img.alt = '';
    img.className = 'icon-picture';
    return img;
  }
  if (id.startsWith('mc:')) {
    const entry = catalog.find((c) => c.id === id.slice(3));
    if (entry) {
      const tile = document.createElement('div');
      tile.className = 'mc-tile';
      tile.style.background = `linear-gradient(135deg, ${entry.tile[0]}, ${entry.tile[1]})`;
      tile.append(pixelCanvas(entry));
      return tile;
    }
    return colorTile(LEGACY.grass);
  }
  if (id.startsWith('color:')) return colorTile(LEGACY[id.slice(6)] || LEGACY.grass);
  return colorTile(LEGACY.grass);
}

export function installIcon(inst) {
  return iconById(iconId(inst));
}

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error(`image did not load: ${src.slice(0, 60)}`));
    img.src = src;
  });
}

function roundRectPath(g, x, y, w, h, r) {
  g.beginPath();
  g.moveTo(x + r, y);
  g.arcTo(x + w, y, x + w, y + h, r);
  g.arcTo(x + w, y + h, x, y + h, r);
  g.arcTo(x, y + h, x, y, r);
  g.arcTo(x, y, x + w, y, r);
  g.closePath();
}

/**
 * The icon as PNG data URLs in the sizes a Windows .ico needs. Pixel textures are scaled by whole
 * steps so they stay crisp; at 16 px they are drawn bare, as a tile would be mostly border.
 */
export async function iconPngs(inst) {
  const id = iconId(inst);
  const sizes = [16, 24, 32, 48, 64, 128, 256];
  const out = [];
  let source = null;
  let entry = null;
  // As a picture (not inline in the page) an SVG only renders with its namespace.
  if (id === 'aurora') source = await loadImage(`data:image/svg+xml;charset=utf-8,${encodeURIComponent(logoSvg().replace('class="logo"', 'xmlns="http://www.w3.org/2000/svg" width="256" height="256"'))}`);
  else if (id.startsWith('custom:')) source = await loadImage(`app://launcher/user-icons/${id.slice(7)}`);
  else if (id.startsWith('mc:')) {
    entry = catalog.find((c) => c.id === id.slice(3));
    if (entry) source = await loadImage(entry.src);
  }
  for (const size of sizes) {
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const g = canvas.getContext('2d');
    if (entry && source) {
      const [sx, sy, sw, sh] = entry.crop || [0, 0, Math.min(source.width, source.height), Math.min(source.width, source.height)];
      g.imageSmoothingEnabled = false;
      if (size <= 16) {
        g.drawImage(source, sx, sy, sw, sh, 0, 0, size, size);
      } else {
        const grad = g.createLinearGradient(0, 0, size, size);
        grad.addColorStop(0, entry.tile[0]);
        grad.addColorStop(1, entry.tile[1]);
        g.fillStyle = grad;
        roundRectPath(g, 0, 0, size, size, size * 0.24);
        g.fill();
        const scale = Math.max(1, Math.floor((size * 0.7) / sw));
        const d = sw * scale;
        g.drawImage(source, sx, sy, sw, sh, Math.round((size - d) / 2), Math.round((size - d) / 2), d, d);
      }
    } else if (source) {
      g.imageSmoothingQuality = 'high';
      if (id.startsWith('custom:') && size >= 32) {
        roundRectPath(g, 0, 0, size, size, size * 0.22);
        g.clip();
      }
      const s = Math.min(source.width, source.height);
      g.drawImage(source, (source.width - s) / 2, (source.height - s) / 2, s, s, 0, 0, size, size);
    } else {
      const colors = LEGACY[id.slice(6)] || LEGACY.grass;
      const grad = g.createLinearGradient(0, 0, size, size);
      grad.addColorStop(0, colors[0]);
      grad.addColorStop(1, colors[1]);
      g.fillStyle = grad;
      roundRectPath(g, 0, 0, size, size, size * 0.28);
      g.fill();
    }
    out.push(canvas.toDataURL('image/png'));
  }
  return out;
}

/** Squares and shrinks a picture the player chose (any format the browser reads) to a PNG. */
export async function squarePicture(source) {
  // A file the player picked, or a picture already at hand as a (data) URL.
  const url = typeof source === 'string' ? source : URL.createObjectURL(source);
  try {
    const img = await loadImage(url);
    const canvas = document.createElement('canvas');
    canvas.width = 256;
    canvas.height = 256;
    const g = canvas.getContext('2d');
    g.imageSmoothingQuality = 'high';
    const s = Math.min(img.width, img.height);
    g.drawImage(img, (img.width - s) / 2, (img.height - s) / 2, s, s, 0, 0, 256, 256);
    return canvas.toDataURL('image/png');
  } finally {
    if (typeof source !== 'string') URL.revokeObjectURL(url);
  }
}
