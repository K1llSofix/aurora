'use strict';

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { withZip } = require('./zip');
const { compareVersions } = require('./rules');

/**
 * Installation icons drawn from the game's own textures (read from the installed client jar,
 * nothing is redistributed), plus pictures the player uploads. `crop` cuts a face out of an
 * entity texture.
 */
const CATALOG = [
  { id: 'grass', path: 'textures/block/grass_block_side.png', tile: ['#4f8f3a', '#2c5420'] },
  { id: 'diamond', path: 'textures/item/diamond.png', tile: ['#2fb5c4', '#165a7a'] },
  { id: 'diamond_sword', path: 'textures/item/diamond_sword.png', tile: ['#3aa1d6', '#283f8f'] },
  { id: 'netherite_sword', path: 'textures/item/netherite_sword.png', tile: ['#5a4b5e', '#221a26'] },
  { id: 'diamond_pickaxe', path: 'textures/item/diamond_pickaxe.png', tile: ['#4dc3c9', '#1d5f6e'] },
  { id: 'bow', path: 'textures/item/bow.png', tile: ['#a77b48', '#4f3420'] },
  { id: 'trident', path: 'textures/item/trident.png', tile: ['#3fb9a6', '#185a55'] },
  { id: 'creeper', path: 'textures/entity/creeper/creeper.png', crop: [8, 8, 8, 8], tile: ['#58b84a', '#1f5a1f'] },
  { id: 'tnt', path: 'textures/block/tnt_side.png', tile: ['#e2463a', '#7a1b16'] },
  { id: 'golden_apple', path: 'textures/item/golden_apple.png', tile: ['#f2c94c', '#9a6a12'] },
  { id: 'totem', path: 'textures/item/totem_of_undying.png', tile: ['#e9c46a', '#7d5a1a'] },
  { id: 'ender_pearl', path: 'textures/item/ender_pearl.png', tile: ['#2a9d8f', '#123f45'] },
  { id: 'ender_eye', path: 'textures/item/ender_eye.png', tile: ['#4cae6c', '#1b3d2a'] },
  { id: 'nether_star', path: 'textures/item/nether_star.png', tile: ['#c7b8ff', '#4b3a8f'] },
  { id: 'heart_of_the_sea', path: 'textures/item/heart_of_the_sea.png', tile: ['#3a86ff', '#1a2f80'] },
  { id: 'emerald', path: 'textures/item/emerald.png', tile: ['#3ddc84', '#11653a'] },
  { id: 'redstone', path: 'textures/item/redstone.png', tile: ['#ff5a4f', '#7a1210'] },
  { id: 'enchanted_book', path: 'textures/item/enchanted_book.png', tile: ['#a26bff', '#3d1f80'] },
  { id: 'elytra', path: 'textures/item/elytra.png', tile: ['#8f9bb8', '#363d55'] },
  { id: 'experience_bottle', path: 'textures/item/experience_bottle.png', tile: ['#b7f04a', '#3f7a14'] },
  { id: 'cake', path: 'textures/item/cake.png', tile: ['#f4a6b8', '#8f3d58'] },
  { id: 'crafting_table', path: 'textures/block/crafting_table_front.png', tile: ['#b88a54', '#5a3d1e'] },
  { id: 'diamond_ore', path: 'textures/block/diamond_ore.png', tile: ['#8a9199', '#3a3f46'] },
  { id: 'glowstone', path: 'textures/block/glowstone.png', tile: ['#ffd46b', '#8f5a12'] },
];

class Icons {
  constructor({ iconsDir, versionsDir }) {
    this.dir = iconsDir;
    this.versionsDir = versionsDir;
    this.cache = null;
  }

  /** The texture catalog from the newest installed game jar, or null before the first install. */
  catalog() {
    if (this.cache) return this.cache;
    let ids = [];
    try {
      ids = fs.readdirSync(this.versionsDir).filter((d) => fs.existsSync(path.join(this.versionsDir, d, `${d}.jar`)));
    } catch {
      return null;
    }
    ids.sort((a, b) => compareVersions(b, a));
    for (const id of ids) {
      try {
        const items = withZip(path.join(this.versionsDir, id, `${id}.jar`), (zip) => CATALOG.map((entry) => {
          const data = zip.get(`assets/minecraft/${entry.path}`);
          return data ? { id: entry.id, crop: entry.crop || null, tile: entry.tile, src: `data:image/png;base64,${data.toString('base64')}` } : null;
        }).filter(Boolean));
        if (items.length) {
          this.cache = items;
          return items;
        }
      } catch {
        // A jar still downloading: try an older one.
      }
    }
    return null;
  }

  forget() {
    this.cache = null;
  }

  /** Stores an uploaded picture (already squared to PNG by the interface) and returns its id. */
  saveCustom(dataUrl) {
    const m = /^data:image\/png;base64,([A-Za-z0-9+/=]+)$/.exec(String(dataUrl));
    if (!m) throw new Error('not a png');
    const data = Buffer.from(m[1], 'base64');
    if (data.length > 4 * 1024 * 1024) throw new Error('icon too large');
    const name = `${crypto.createHash('sha1').update(data).digest('hex').slice(0, 16)}.png`;
    fs.mkdirSync(this.dir, { recursive: true });
    fs.writeFileSync(path.join(this.dir, name), data);
    return `custom:${name}`;
  }

  customFile(name) {
    if (!/^[0-9a-f]{16}\.png$/.test(name)) return null;
    return path.join(this.dir, name);
  }

  /**
   * Packs PNG images into a Windows .ico (PNG entries, supported since Vista). The file name
   * carries a hash: Explorer caches shortcut icons by path, so a new picture needs a new file.
   */
  writeIco(key, pngs) {
    const images = pngs
      .map((url) => /^data:image\/png;base64,(.+)$/.exec(url))
      .filter(Boolean)
      .map((m) => Buffer.from(m[1], 'base64'))
      .map((data) => ({ data, size: data.readUInt32BE(16) }))
      .sort((a, b) => a.size - b.size);
    if (!images.length) throw new Error('no icon images');
    const header = Buffer.alloc(6);
    header.writeUInt16LE(0, 0);
    header.writeUInt16LE(1, 2);
    header.writeUInt16LE(images.length, 4);
    const entries = [];
    let offset = 6 + 16 * images.length;
    for (const img of images) {
      const e = Buffer.alloc(16);
      e.writeUInt8(img.size >= 256 ? 0 : img.size, 0);
      e.writeUInt8(img.size >= 256 ? 0 : img.size, 1);
      e.writeUInt8(0, 2);
      e.writeUInt8(0, 3);
      e.writeUInt16LE(1, 4);
      e.writeUInt16LE(32, 6);
      e.writeUInt32LE(img.data.length, 8);
      e.writeUInt32LE(offset, 12);
      offset += img.data.length;
      entries.push(e);
    }
    const ico = Buffer.concat([header, ...entries, ...images.map((i) => i.data)]);
    const hash = crypto.createHash('sha1').update(ico).digest('hex').slice(0, 10);
    const dir = path.join(this.dir, 'shortcuts');
    fs.mkdirSync(dir, { recursive: true });
    // Old icons of the same installation are no longer used by any shortcut.
    for (const old of fs.readdirSync(dir)) {
      if (old.startsWith(`${key}-`) && old !== `${key}-${hash}.ico`) fs.rmSync(path.join(dir, old), { force: true });
    }
    const file = path.join(dir, `${key}-${hash}.ico`);
    fs.writeFileSync(file, ico);
    return file;
  }
}

module.exports = { Icons, CATALOG };
