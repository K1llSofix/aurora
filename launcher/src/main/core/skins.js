'use strict';

const fs = require('fs');
const path = require('path');
const net = require('./net');
const { LauncherError } = require('./errors');

const TEXTURES = 'https://textures.minecraft.net/texture/';
const MINESKIN = 'https://api.mineskin.org/v2/skins';

function pngSize(data) {
  if (data.length < 24 || data.readUInt32BE(0) !== 0x89504e47) return null;
  return { width: data.readUInt32BE(16), height: data.readUInt32BE(20) };
}

function dataUrl(data) {
  return `data:image/png;base64,${data.toString('base64')}`;
}

/**
 * Skins for offline accounts. The launcher keeps them as <uuid>.png with a <uuid>.json beside it
 * ({"slim": true}); the Aurora mod reads the same folder (passed as -Daurora.skins) and shows the
 * skin in game. Microsoft accounts keep their real skin from Mojang.
 */
class Skins {
  constructor({ dir, cacheDir }) {
    this.dir = dir;
    this.cacheDir = path.join(cacheDir, 'skins');
  }

  file(uuid) {
    if (!/^[0-9a-f-]{32,36}$/i.test(uuid)) throw new LauncherError('bad_file', uuid);
    return path.join(this.dir, `${uuid.toLowerCase()}.png`);
  }

  read(uuid) {
    try {
      const data = fs.readFileSync(this.file(uuid));
      let slim = false;
      try {
        slim = Boolean(JSON.parse(fs.readFileSync(this.file(uuid).replace(/\.png$/, '.json'), 'utf8')).slim);
      } catch {
        slim = false;
      }
      return { dataUrl: dataUrl(data), slim };
    } catch {
      return null;
    }
  }

  save(uuid, url, slim) {
    const m = /^data:image\/png;base64,([A-Za-z0-9+/=]+)$/.exec(String(url));
    if (!m) throw new LauncherError('bad_skin');
    const data = Buffer.from(m[1], 'base64');
    const size = pngSize(data);
    if (!size || size.width !== 64 || size.height !== 64) throw new LauncherError('bad_skin');
    fs.mkdirSync(this.dir, { recursive: true });
    const file = this.file(uuid);
    fs.writeFileSync(file, data);
    fs.writeFileSync(file.replace(/\.png$/, '.json'), JSON.stringify({ slim: Boolean(slim) }));
  }

  remove(uuid) {
    const file = this.file(uuid);
    fs.rmSync(file, { force: true });
    fs.rmSync(file.replace(/\.png$/, '.json'), { force: true });
  }

  /** A texture from Mojang's texture server by its hash, cached on disk. */
  async texture(hash) {
    if (!/^[0-9a-f]{40,80}$/i.test(hash)) throw new LauncherError('bad_skin');
    const file = path.join(this.cacheDir, `${hash}.png`);
    try {
      return dataUrl(fs.readFileSync(file));
    } catch {
      const data = await net.getBuffer(TEXTURES + hash, { timeout: 15000, retries: 2 });
      if (!pngSize(data)) throw new LauncherError('bad_skin');
      fs.mkdirSync(this.cacheDir, { recursive: true });
      fs.writeFileSync(file, data);
      return dataUrl(data);
    }
  }

  /** The skin of any licensed player, looked up by name through Mojang's public API. */
  async byName(name) {
    if (!/^[A-Za-z0-9_]{2,16}$/.test(name)) throw new LauncherError('bad_name', name);
    const profile = await net.send(`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(name)}`, { timeout: 12000 });
    if (profile.status === 204 || profile.status === 404 || !profile.json.id) throw new LauncherError('skin_no_player', name, { name });
    if (!profile.ok) throw new LauncherError('network', `profile ${profile.status}`);
    const session = await net.getJson(`https://sessionserver.mojang.com/session/minecraft/profile/${profile.json.id}`, { timeout: 12000 });
    const prop = (session.properties || []).find((p) => p.name === 'textures');
    if (!prop) throw new LauncherError('skin_no_player', name, { name });
    const textures = JSON.parse(Buffer.from(prop.value, 'base64').toString('utf8')).textures || {};
    const skin = textures.SKIN;
    if (!skin || !skin.url) throw new LauncherError('skin_default', name, { name });
    const hash = skin.url.split('/').pop();
    return { name: session.name, dataUrl: await this.texture(hash), slim: Boolean(skin.metadata && skin.metadata.model === 'slim') };
  }

  /** Skins from the public MineSkin gallery: the newest ones, or those whose name matches. */
  async gallery({ filter = '', after = '' } = {}) {
    const q = new URLSearchParams({ size: '24' });
    if (filter) q.set('filter', filter);
    if (after) q.set('after', after);
    const res = await net.getJson(`${MINESKIN}?${q}`, { timeout: 15000 });
    const seen = new Set();
    const skins = (res.skins || []).filter((s) => s.texture && !seen.has(s.texture) && seen.add(s.texture)).map((s) => ({
      id: s.uuid,
      name: s.name || '',
      texture: s.texture,
      url: `https://mineskin.org/${s.shortId || s.uuid}`,
    }));
    return { skins, next: res.pagination && res.pagination.next ? res.pagination.next.after : null };
  }
}

module.exports = { Skins, pngSize };
