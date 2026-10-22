// Skin editor for offline accounts: a 3D player model you can turn and paint on, the flat 64×64
// texture for precise work, a MineSkin gallery and "skin by player name". Saved skins are shown
// in game by the Aurora mod (only on your screen; servers keep their own skin rules).

import { h, fill, append, btn, iconBtn, clear, toast, modal, segmented } from './dom.js';
import { icon } from './icons.js';
import { t, errorText } from './i18n.js';
import { api, showError } from './store.js';

const TEX = 64;
const SCALE = 9; // CSS pixels per texel on the 3D model
const FLAT = 5; // CSS pixels per texel on the flat texture

/** A cuboid: size in texels and where its faces sit on the skin texture (standard layout). */
function box(id, w, hgt, d, u, v, pos, layer, pivot) {
  return { id, w, h: hgt, d, u, v, pos, layer, pivot };
}

/** The player model: base parts and their outer layer, in texels, body centre at the origin. */
function parts(slim) {
  const aw = slim ? 3 : 4;
  const ax = 4 + aw / 2;
  return [
    box('head', 8, 8, 8, 0, 0, [0, -10, 0], 'base'),
    box('hat', 8, 8, 8, 32, 0, [0, -10, 0], 'outer'),
    box('body', 8, 12, 4, 16, 16, [0, 0, 0], 'base'),
    box('jacket', 8, 12, 4, 16, 32, [0, 0, 0], 'outer'),
    box('rightArm', aw, 12, 4, 40, 16, [-ax, 0, 0], 'base', 'rightArm'),
    box('rightSleeve', aw, 12, 4, 40, 32, [-ax, 0, 0], 'outer', 'rightArm'),
    box('leftArm', aw, 12, 4, 32, 48, [ax, 0, 0], 'base', 'leftArm'),
    box('leftSleeve', aw, 12, 4, 48, 48, [ax, 0, 0], 'outer', 'leftArm'),
    box('rightLeg', 4, 12, 4, 0, 16, [-2, 12, 0], 'base', 'rightLeg'),
    box('rightPants', 4, 12, 4, 0, 32, [-2, 12, 0], 'outer', 'rightLeg'),
    box('leftLeg', 4, 12, 4, 16, 48, [2, 12, 0], 'base', 'leftLeg'),
    box('leftPants', 4, 12, 4, 0, 48, [2, 12, 0], 'outer', 'leftLeg'),
  ];
}

/**
 * The six faces of a box: texture rectangle, size on screen and transform. Every face is seen
 * from outside the way Minecraft maps it; only the bottom face is stored upside down.
 */
function faces(b) {
  const { u, v, w, h: hh, d } = b;
  return [
    { name: 'front', rx: u + d, ry: v + d, rw: w, rh: hh, fw: w, fh: hh, t: `translateZ(${(d / 2) * SCALE}px)` },
    { name: 'back', rx: u + 2 * d + w, ry: v + d, rw: w, rh: hh, fw: w, fh: hh, t: `rotateY(180deg) translateZ(${(d / 2) * SCALE}px)` },
    { name: 'right', rx: u, ry: v + d, rw: d, rh: hh, fw: d, fh: hh, t: `rotateY(-90deg) translateZ(${(w / 2) * SCALE}px)` },
    { name: 'left', rx: u + d + w, ry: v + d, rw: d, rh: hh, fw: d, fh: hh, t: `rotateY(90deg) translateZ(${(w / 2) * SCALE}px)` },
    { name: 'top', rx: u + d, ry: v, rw: w, rh: d, fw: w, fh: d, t: `rotateX(90deg) translateZ(${(hh / 2) * SCALE}px)` },
    { name: 'bottom', rx: u + d + w, ry: v, rw: w, rh: d, fw: w, fh: d, t: `rotateX(-90deg) translateZ(${(hh / 2) * SCALE}px)`, flip: true },
  ];
}

const PALETTE = [
  '#000000', '#3b3b3b', '#7a7a7a', '#bcbcbc', '#ffffff', '#5a3825', '#8b5a2b', '#c58c63',
  '#f1c27d', '#ffdbac', '#b02e26', '#f9801d', '#fed83d', '#80c71f', '#5e7c16', '#169c9c',
  '#3ab3da', '#3c44aa', '#8932b8', '#c74ebd', '#f38baa', '#2dd4bf', '#5b8cff', '#9b6cff',
];

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('bad image'));
    img.src = src;
  });
}

