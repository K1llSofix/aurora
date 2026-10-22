// The launcher's backdrop: the same night over a mountain lake as Aurora's title screen in game
// (gui/background/AuroraScene.java), redrawn with canvas 2D. Landscape layers are painted once
// per size into offscreen canvases and only moved for parallax; the northern lights are computed
// in a small pixel buffer and scaled up, which also gives them their soft glow.

const WATER = 0.655;
const SQUASH = 0.78;
const SKY = [
  [0, '#03040b'],
  [0.24, '#060a1c'],
  [0.44, '#0c1431'],
  [0.58, '#141f47'],
  [WATER + 0.01, '#1c2b57'],
];

// ------------------------------------------------------------------ noise (Noise.java)

function hash(i, seed) {
  let h = (Math.imul(i, 0x27d4eb2d) + Math.imul(seed, 0x165667b1)) | 0;
  h ^= h >>> 15;
  h = Math.imul(h, 0x85ebca6b);
  h ^= h >>> 13;
  h = Math.imul(h, 0xc2b2ae35);
  h ^= h >>> 16;
  return (h >>> 8) / 16777216;
}

function value(x, seed) {
  const i = Math.floor(x);
  const f = x - i;
  const u = f * f * (3 - 2 * f);
  const a = hash(i, seed);
  return a + (hash(i + 1, seed) - a) * u;
}

function fbm(x, seed, octaves) {
  let sum = 0;
  let amp = 0.5;
  let norm = 0;
  for (let o = 0; o < octaves; o++) {
    sum += value(x, seed + o * 1013) * amp;
    norm += amp;
    x = x * 2.03 + 17.1;
    amp *= 0.5;
  }
  return sum / norm;
}

function ridged(x, seed, octaves) {
  let sum = 0;
  let amp = 0.5;
  let norm = 0;
  for (let o = 0; o < octaves; o++) {
    const n = 1 - Math.abs(value(x, seed + o * 1013) * 2 - 1);
    sum += n * n * amp;
    norm += amp;
    x = x * 2.07 + 31.7;
    amp *= 0.48;
  }
  return sum / norm;
}

function smooth(e0, e1, x) {
  const t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
  return t * t * (3 - 2 * t);
}

// ------------------------------------------------------------------ colour