/** Turns an old 64×32 skin into the 64×64 layout, mirroring the right limbs like the game does. */
function upgradeLegacy(img) {
  const canvas = document.createElement('canvas');
  canvas.width = TEX;
  canvas.height = TEX;
  const g = canvas.getContext('2d');
  g.drawImage(img, 0, 0);
  const copy = (sx, sy, w, hh, dx, dy) => {
    g.save();
    g.translate(dx + w, dy);
    g.scale(-1, 1);
    g.drawImage(img, sx, sy, w, hh, 0, 0, w, hh);
    g.restore();
  };
  // Left leg from the right leg, left arm from the right arm: each face mirrored in place.
  for (const [sx, dx] of [[0, 16], [40, 32]]) {
    copy(sx + 4, 16, 4, 4, dx + 4, 48);
    copy(sx + 8, 16, 4, 4, dx + 8, 48);
    copy(sx, 20, 4, 12, dx + 8, 52);
    copy(sx + 4, 20, 4, 12, dx + 4, 52);
    copy(sx + 8, 20, 4, 12, dx, 52);
    copy(sx + 12, 20, 4, 12, dx + 12, 52);
  }
  return canvas;
}

/** A simple starting skin for when the game textures are not downloaded yet. */
function templateSkin() {
  const canvas = document.createElement('canvas');
  canvas.width = TEX;
  canvas.height = TEX;
  const g = canvas.getContext('2d');
  const rect = (x, y, w, hh, c) => {
    g.fillStyle = c;
    g.fillRect(x, y, w, hh);
  };
  rect(0, 0, 32, 16, '#c58c63');
  rect(8, 0, 16, 8, '#4a2f1b');
  rect(0, 8, 32, 2, '#4a2f1b');
  rect(9, 12, 2, 1, '#ffffff');
  rect(13, 12, 2, 1, '#ffffff');
  rect(10, 12, 1, 1, '#2b4c8f');
  rect(13, 12, 1, 1, '#2b4c8f');
  rect(16, 16, 24, 16, '#2dd4bf');
  rect(40, 16, 16, 16, '#c58c63');
  rect(40, 20, 16, 4, '#2dd4bf');
  rect(32, 48, 16, 16, '#c58c63');
  rect(32, 52, 16, 4, '#2dd4bf');
  rect(0, 16, 16, 16, '#3c44aa');
  rect(0, 28, 16, 4, '#3b3b3b');
  rect(16, 48, 16, 16, '#3c44aa');
  rect(16, 60, 16, 4, '#3b3b3b');
  return canvas;
}

/** A flat front view of a skin for gallery tiles: base and outer layer of every part. */
export function renderFront(source, slim, scale = 4) {
  // Old 64×32 skins have no left limbs of their own: build them the way the game does.
  const img = source.height === 32 ? upgradeLegacy(source) : source;
  const canvas = document.createElement('canvas');
  canvas.width = 16 * scale;
  canvas.height = 32 * scale;
  const g = canvas.getContext('2d');
  g.imageSmoothingEnabled = false;
  const aw = slim ? 3 : 4;
  const part = (sx, sy, w, hh, dx, dy) => g.drawImage(img, sx, sy, w, hh, dx * scale, dy * scale, w * scale, hh * scale);
  const both = (base, outer, w, hh, dx, dy) => {
    part(base[0], base[1], w, hh, dx, dy);
    if (img.height >= 64 || !outer[2]) part(outer[0], outer[1], w, hh, dx, dy);
  };
  both([8, 8], [40, 8, true], 8, 8, 4, 0);
  both([20, 20], [20, 36], 8, 12, 4, 8);
  both([44, 20], [44, 36], aw, 12, 4 - aw, 8);
  both([36, 52], [52, 52], aw, 12, 12, 8);
  both([4, 20], [4, 36], 4, 12, 4, 20);
  both([20, 52], [4, 52], 4, 12, 8, 20);
  return canvas;
}

/**
 * Opens the editor for an offline account. {@code onSaved} runs after the skin was stored.
 */
export async function skinEditor(account, onSaved) {
  let initial;
  try {
    initial = await api.skins.get(account.id);
  } catch (e) {
    showError(e);
    return;
  }
  const tex = document.createElement('canvas');
  tex.width = TEX;
  tex.height = TEX;
  const tg = tex.getContext('2d', { willReadFrequently: true });
  let slim = Boolean(initial.slim);
  if (initial.dataUrl) {
    const img = await loadImage(initial.dataUrl).catch(() => null);
    if (img) tg.drawImage(img.height === 32 ? upgradeLegacy(img) : img, 0, 0);
  } else {
    tg.drawImage(templateSkin(), 0, 0);
  }

  let tool = 'pencil';
  let layer = 'base';
  let color = '#2dd4bf';
  let showOuter = true;
  let animate = true;
  let paneKey = 'draw';
  const undo = [];
  const redo = [];
  const recent = [];

  const snapshot = () => tg.getImageData(0, 0, TEX, TEX);
  const pushUndo = () => {
    undo.push(snapshot());
    if (undo.length > 60) undo.shift();
    redo.length = 0;
    syncTools();
  };

  // ---------------------------------------------------------------- 3D model

  const stage = h('div.skin-stage');
  const model = h('div.skin-model');
  stage.append(model);
  let rotY = -28;
  let rotX = -12;
  const pivots = {};
  const faceEls = [];

  const buildModel = () => {
    clear(model);
    faceEls.length = 0;
    for (const key of ['rightArm', 'leftArm', 'rightLeg', 'leftLeg']) pivots[key] = null;
    for (const b of parts(slim)) {
      const inflate = b.layer === 'outer' ? (b.id === 'hat' ? 0.5 : 0.25) : 0;
      const el = h(`div.skin-box.${b.layer}`);
      let parent = model;
      let [x, y, z] = b.pos;
      if (b.pivot) {
        // Limbs swing from the shoulder or hip: a pivot at the top of the part.
        if (!pivots[b.pivot]) {
          const pv = h('div.skin-pivot');
          pv.style.transform = `translate3d(${x * SCALE}px, ${(y - b.h / 2) * SCALE}px, ${z * SCALE}px)`;
          model.append(pv);
          pivots[b.pivot] = pv;
        }
        parent = pivots[b.pivot];
        x = 0;
        y = b.h / 2;
        z = 0;
      }
      el.style.transform = `translate3d(${x * SCALE}px, ${y * SCALE}px, ${z * SCALE}px)`;
      for (const f of faces(b)) {
        const fw = (f.fw + inflate * 2) * SCALE;
        const fh = (f.fh + inflate * 2) * SCALE;
        const depth = f.name === 'front' || f.name === 'back' ? b.d : f.name === 'top' || f.name === 'bottom' ? b.h : b.w;
        const transform = f.t.replace(/translateZ\(([\d.]+)px\)/, `translateZ(${(depth / 2 + inflate) * SCALE}px)`);
        const sx = fw / f.rw;
        const sy = fh / f.rh;
        const face = h('div.skin-face', { dataset: { layer: b.layer } });
        Object.assign(face.style, { width: `${fw}px`, height: `${fh}px`, left: `${-fw / 2}px`, top: `${-fh / 2}px`, transform });
        const paint = f.flip ? h('div.skin-flip') : face;
        if (f.flip) {
          face.classList.add('flipped');
          face.append(paint);
        }
        Object.assign(paint.style, {
          backgroundSize: `${TEX * sx}px ${TEX * sy}px`,
          backgroundPosition: `${-f.rx * sx}px ${-f.ry * sy}px`,
        });
        face.uv = { ...f, sx, sy };
        face.addEventListener('pointerdown', (e) => faceDown(e, face));
        face.addEventListener('pointermove', (e) => faceMove(e, face));
        faceEls.push(face);
        el.append(face);
      }
      parent.append(el);
    }
    syncLayers();
    refreshTexture();
  };

  const syncLayers = () => {
    model.classList.toggle('no-outer', !showOuter);
    model.classList.toggle('target-outer', layer === 'outer');
  };

  let pending = false;
  const refreshTexture = () => {
    if (pending) return;
    pending = true;
    requestAnimationFrame(() => {
      pending = false;
      model.style.setProperty('--skin', `url(${tex.toDataURL('image/png')})`);
      drawFlat();
    });
  };

  const applyRotation = () => {
    model.style.transform = `rotateX(${rotX}deg) rotateY(${rotY}deg)`;
  };
  applyRotation();

  // Idle animation: the limbs swing gently unless you are painting.
  let painting = false;
  let raf = 0;
  const swing = (time) => {
    const a = animate && !painting ? Math.sin(time / 520) * 22 : 0;
    const set = (key, angle) => {
      if (pivots[key]) pivots[key].style.transform = pivots[key].style.transform.replace(/ rotateX\([^)]*\)/, '') + ` rotateX(${angle}deg)`;
    };
    set('rightArm', a);
    set('leftArm', -a);
    set('rightLeg', -a);
    set('leftLeg', a);
    raf = requestAnimationFrame(swing);
  };
  raf = requestAnimationFrame(swing);

  // Drag on empty space (or with the right button anywhere) turns the model.
  let rotating = null;
  stage.addEventListener('pointerdown', (e) => {
    if (e.target.closest('.skin-face') && e.button === 0 && paneKey === 'draw') return;
    rotating = { x: e.clientX, y: e.clientY, ry: rotY, rx: rotX };
    stage.setPointerCapture(e.pointerId);
  });
  stage.addEventListener('pointermove', (e) => {
    if (!rotating) return;
    rotY = rotating.ry + (e.clientX - rotating.x) * 0.6;
    rotX = Math.max(-60, Math.min(60, rotating.rx - (e.clientY - rotating.y) * 0.4));
    applyRotation();
  });
  stage.addEventListener('pointerup', () => {
    rotating = null;
  });
  stage.addEventListener('contextmenu', (e) => e.preventDefault());

  // ---------------------------------------------------------------- painting

  const pixel = (x, y) => {
    const d = tg.getImageData(x, y, 1, 1).data;
    return [d[0], d[1], d[2], d[3]];
  };

  const hexToRgb = (hex) => {
    const n = parseInt(hex.slice(1), 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  };

  const rgbToHex = ([r, g, b]) => `#${[r, g, b].map((c) => c.toString(16).padStart(2, '0')).join('')}`;

  /** Flood fill inside one face rectangle: the natural "fill this side" of a skin. */
  const floodFill = (x, y, rect) => {
    const img = snapshot();
    const data = img.data;
    const at = (px, py) => (py * TEX + px) * 4;
    const target = data.slice(at(x, y), at(x, y) + 4);
    const [r, g, b] = hexToRgb(color);
    if (target[0] === r && target[1] === g && target[2] === b && target[3] === 255) return;
    const same = (i) => data[i] === target[0] && data[i + 1] === target[1] && data[i + 2] === target[2] && data[i + 3] === target[3];
    const stack = [[x, y]];
    while (stack.length) {
      const [px, py] = stack.pop();
      if (px < rect.x || py < rect.y || px >= rect.x + rect.w || py >= rect.y + rect.h) continue;
      const i = at(px, py);
      if (!same(i)) continue;
      data[i] = r;
      data[i + 1] = g;
      data[i + 2] = b;
      data[i + 3] = 255;
      stack.push([px + 1, py], [px - 1, py], [px, py + 1], [px, py - 1]);
    }
    tg.putImageData(img, 0, 0);
  };

  const remember = () => {
    if (recent[0] === color) return;
    const i = recent.indexOf(color);
    if (i >= 0) recent.splice(i, 1);
    recent.unshift(color);
    recent.length = Math.min(recent.length, 8);
    renderRecent();
  };

  const paintAt = (x, y, rect) => {
    if (x < 0 || y < 0 || x >= TEX || y >= TEX) return;
    if (tool === 'pipette') {
      const p = pixel(x, y);
      if (p[3] > 0) setColor(rgbToHex(p));
      return;
    }
    if (tool === 'bucket') {
      floodFill(x, y, rect || { x: 0, y: 0, w: TEX, h: TEX });
    } else if (tool === 'eraser') {
      tg.clearRect(x, y, 1, 1);
    } else {
      tg.fillStyle = color;
      tg.clearRect(x, y, 1, 1);
      tg.fillRect(x, y, 1, 1);
    }
    refreshTexture();
  };

  const texelOfFace = (e, face) => {
    const f = face.uv;
    const fx = Math.floor(e.offsetX / f.sx);
    let fy = Math.floor(e.offsetY / f.sy);
    if (f.flip) fy = f.rh - 1 - fy;
    return { x: f.rx + Math.max(0, Math.min(f.rw - 1, fx)), y: f.ry + Math.max(0, Math.min(f.rh - 1, fy)), rect: { x: f.rx, y: f.ry, w: f.rw, h: f.rh } };
  };

  let lastPaint = null;
  const faceDown = (e, face) => {
    if (e.button !== 0 || paneKey !== 'draw') return;
    e.stopPropagation();
    painting = true;
    if (tool !== 'pipette') pushUndo();
    const p = texelOfFace(e, face);
    lastPaint = `${p.x},${p.y}`;
    paintAt(p.x, p.y, p.rect);
    if (tool !== 'pipette') remember();
    if (tool === 'bucket' || tool === 'pipette') painting = false;
  };
  const faceMove = (e, face) => {
    if (!painting || !(e.buttons & 1) || tool === 'bucket' || tool === 'pipette') return;
    const p = texelOfFace(e, face);
    const key = `${p.x},${p.y}`;
    if (key === lastPaint) return;
    lastPaint = key;
    paintAt(p.x, p.y, p.rect);
  };
  window.addEventListener('pointerup', () => {
    painting = false;
  });

  // ---------------------------------------------------------------- flat texture

  const flat = h('canvas.skin-flat', { width: TEX * FLAT, height: TEX * FLAT });
  const fg = flat.getContext('2d');
  let hover = null;
  const REGIONS = () => parts(slim).map((b) => ({ id: b.id, layer: b.layer, x: b.u, y: b.v, w: 2 * (b.w + b.d), h: b.d + b.h }));

  const drawFlat = () => {
    fg.imageSmoothingEnabled = false;
    // Checkerboard where the texture is transparent.
    for (let y = 0; y < TEX; y++) {
      for (let x = 0; x < TEX; x++) {
        fg.fillStyle = (x + y) % 2 ? '#1c2140' : '#161a33';
        fg.fillRect(x * FLAT, y * FLAT, FLAT, FLAT);
      }
    }
    fg.drawImage(tex, 0, 0, TEX * FLAT, TEX * FLAT);
    fg.strokeStyle = 'rgba(255,255,255,0.05)';
    fg.lineWidth = 1;
    fg.beginPath();
    for (let i = 0; i <= TEX; i++) {
      fg.moveTo(i * FLAT + 0.5, 0);
      fg.lineTo(i * FLAT + 0.5, TEX * FLAT);
      fg.moveTo(0, i * FLAT + 0.5);
      fg.lineTo(TEX * FLAT, i * FLAT + 0.5);
    }
    fg.stroke();
    for (const r of REGIONS()) {
      const active = r.layer === layer;
      fg.strokeStyle = active ? 'rgba(79,163,247,0.55)' : 'rgba(255,255,255,0.12)';
      fg.strokeRect(r.x * FLAT + 0.5, r.y * FLAT + 0.5, r.w * FLAT - 1, r.h * FLAT - 1);
    }
    if (hover) {
      fg.strokeStyle = '#ffffff';
      fg.strokeRect(hover.x * FLAT + 0.5, hover.y * FLAT + 0.5, FLAT - 1, FLAT - 1);
    }
  };

  const flatTexel = (e) => {
    const r = flat.getBoundingClientRect();
    return { x: Math.floor(((e.clientX - r.left) / r.width) * TEX), y: Math.floor(((e.clientY - r.top) / r.height) * TEX) };
  };
  const regionAt = (x, y) => {
    for (const b of parts(slim)) {
      for (const f of faces(b)) {
        if (x >= f.rx && y >= f.ry && x < f.rx + f.rw && y < f.ry + f.rh) return { x: f.rx, y: f.ry, w: f.rw, h: f.rh };
      }
    }
    return null;
  };
  let flatDown = false;
  flat.addEventListener('pointerdown', (e) => {
    if (e.button !== 0) return;
    const p = flatTexel(e);
    flatDown = true;
    flat.setPointerCapture(e.pointerId);
    if (tool !== 'pipette') pushUndo();
    lastPaint = `${p.x},${p.y}`;
    paintAt(p.x, p.y, regionAt(p.x, p.y));
    if (tool !== 'pipette') remember();
  });
  flat.addEventListener('pointermove', (e) => {
    const p = flatTexel(e);
    hover = p;
    if (flatDown && tool !== 'bucket' && tool !== 'pipette' && `${p.x},${p.y}` !== lastPaint) {
      lastPaint = `${p.x},${p.y}`;
      paintAt(p.x, p.y, regionAt(p.x, p.y));
    } else {
      drawFlat();
    }
  });
  flat.addEventListener('pointerup', () => {
    flatDown = false;
  });
  flat.addEventListener('pointerleave', () => {
    hover = null;
    drawFlat();
  });

  // ---------------------------------------------------------------- tools

  const toolBtns = {};
  const toolBar = h('div.skin-tools',
    ...[['pencil', 'skin.pencil'], ['eraser', 'skin.eraser'], ['bucket', 'skin.bucket'], ['pipette', 'skin.pipette']].map(([key, label]) => {
      const b = iconBtn(key, { tip: t(label), onclick: () => { tool = key; syncTools(); } });
      toolBtns[key] = b;
      return b;
    }),
    h('div.sep'),
    iconBtn('undo', { tip: t('skin.undo'), onclick: () => history(undo, redo) }),
    iconBtn('redo', { tip: t('skin.redo'), onclick: () => history(redo, undo) }));
  const [undoBtn, redoBtn] = toolBar.querySelectorAll('.icon-btn:nth-last-child(-n+2)');

  function history(from, to) {
    if (!from.length) return;
    to.push(snapshot());
    tg.putImageData(from.pop(), 0, 0);
    refreshTexture();
    syncTools();
  }

  function syncTools() {
    for (const [key, b] of Object.entries(toolBtns)) b.classList.toggle('on', key === tool);
    undoBtn.disabled = !undo.length;
    redoBtn.disabled = !redo.length;
    stage.dataset.tool = tool;
  }

  const swatch = h('input.skin-color', { type: 'color', value: color });
  const colorHex = h('input.input.mono', { value: color, maxlength: 7, style: { width: '96px', height: '34px' } });
  const recentRow = h('div.skin-swatches');
  function setColor(hex) {
    color = hex;
    swatch.value = hex;
    colorHex.value = hex;
    if (tool === 'eraser' || tool === 'pipette') tool = 'pencil';
    syncTools();
  }
  swatch.addEventListener('input', () => setColor(swatch.value));
  colorHex.addEventListener('change', () => {
    if (/^#[0-9a-f]{6}$/i.test(colorHex.value.trim())) setColor(colorHex.value.trim().toLowerCase());
    else colorHex.value = color;
  });
  function renderRecent() {
    fill(recentRow, recent.map((c) => h('button.skin-swatch', { type: 'button', style: { background: c }, 'data-tip': c, onclick: () => setColor(c) })));
  }
  const palette = h('div.skin-swatches', PALETTE.map((c) => h('button.skin-swatch', { type: 'button', style: { background: c }, 'data-tip': c, onclick: () => setColor(c) })));

  const layerSeg = segmented([['base', t('skin.layerBase')], ['outer', t('skin.layerOuter')]], layer, (v) => {
    layer = v;
    syncLayers();
    drawFlat();
  });

  const drawTab = h('div.skin-draw',
    h('div.row', { style: { gap: '12px', flexWrap: 'wrap' } }, toolBar, h('div.grow'), layerSeg),
    h('div.row', { style: { gap: '10px', alignItems: 'center' } }, swatch, colorHex, palette),
    h('div.row', { style: { gap: '8px', minHeight: '22px' } }, h('span.dim', { style: { fontSize: '12px' } }, t('skin.recent')), recentRow),
    h('div.skin-flat-wrap', flat),
    h('div.hint.dim', { style: { fontSize: '12px' } }, t('skin.hint')));

  // ---------------------------------------------------------------- loading skins

  const applySkin = async (url, newSlim) => {
    try {
      const img = await loadImage(url);
      if (img.width !== 64 || (img.height !== 64 && img.height !== 32)) throw new Error('size');
      pushUndo();
      tg.clearRect(0, 0, TEX, TEX);
      tg.drawImage(img.height === 32 ? upgradeLegacy(img) : img, 0, 0);
      if (typeof newSlim === 'boolean' && newSlim !== slim) {
        slim = newSlim;
        fill(modelSeg, modelToggle());
        buildModel();
      }
      refreshTexture();
      toast(t('skin.applied'), { icon: 'check', duration: 1800 });
    } catch {
      toast(errorText({ code: 'bad_skin' }), { error: true });
    }
  };

  const galleryGrid = h('div.skin-gallery');
  const galleryMore = h('div', { style: { display: 'flex', justifyContent: 'center', marginTop: '10px' } });
  const gallerySearch = h('input.input', { placeholder: t('skin.gallerySearch') });
  let galleryToken = 0;
  let galleryNext = null;
  const galleryTile = (skin) => {
    const tile = h('button.skin-tile', { type: 'button', 'data-tip': skin.name || t('skin.untitled') }, h('div.skeleton', { style: { width: '48px', height: '96px' } }));
    api.skins.texture(skin.texture).then(async (url) => {
      const img = await loadImage(url);
      fill(tile, renderFront(img, false, 3), h('span.ellipsis', skin.name || '—'));
      tile.onclick = () => applySkin(url);
    }).catch(() => tile.remove());
    return tile;
  };
  const loadGallery = async (reset) => {
    const mine = ++galleryToken;
    if (reset) {
      galleryNext = null;
      fill(galleryGrid, Array.from({ length: 12 }, () => h('div.skin-tile', h('div.skeleton', { style: { width: '48px', height: '96px' } }))));
    }
    clear(galleryMore);
    try {
      const res = await api.skins.gallery({ filter: gallerySearch.value.trim(), after: reset ? '' : galleryNext || '' });
      if (mine !== galleryToken) return;
      if (reset) clear(galleryGrid);
      append(galleryGrid, res.skins.map(galleryTile));
      if (reset && !res.skins.length) galleryGrid.append(h('div.dim', { style: { padding: '30px', gridColumn: '1 / -1', textAlign: 'center' } }, t('mods.noResults')));
      galleryNext = res.next;
      if (galleryNext) galleryMore.append(btn(t('mods.more'), { class: 'sm', icon: 'chevronDown', onclick: () => loadGallery(false) }));
    } catch (e) {
      if (mine === galleryToken) {
        clear(galleryGrid);
        showError(e);
      }
    }
  };
  let galleryTimer = null;
  gallerySearch.addEventListener('input', () => {
    clearTimeout(galleryTimer);
    galleryTimer = setTimeout(() => loadGallery(true), 400);
  });
  const galleryTab = h('div.skin-gallery-tab',
    h('div.input-wrap', icon('search'), gallerySearch),
    galleryGrid,
    galleryMore,
    h('div.hint.dim', { style: { fontSize: '11.5px', marginTop: '8px' } }, t('skin.galleryNote')));

  const nameInput = h('input.input', { placeholder: t('skin.byNamePlaceholder'), maxlength: 16, spellcheck: false });
  const nameResult = h('div.skin-name-result');
  const findByName = async () => {
    const value = nameInput.value.trim();
    if (!value) return;
    fill(nameResult, h('div.row.dim', h('div.spinner'), t('skin.searching')));
    try {
      const res = await api.skins.byName(value);
      const img = await loadImage(res.dataUrl);
      fill(nameResult,
        renderFront(img, res.slim, 4),
        h('div.col', { style: { gap: '8px' } },
          h('div', { style: { fontWeight: 700, fontSize: '16px' } }, res.name),
          h('div.dim', { style: { fontSize: '12.5px' } }, res.slim ? 'Slim' : 'Classic'),
          btn(t('skin.use'), { class: 'primary sm', icon: 'check', onclick: () => applySkin(res.dataUrl, res.slim) })));
    } catch (e) {
      fill(nameResult, h('div.err-box', errorText(e.info)));
    }
  };
  nameInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') findByName();
  });
  const nameTab = h('div.skin-name-tab',
    h('p.muted', { style: { margin: 0, fontSize: '13px' } }, t('skin.byNameText')),
    h('div.row', h('div.input-wrap.grow', icon('user'), nameInput), btn(t('skin.find'), { class: 'primary', icon: 'search', onclick: findByName })),
    nameResult);

  // ---------------------------------------------------------------- layout

  const panes = { draw: drawTab, gallery: galleryTab, name: nameTab };
  const paneWrap = h('div.skin-pane');
  let galleryLoaded = false;
  const showPane = (key) => {
    paneKey = key;
    fill(paneWrap, panes[key]);
    for (const b of tabs.children) b.classList.toggle('on', b.dataset.key === key);
    stage.classList.toggle('painting', key === 'draw');
    if (key === 'gallery' && !galleryLoaded) {
      galleryLoaded = true;
      loadGallery(true);
    }
  };
  const tabs = h('div.tabs', ...[['draw', 'palette', 'skin.tabDraw'], ['gallery', 'image', 'skin.tabGallery'], ['name', 'user', 'skin.tabName']].map(([key, ic, label]) => h('button', { type: 'button', dataset: { key }, onclick: () => showPane(key) }, icon(ic), t(label))));

  const modelSeg = h('div');
  const modelToggle = () => segmented([['classic', 'Classic'], ['slim', 'Slim']], slim ? 'slim' : 'classic', (v) => {
    slim = v === 'slim';
    buildModel();
  });
  fill(modelSeg, modelToggle());

  const viewTools = h('div.skin-view-tools',
    iconBtn('shirt', { tip: t('skin.toggleOuter'), class: 'on', onclick: (e) => { showOuter = !showOuter; e.currentTarget.classList.toggle('on', showOuter); syncLayers(); } }),
    iconBtn('walk', { tip: t('skin.toggleAnim'), class: 'on', onclick: (e) => { animate = !animate; e.currentTarget.classList.toggle('on', animate); } }),
    iconBtn('refresh', { tip: t('skin.resetView'), onclick: () => { rotY = -28; rotX = -12; applyRotation(); } }));

  modal((close) => {
    const save = btn(t('common.save'), {
      class: 'primary',
      icon: 'check',
      onclick: async () => {
        save.disabled = true;
        try {
          await api.skins.save(account.id, tex.toDataURL('image/png'), slim);
          toast(t('skin.saved', { name: account.name }), { icon: 'check' });
          onSaved?.();
          close();
        } catch (e) {
          save.disabled = false;
          showError(e);
        }
      },
    });
    return [
      h('div.modal-head',
        h('div.grow', h('h2', t('skin.title')), h('p', t('skin.subtitle', { name: account.name }))),
        modelSeg,
        iconBtn('x', { onclick: () => close(), tip: t('common.close') })),
      h('div.modal-body.skin-body',
        h('div.skin-left', stage, viewTools, h('div.hint.dim', { style: { fontSize: '11.5px', textAlign: 'center' } }, t('skin.rotateHint'))),
        h('div.skin-right', tabs, paneWrap)),
      h('div.modal-foot',
        btn(t('skin.upload'), {
          icon: 'download',
          onclick: async () => {
            try {
              const url = await api.skins.pick();
              if (url) applySkin(url);
            } catch (e) {
              showError(e);
            }
          },
        }),
        initial.custom ? btn(t('skin.reset'), {
          icon: 'refresh',
          class: 'danger',
          onclick: async () => {
            try {
              await api.skins.reset(account.id);
              toast(t('skin.resetDone'), { icon: 'refresh' });
              onSaved?.();
              close();
            } catch (e) {
              showError(e);
            }
          },
        }) : null,
        h('div.grow'),
        btn(t('common.cancel'), { onclick: () => close() }),
        save),
    ];
  }, {
    wide: true,
    className: 'skin-modal',
    onClose: () => cancelAnimationFrame(raf),
  });

  buildModel();
  showPane('draw');
  syncTools();
}