function rgb(hex) {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function mix(a, b, t) {
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

function css(c, alpha = 1) {
  return `rgba(${c[0] | 0},${c[1] | 0},${c[2] | 0},${alpha})`;
}

// Natural aurora green and violet nudged towards the launcher's teal and violet accents.
const LOW = mix(rgb('#3dffa8'), rgb('#2dd4bf'), 0.3);
const HIGH = mix(rgb('#9d6bff'), rgb('#8b5cf6'), 0.3);
const MIDDLE = mix(LOW, HIGH, 0.5);
const WHITE = [255, 255, 255];

// ------------------------------------------------------------------ silhouettes (Ridge.java)

class Ridge {
  constructor() {
    this.xs = [];
    this.ys = [];
    this.top = Infinity;
  }

  add(x, y) {
    this.xs.push(x);
    this.ys.push(y);
    if (y < this.top) this.top = y;
  }

  yAt(x) {
    const xs = this.xs;
    const n = xs.length;
    if (!n) return 0;
    if (x <= xs[0]) return this.ys[0];
    if (x >= xs[n - 1]) return this.ys[n - 1];
    let lo = 0;
    let hi = n - 1;
    while (hi - lo > 1) {
      const mid = (lo + hi) >> 1;
      if (xs[mid] <= x) lo = mid;
      else hi = mid;
    }
    const span = xs[hi] - xs[lo];
    const t = span <= 0 ? 0 : (x - xs[lo]) / span;
    return this.ys[lo] + (this.ys[hi] - this.ys[lo]) * t;
  }

  /** The silhouette as a closed path down to {@code bottom}; y values above {@code clamp} kept. */
  path(bottom, clamp = Infinity) {
    const p = new Path2D();
    const n = this.xs.length;
    p.moveTo(this.xs[0], bottom);
    for (let i = 0; i < n; i++) p.lineTo(this.xs[i], Math.min(clamp, this.ys[i]));
    p.lineTo(this.xs[n - 1], bottom);
    p.closePath();
    return p;
  }
}

/** Height of a stylised spruce at distance d from its trunk: stacked triangles (tiers). */
function spruce(d, half, height, tiers) {
  d = Math.abs(d);
  if (d >= half) return -1;
  let best = -1;
  for (let k = 0; k < tiers; k++) {
    const frac = (k + 1) / tiers;
    const tierW = half * (0.32 + 0.68 * frac);
    if (d >= tierW) continue;
    const tierTop = height * (1 - k * (0.82 / tiers));
    const tierBottom = height * (1 - frac) * 0.9;
    best = Math.max(best, tierTop - (tierTop - tierBottom) * (d / tierW));
  }
  return best;
}

function forest(ground, trees, x0, x1, step) {
  const out = new Ridge();
  const skyline = (x, near) => {
    let y = ground.yAt(x);
    for (let i = Math.max(0, near - 6); i < Math.min(trees.length, near + 6); i++) {
      const t = trees[i];
      const above = spruce(x - t.x, t.half, t.height, t.tiers);
      if (above >= 0) y = Math.min(y, ground.yAt(t.x) - above);
    }
    return y;
  };
  let next = 0;
  for (let x = x0; x <= x1 + step; x += step) {
    while (next < trees.length && trees[next].x < x) {
      const tree = trees[next++];
      if (tree.x > x - step) out.add(tree.x, skyline(tree.x, next - 1));
    }
    out.add(x, skyline(x, next));
  }
  return out;
}

// ------------------------------------------------------------------ the scene

function layer(w, h) {
  const c = document.createElement('canvas');
  c.width = Math.max(1, Math.ceil(w));
  c.height = Math.max(1, Math.ceil(h));
  return c;
}

export class AuroraBackdrop {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d', { alpha: false });
    this.t = 40 + Math.random() * 400;
    this.mx = 0;
    this.my = 0;
    this.tx = 0;
    this.ty = 0;
    this.motion = true;
    this.running = false;
    this.last = 0;
    // 30 frames a second is plenty for light that drifts this slowly.
    this.fps = 31;
    this.frame = this.frame.bind(this);
    this.resize = this.resize.bind(this);
    window.addEventListener('resize', this.resize);
    window.addEventListener('pointermove', (e) => {
      this.tx = (e.clientX / window.innerWidth) * 2 - 1;
      this.ty = (e.clientY / window.innerHeight) * 2 - 1;
    }, { passive: true });
    document.addEventListener('visibilitychange', () => (document.hidden ? this.stop() : this.start()));
    this.resize();
  }

  setMotion(on) {
    this.motion = on;
    if (on) this.start();
    else {
      this.stop();
      this.draw(0);
    }
  }

  start() {
    if (this.running || !this.motion || document.hidden) return;
    this.running = true;
    this.last = performance.now();
    requestAnimationFrame(this.frame);
  }

  stop() {
    this.running = false;
  }

  frame(now) {
    if (!this.running) return;
    if (now - this.last >= 1000 / this.fps) {
      const dt = Math.min(0.1, (now - this.last) / 1000);
      this.last = now;
      this.draw(dt);
    }
    requestAnimationFrame(this.frame);
  }

  resize() {
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const w = Math.round(window.innerWidth * dpr);
    const h = Math.round(window.innerHeight * dpr);
    if (w === this.w && h === this.h) return;
    this.canvas.width = w;
    this.canvas.height = h;
    this.w = w;
    this.h = h;
    this.dpr = dpr;
    this.build();
    this.draw(0);
  }

  shiftX(depth) {
    return -this.mx * depth * this.h * 0.035;
  }

  shiftY(depth) {
    return -this.my * depth * this.h * 0.014;
  }

  // ---------------------------------------------------------------- static layers

  build() {
    const { w, h } = this;
    const px = this.dpr;
    const m = h * 0.06 * 1.3;
    this.m = m;
    const water = h * WATER;
    this.water = water;
    const step = Math.max(px * 2.5, h / 320);
    const fine = Math.max(px * 1.5, h / 600);

    const far = new Ridge();
    const mid = new Ridge();
    const ground = new Ridge();
    for (let x = -m; x <= w + m + step; x += step) {
      const u = (x - w * 0.5) / h;
      const peaks = Math.pow(ridged(u * 1.45 + 4.2, 11, 6), 1.4);
      const range = 0.68 + 0.32 * fbm(u * 0.45 + 2, 12, 2);
      far.add(x, water - h * (0.05 + 0.3 * peaks * range));
      mid.add(x, water - h * (0.015 + 0.1 * Math.pow(ridged(u * 2.3 + 9, 21, 5), 1.3)));
      ground.add(x, water - h * (0.003 + 0.012 * fbm(u * 3, 31, 3)));
    }
    const trees = [];
    for (let x = -m, k = 0; x < w + m; k++) {
      const u = (x - w * 0.5) / h;
      const density = fbm(u * 1.3 + 5, 32, 3);
      const half = h * (0.0055 + 0.005 * hash(k, 33));
      if (density > 0.36) {
        const th = h * (0.014 + 0.03 * hash(k, 34)) * (0.35 + 0.65 * smooth(0.36, 0.6, density));
        trees.push({ x, half, height: th, tiers: 3 });
      }
      x += half * (1.05 + 0.9 * hash(k, 35));
    }
    const shore = forest(ground, trees, -m, w + m, fine);

    const leftGround = new Ridge();
    const rightGround = new Ridge();
    for (let x = -m; x <= w + m + step; x += step) {
      const u = (x - w * 0.5) / h;
      const rough = h * 0.02 * fbm(u * 4 + 1, 41, 3);
      leftGround.add(x, h * 1.02 - h * 0.075 * smooth(w * 0.24, w * 0.06, x) - rough);
      rightGround.add(x, h * 1.02 - h * 0.11 * smooth(w * 0.68, w * 0.86, x) - rough);
    }
    const left = [
      { x: -w * 0.01, half: h * 0.075, height: h * 0.3, tiers: 7 },
      { x: w * 0.05, half: h * 0.05, height: h * 0.17, tiers: 5 },
      { x: w * 0.105, half: h * 0.032, height: h * 0.08, tiers: 4 },
    ];
    const right = [
      { x: w * 0.78, half: h * 0.035, height: h * 0.09, tiers: 4 },
      { x: w * 0.84, half: h * 0.062, height: h * 0.27, tiers: 6 },
      { x: w * 0.905, half: h * 0.088, height: h * 0.43, tiers: 8 },
      { x: w * 0.965, half: h * 0.068, height: h * 0.31, tiers: 7 },
      { x: w + h * 0.03, half: h * 0.075, height: h * 0.36, tiers: 7 },
    ];
    const bankLeft = forest(leftGround, left, -m, w * 0.3, fine);
    const bankRight = forest(rightGround, right, w * 0.62, w + m, fine);

    const farTop = mix(rgb('#34477e'), LOW, 0.1);
    this.layers = {
      base: this.paintBase(),
      milky: this.paintMilkyWay(),
      far: this.paintFill(far, water + 1, far.top, farTop, far.top + h * 0.14, rgb('#0d1434')),
      mid: this.paintFill(mid, water + 1, mid.top, rgb('#141c3d'), water, rgb('#090d20')),
      shore: this.paintFill(shore, water + 1, shore.top, rgb('#05070f'), water, rgb('#04060d')),
      farR: this.paintReflection(far, far.top, mix(farTop, rgb('#0a1230'), 0.45), far.top + h * 0.14, rgb('#0a1029')),
      midR: this.paintReflection(mid, mid.top, rgb('#0b1128'), water, rgb('#070a1a')),
      shoreR: this.paintReflection(shore, shore.top, rgb('#04060d'), water, rgb('#04060d')),
      banks: this.paintBanks(bankLeft, bankRight),
      vignette: this.paintVignette(),
    };
    this.star = this.paintStarSprite();
    this.streak = this.paintStreak();
    this.buildStars();
    this.aurora = layer(Math.ceil(w / 4), Math.ceil(water / 4));
    this.auroraCtx = this.aurora.getContext('2d');
    this.auroraImage = this.auroraCtx.createImageData(this.aurora.width, this.aurora.height);
    this.mistCanvas = layer(160, 24);
    this.mistCtx = this.mistCanvas.getContext('2d');
    this.mistImage = this.mistCtx.createImageData(160, 24);
  }

  paintBase() {
    const { w, h, water } = this;
    const c = layer(w, h);
    const g = c.getContext('2d');
    const sky = g.createLinearGradient(0, 0, 0, h * (WATER + 0.01));
    for (const [stop, color] of SKY) sky.addColorStop(stop / (WATER + 0.01), color);
    g.fillStyle = sky;
    g.fillRect(0, 0, w, water + 2);
    // A faint glow of the lights over the whole sky.
    const glowColor = mix(LOW, HIGH, 0.35);
    let gl = g.createLinearGradient(0, h * 0.1, 0, h * 0.32);
    gl.addColorStop(0, css(glowColor, 0));
    gl.addColorStop(1, css(glowColor, 0.09));
    g.fillStyle = gl;
    g.fillRect(0, h * 0.1, w, h * 0.22);
    gl = g.createLinearGradient(0, h * 0.32, 0, water);
    gl.addColorStop(0, css(glowColor, 0.09));
    gl.addColorStop(1, css(glowColor, 0.036));
    g.fillStyle = gl;
    g.fillRect(0, h * 0.32, w, water - h * 0.32);
    const lake = g.createLinearGradient(0, water, 0, h);
    lake.addColorStop(0, '#121c40');
    lake.addColorStop(1, '#03050b');
    g.fillStyle = lake;
    g.fillRect(0, water, w, h - water);
    return c;
  }

  paintMilkyWay() {
    const { w, h } = this;
    const m = this.m;
    const c = layer(w + 2 * m, h);
    const g = c.getContext('2d');
    g.translate(m, 0);
    const ax = w * 0.98;
    const ay = -h * 0.06;
    const bx = w * 0.12;
    const by = h * 0.66;
    const len = Math.hypot(bx - ax, by - ay);
    const nx = -(by - ay) / len;
    const ny = (bx - ax) / len;
    const color = rgb('#c2bcff');
    const segments = 48;
    g.filter = `blur(${Math.round(h * 0.012)}px)`;
    for (let i = 0; i < segments; i++) {
      const s = (i + 0.5) / segments;
      const x = ax + (bx - ax) * s;
      const y = ay + (by - ay) * s;
      const width = h * (0.045 + 0.05 * fbm(s * 5, 120, 3));
      const a = 0.075 * smooth(0.25, 0.7, fbm(s * 3.5 + 3, 121, 3));
      const grad = g.createLinearGradient(x - nx * width, y - ny * width, x + nx * width, y + ny * width);
      grad.addColorStop(0, css(color, 0));
      grad.addColorStop(0.5, css(color, a));
      grad.addColorStop(1, css(color, 0));
      g.fillStyle = grad;
      const seg = len / segments;
      g.save();
      g.translate(x, y);
      g.rotate(Math.atan2(by - ay, bx - ax));
      g.fillRect(-seg * 0.6, -width, seg * 1.2, width * 2);
      g.restore();
    }
    return c;
  }

  /** A ridge filled downwards with a vertical gradient: hazy valleys take the lower colour. */
  paintFill(ridge, bottom, yTop, top, yLow, low) {
    const m = this.m;
    const y0 = Math.floor(ridge.top) - 2;
    const c = layer(this.w + 2 * m, bottom - y0);
    const g = c.getContext('2d');
    g.translate(m, -y0);
    const grad = g.createLinearGradient(0, yTop, 0, Math.max(yLow, yTop + 1));
    grad.addColorStop(0, css(top));
    grad.addColorStop(1, css(low));
    g.fillStyle = grad;
    g.fill(ridge.path(bottom));
    return { canvas: c, y: y0 };
  }

  /** The mirror image of a ridge in the lake, squashed as seen from above the water. */
  paintReflection(ridge, yTop, top, yLow, low) {
    const { water } = this;
    const m = this.m;
    const depth = (water - ridge.top) * SQUASH + 4;
    const c = layer(this.w + 2 * m, depth);
    const g = c.getContext('2d');
    // y' = water + (water - y) * squash, drawn relative to the water line.
    g.setTransform(1, 0, 0, -SQUASH, m, water * SQUASH);
    const grad = g.createLinearGradient(0, yTop, 0, Math.max(yLow, yTop + 1));
    grad.addColorStop(0, css(top));
    grad.addColorStop(1, css(low));
    g.fillStyle = grad;
    g.fill(ridge.path(water + 1, water));
    return { canvas: c, y: water };
  }

  paintBanks(left, right) {
    const { h } = this;
    const m = this.m;
    const top = Math.floor(Math.min(left.top, right.top)) - 2;
    const c = layer(this.w + 2 * m, h + 4 - top);
    const g = c.getContext('2d');
    g.translate(m, -top);
    const grad = g.createLinearGradient(0, top, 0, h);
    grad.addColorStop(0, '#060914');
    grad.addColorStop(1, '#020307');
    g.fillStyle = grad;
    g.fill(left.path(h + 4));
    g.fill(right.path(h + 4));
    return { canvas: c, y: top };
  }

  paintVignette() {
    const { w, h } = this;
    const c = layer(w, h);
    const g = c.getContext('2d');
    let grad = g.createLinearGradient(0, 0, 0, h * 0.2);
    grad.addColorStop(0, 'rgba(0,0,0,0.35)');
    grad.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = grad;
    g.fillRect(0, 0, w, h * 0.2);
    grad = g.createLinearGradient(0, h * 0.72, 0, h);
    grad.addColorStop(0, 'rgba(0,0,0,0)');
    grad.addColorStop(1, 'rgba(0,0,0,0.55)');
    g.fillStyle = grad;
    g.fillRect(0, h * 0.72, w, h * 0.28 + 1);
    const sw = Math.min(w * 0.16, h * 0.3);
    grad = g.createLinearGradient(0, 0, sw, 0);
    grad.addColorStop(0, 'rgba(0,0,0,0.3)');
    grad.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = grad;
    g.fillRect(0, 0, sw, h);
    grad = g.createLinearGradient(w - sw, 0, w, 0);
    grad.addColorStop(0, 'rgba(0,0,0,0)');
    grad.addColorStop(1, 'rgba(0,0,0,0.3)');
    g.fillStyle = grad;
    g.fillRect(w - sw, 0, sw, h);
    return c;
  }

  paintStarSprite() {
    const c = layer(32, 32);
    const g = c.getContext('2d');
    const grad = g.createRadialGradient(16, 16, 0, 16, 16, 16);
    grad.addColorStop(0, 'rgba(255,255,255,1)');
    grad.addColorStop(0.22, 'rgba(255,255,255,0.85)');
    grad.addColorStop(0.5, 'rgba(255,255,255,0.18)');
    grad.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = grad;
    g.fillRect(0, 0, 32, 32);
    return c;
  }

  /** A soft horizontal streak: ripples on the lake and the glints of bright stars. */
  paintStreak() {
    const c = layer(64, 4);
    const g = c.getContext('2d');
    const grad = g.createLinearGradient(0, 0, 64, 0);
    grad.addColorStop(0, 'rgba(255,255,255,0)');
    grad.addColorStop(0.5, 'rgba(255,255,255,1)');
    grad.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = grad;
    g.fillRect(0, 0, 64, 4);
    return c;
  }

  buildStars() {
    const { w, h } = this;
    const count = Math.max(60, Math.min(420, Math.round(((w * h) / 1e6) * 115)));
    const warm = 'rgba(255,232,200,';
    const cool = 'rgba(207,224,255,';
    this.stars = [];
    for (let i = 0; i < count; i++) {
      const s = hash(i, 9);
      const tint = hash(i, 12);
      this.stars.push({
        u: hash(i, 7),
        v: Math.pow(hash(i, 8), 1.35),
        size: 0.7 + 2.1 * s * s * s,
        phase: hash(i, 10) * 6.2832,
        speed: 0.5 + 2.4 * hash(i, 11),
        tint: tint < 0.16 ? warm : tint < 0.42 ? cool : 'rgba(255,255,255,',
      });
    }
  }

  // ---------------------------------------------------------------- frame

  draw(dt) {
    const { ctx, w, h, water, layers } = this;
    if (!layers) return;
    this.t += dt * (this.motion ? 1 : 0);
    const k = 1 - Math.exp(-dt * 2.5);
    this.mx += (this.tx - this.mx) * k;
    this.my += (this.ty - this.my) * k;
    const m = this.m;

    ctx.globalAlpha = 1;
    ctx.globalCompositeOperation = 'source-over';
    ctx.drawImage(layers.base, 0, 0);
    ctx.drawImage(layers.milky, this.shiftX(0.03) - m, this.shiftY(0.03));
    this.drawStars(this.shiftX(0.04), this.shiftY(0.04));
    this.drawMeteor(this.shiftX(0.04), this.shiftY(0.04));

    this.computeAurora(this.shiftX(0.1), this.shiftY(0.1));
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.globalCompositeOperation = 'lighter';
    if (this.debug !== 'noaurora') ctx.drawImage(this.aurora, 0, 0, w, water);
    ctx.globalCompositeOperation = 'source-over';

    const farX = this.shiftX(0.22);
    const midX = this.shiftX(0.36);
    const shoreX = this.shiftX(0.5);
    this.blit(layers.far, farX);
    this.blit(layers.mid, midX);
    this.blit(layers.shore, shoreX);

    // The lights mirrored in the lake, dimmer and squashed.
    ctx.save();
    ctx.beginPath();
    ctx.rect(0, water, w, h - water);
    ctx.clip();
    ctx.globalAlpha = 0.42;
    ctx.globalCompositeOperation = 'lighter';
    ctx.setTransform(1, 0, 0, -SQUASH, 0, water * (1 + SQUASH));
    ctx.drawImage(this.aurora, 0, 0, w, water);
    ctx.restore();

    this.blit(layers.farR, farX);
    this.blit(layers.midR, midX);
    this.blit(layers.shoreR, shoreX);
    const edge = mix(LOW, WHITE, 0.5);
    const edgeGrad = ctx.createLinearGradient(0, water, 0, water + this.dpr * 1.5);
    edgeGrad.addColorStop(0, css(edge, 0.22));
    edgeGrad.addColorStop(1, css(edge, 0));
    ctx.fillStyle = edgeGrad;
    ctx.fillRect(0, water, w, this.dpr * 1.5);
    this.drawRipples(water, shoreX);
    this.drawMist(water, midX);

    this.blit(layers.banks, this.shiftX(1));
    ctx.drawImage(layers.vignette, 0, 0);
  }

  blit(l, dx) {
    this.ctx.drawImage(l.canvas, dx - this.m, l.y);
  }

  drawStars(dx, dy) {
    const { ctx, w, water, t, star } = this;
    const m = this.m / 1.3;
    const px = this.dpr;
    for (const s of this.stars) {
      const x = s.u * (w + 2 * m) - m + dx;
      const y = s.v * water + dy;
      if (x < -8 || x > w + 8) continue;
      const twinkle = 0.55 + 0.45 * Math.sin(t * s.speed + s.phase);
      const haze = 1 - smooth(0.5, 1, s.v);
      const a = twinkle * haze * Math.min(1, 0.3 + s.size * 0.38);
      if (a <= 0.02) continue;
      const r = Math.max(1.6 * px, s.size * px * 1.9);
      ctx.globalAlpha = a;
      ctx.drawImage(star, x - r, y - r, r * 2, r * 2);
      if (s.size > 2.3) {
        // A thin cross glint on the brightest stars, breathing with the twinkle.
        const gl = s.size * px * 3.2 * twinkle;
        ctx.globalAlpha = a * 0.55;
        ctx.drawImage(this.streak, x - gl, y - px * 0.5, gl * 2, px);
        ctx.save();
        ctx.translate(x, y);
        ctx.rotate(Math.PI / 2);
        ctx.drawImage(this.streak, -gl, -px * 0.5, gl * 2, px);
        ctx.restore();
      }
    }
    ctx.globalAlpha = 1;
  }

  drawMeteor(dx, dy) {
    const { ctx, w, h, t } = this;
    const period = 8.5;
    const k = Math.floor(t / period);
    const start = hash(k, 90) * (period - 1.6);
    const duration = 0.8 + 0.5 * hash(k, 91);
    const p = (t - k * period - start) / duration;
    if (p < 0 || p > 1) return;
    const sx = w * (0.32 + 0.6 * hash(k, 92)) + dx;
    const sy = h * (0.04 + 0.2 * hash(k, 93)) + dy;
    const angle = ((148 + 22 * hash(k, 94)) * Math.PI) / 180;
    const dirX = Math.cos(angle);
    const dirY = Math.sin(angle);
    const travel = h * (0.28 + 0.18 * hash(k, 95));
    const eased = 1 - (1 - p) * (1 - p);
    const hx = sx + dirX * travel * eased;
    const hy = sy + dirY * travel * eased;
    const fade = Math.sin(Math.PI * p);
    const tail = h * 0.16 * fade;
    const tx = hx - dirX * tail;
    const ty = hy - dirY * tail;
    const grad = ctx.createLinearGradient(hx, hy, tx, ty);
    grad.addColorStop(0, `rgba(255,255,255,${fade})`);
    grad.addColorStop(1, 'rgba(191,230,255,0)');
    ctx.strokeStyle = grad;
    ctx.lineCap = 'round';
    ctx.lineWidth = this.dpr * 1.6;
    ctx.beginPath();
    ctx.moveTo(hx, hy);
    ctx.lineTo(tx, ty);
    ctx.stroke();
    const r = this.dpr * 7;
    ctx.globalAlpha = fade * 0.8;
    ctx.drawImage(this.star, hx - r, hy - r, r * 2, r * 2);
    ctx.globalAlpha = 1;
  }

  /**
   * Three ribbons of light, written straight into a quarter-resolution pixel buffer: each column
   * has a bright lower edge with a whitish core on strong rays, fading upwards from green into
   * violet, while rays drift along the ribbon and whole sections brighten and fade.
   */
  computeAurora(dx, dy) {
    const img = this.auroraImage;
    const data = img.data;
    const bw = img.width;
    const bh = img.height;
    const { w, h, t } = this;
    const scale = w / bw;
    data.fill(0);
    for (let r = 0; r < 3; r++) {
      const strength = r === 0 ? 1 : r === 1 ? 0.78 : 0.58;
      for (let i = 0; i < bw; i++) {
        const x = i * scale;
        const u = (x - dx - w * 0.5) / h;
        const wave = Math.sin(u * (1.7 + 0.45 * r) + t * (0.05 + 0.025 * r) + r * 2.1);
        const fold = Math.sin(u * (6.2 + r) + t * (0.11 + 0.03 * r) + r * 1.3);
        const drift = fbm(u * 1.2 + t * 0.02 + r * 7.3, 40 + r, 3) - 0.5;
        const base = h * (0.25 + 0.07 * r) - h * 0.07 * u + h * 0.045 * wave + h * 0.022 * fold + h * 0.06 * drift + dy;
        const section = smooth(0.36, 0.68, fbm(u * 0.8 - t * 0.016 + r * 3.7, 50 + r, 3));
        const ray = fbm(u * 15 + t * (r % 2 === 0 ? 0.2 : -0.16), 60 + r, 3);
        const flicker = 0.88 + 0.12 * Math.sin(t * 1.1 + u * 7 + r);
        const light = Math.min(1, section * (0.35 + 0.8 * ray) * flicker * strength * 1.15);
        if (light < 0.004) continue;
        const height = h * (0.1 + 0.13 * fbm(u * 2.2 + r * 5 + t * 0.03, 70 + r, 2)) * (0.55 + 0.65 * ray);
        // The lower edge whitens on strong rays.
        const er = LOW[0] + (255 - LOW[0]) * 0.32 * ray;
        const eg = LOW[1] + (255 - LOW[1]) * 0.32 * ray;
        const eb = LOW[2] + (255 - LOW[2]) * 0.32 * ray;
        const top = base - height;
        const knee = base - height * 0.3;
        const under = base + h * 0.011;
        const y0 = Math.max(0, Math.floor(top / scale));
        const y1 = Math.min(bh - 1, Math.ceil(under / scale));
        for (let y = y0; y <= y1; y++) {
          const py = y * scale;
          let cr;
          let cg;
          let cb;
          let a;
          if (py < knee) {
            // Violet top fading into the middle colour.
            const f = Math.max(0, (py - top) / (knee - top));
            cr = HIGH[0] + (MIDDLE[0] - HIGH[0]) * f;
            cg = HIGH[1] + (MIDDLE[1] - HIGH[1]) * f;
            cb = HIGH[2] + (MIDDLE[2] - HIGH[2]) * f;
            a = light * 0.48 * f;
          } else if (py < base) {
            const f = (py - knee) / (base - knee);
            cr = MIDDLE[0] + (er - MIDDLE[0]) * f;
            cg = MIDDLE[1] + (eg - MIDDLE[1]) * f;
            cb = MIDDLE[2] + (eb - MIDDLE[2]) * f;
            a = light * (0.48 + 0.52 * f);
          } else {
            const f = Math.min(1, (py - base) / (under - base));
            cr = er + (LOW[0] - er) * f;
            cg = eg + (LOW[1] - eg) * f;
            cb = eb + (LOW[2] - eb) * f;
            a = light * (1 - f);
          }
          if (a <= 0) continue;
          const o = (y * bw + i) * 4;
          // Additive light: overlapping ribbons brighten each other, as real aurora does.
          data[o] = Math.min(255, data[o] + cr * a);
          data[o + 1] = Math.min(255, data[o + 1] + cg * a);
          data[o + 2] = Math.min(255, data[o + 2] + cb * a);
          data[o + 3] = 255;
        }
      }
    }
    this.auroraCtx.putImageData(img, 0, 0);
  }

  /** Thin light and dark streaks that break up the mirror like a breath of wind. */
  drawRipples(water, dx) {
    const { ctx, w, h, t } = this;
    const px = this.dpr;
    const m = this.m / 1.3;
    const light = mix(LOW, WHITE, 0.55);
    for (let i = 0; i < 70; i++) {
      const depth = Math.pow(hash(i, 100), 1.6);
      const y = water + (h - water) * (0.015 + 0.985 * depth);
      const len = h * (0.015 + 0.09 * hash(i, 101)) * (0.35 + 0.65 * depth);
      const x = hash(i, 102) * (w + 2 * m) - m + dx * (0.6 + 0.4 * depth) + Math.sin(t * 0.3 + i) * h * 0.012;
      const pulse = 0.5 + 0.5 * Math.sin(t * (0.5 + hash(i, 103)) + i * 1.7);
      const thick = px * (1 + 1.6 * depth);
      if (hash(i, 104) < 0.4) {
        ctx.globalAlpha = 0.3 * (0.5 + 0.5 * pulse);
        ctx.drawImage(this.streakOf([0, 0, 0]), x - len / 2, y, len, thick);
      } else {
        ctx.globalAlpha = (0.05 + 0.13 * pulse) * (0.45 + 0.55 * depth);
        ctx.globalCompositeOperation = 'lighter';
        ctx.drawImage(this.streakOf(light), x - len / 2, y, len, thick);
        ctx.globalCompositeOperation = 'source-over';
      }
    }
    ctx.globalAlpha = 1;
  }

  streakOf(color) {
    const key = color.join(',');
    this.streaks = this.streaks || new Map();
    if (!this.streaks.has(key)) {
      const c = layer(64, 4);
      const g = c.getContext('2d');
      const grad = g.createLinearGradient(0, 0, 64, 0);
      grad.addColorStop(0, css(color, 0));
      grad.addColorStop(0.5, css(color, 1));
      grad.addColorStop(1, css(color, 0));
      g.fillStyle = grad;
      g.fillRect(0, 0, 64, 4);
      this.streaks.set(key, c);
    }
    return this.streaks.get(key);
  }

  /** Two slow banks of mist hugging the water line, drifting in opposite directions. */
  drawMist(water, dx) {
    const { ctx, w, h, t } = this;
    const m = this.m / 1.3;
    const tint = mix(rgb('#b4c3ea'), LOW, 0.25);
    const img = this.mistImage;
    const data = img.data;
    const cols = img.width;
    const rows = img.height;
    for (let band = 0; band < 2; band++) {
      const dir = band === 0 ? 1 : -1;
      data.fill(0);
      for (let i = 0; i < cols; i++) {
        const x = -m + (i / (cols - 1)) * (w + 2 * m);
        const u = (x - w * 0.5) / h;
        const density = 0.15 * smooth(0.3, 0.75, fbm(u * 1.8 + t * 0.02 * dir + band * 4, 80 + band, 3));
        for (let y = 0; y < rows; y++) {
          const v = 1 - Math.abs((y + 0.5) / rows * 2 - 1);
          const o = (y * cols + i) * 4;
          data[o] = tint[0];
          data[o + 1] = tint[1];
          data[o + 2] = tint[2];
          data[o + 3] = 255 * density * v;
        }
      }
      this.mistCtx.putImageData(img, 0, 0);
      const y = water - h * 0.01 + band * h * 0.022;
      const half = h * (0.016 + 0.006 * band);
      ctx.drawImage(this.mistCanvas, -m + dx, y - half, w + 2 * m, half * 2);
    }
  }
}
