'use strict';

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { EventEmitter } = require('events');
const net = require('./net');
const { JsonStore } = require('./store');
const { LauncherError } = require('./errors');
const { withZip } = require('./zip');
const { compareVersions } = require('./rules');
const { MICROSOFT_CLIENT_ID } = require('../defaults');

const AUTHORITY = 'https://login.microsoftonline.com/consumers/oauth2/v2.0/';
const SCOPE = 'XboxLive.signin offline_access';
const NAME = /^[A-Za-z0-9_]{3,16}$/;
/** Refresh a Minecraft token that expires within this time. */
const REFRESH_MARGIN = 10 * 60 * 1000;
const DEFAULT_SKINS = ['alex', 'ari', 'efe', 'kai', 'makena', 'noor', 'steve', 'sunny', 'zuri'];

/** The UUID the game gives an offline name: UUID.nameUUIDFromBytes("OfflinePlayer:" + name). */
function offlineUuid(name) {
  const md5 = crypto.createHash('md5').update(`OfflinePlayer:${name}`, 'utf8').digest();
  md5[6] = (md5[6] & 0x0f) | 0x30;
  md5[8] = (md5[8] & 0x3f) | 0x80;
  return dashed(md5.toString('hex'));
}

function dashed(hex) {
  const h = hex.replace(/-/g, '').toLowerCase();
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

/** java.util.UUID#hashCode, which picks a player's default skin. */
function uuidHash(uuid) {
  const hex = uuid.replace(/-/g, '');
  const msb = BigInt.asIntN(64, BigInt(`0x${hex.slice(0, 16)}`));
  const lsb = BigInt.asIntN(64, BigInt(`0x${hex.slice(16)}`));
  const hilo = msb ^ lsb;
  return Number(BigInt.asIntN(32, hilo >> 32n)) ^ Number(BigInt.asIntN(32, hilo));
}

/** The default skin the game shows for a UUID (DefaultPlayerSkin in 26.x): 9 skins × 2 models. */
function defaultSkinFor(uuid) {
  const index = ((uuidHash(uuid) % 18) + 18) % 18;
  return { name: DEFAULT_SKINS[index % 9], slim: index < 9 };
}

function xboxError(code) {
  switch (code) {
    case 2148916233: return 'ms_no_xbox';
    case 2148916235: return 'ms_region';
    case 2148916236:
    case 2148916237: return 'ms_adult';
    case 2148916238: return 'ms_child';
    default: return 'ms_xbox';
  }
}

/**
 * Saved accounts: offline names and Microsoft accounts. Microsoft sign-in uses the OAuth device
 * code flow, so the password is typed on microsoft.com and never reaches the launcher; the
 * refresh token is kept encrypted with the OS key store (DPAPI on Windows).
 *
 * New offline names need a Microsoft sign-in on this computer first ({@code gate}): "owner" when
 * the account owns the game, "microsoft" when Mojang has not approved the launcher yet, which
 * leaves no way to check that. Names saved before the gate existed keep working.
 */
class Accounts extends EventEmitter {
  constructor({ file, versionsDir, cacheDir, protect, unprotect }) {
    super();
    this.store = new JsonStore(file, { selected: null, gate: null, accounts: [] });
    this.versionsDir = versionsDir;
    this.cacheDir = cacheDir;
    this.protect = protect;
    this.unprotect = unprotect;
    this.skinCache = new Map();
  }

  get data() {
    return this.store.data;
  }

  changed() {
    this.store.save();
    this.emit('changed');
  }

  /** Accounts for the interface, without any tokens. */
  list() {
    const selected = this.current();
    return this.data.accounts.map((a) => ({
      id: a.id,
      type: a.type,
      name: a.name,
      uuid: a.uuid,
      skin: a.skin || null,
      added: a.added,
      lastUsed: a.lastUsed || 0,
      selected: Boolean(selected && selected.id === a.id),
      expired: a.type === 'microsoft' && Boolean(a.ms && a.ms.broken),
    }));
  }

  current() {
    const all = this.data.accounts;
    return all.find((a) => a.id === this.data.selected) || all[0] || null;
  }

  select(id) {
    if (!this.data.accounts.some((a) => a.id === id)) return;
    this.data.selected = id;
    this.changed();
  }

  remove(id) {
    this.data.accounts = this.data.accounts.filter((a) => a.id !== id);
    if (this.data.selected === id) this.data.selected = this.data.accounts[0] ? this.data.accounts[0].id : null;
    this.changed();
  }

  offlineUnlocked() {
    return Boolean(this.data.gate);
  }

  addOffline(name) {
    if (!this.offlineUnlocked()) throw new LauncherError('offline_locked');
    name = String(name || '').trim();
    if (!NAME.test(name)) throw new LauncherError('bad_name', name);
    const uuid = offlineUuid(name);
    const id = `offline:${uuid}`;
    if (!this.data.accounts.some((a) => a.id === id)) {
      this.data.accounts.push({ id, type: 'offline', name, uuid, added: Date.now() });
    }
    this.data.selected = id;
    this.changed();
    return id;
  }

  // ------------------------------------------------------------------ Microsoft sign-in

  /**
   * Signs in a Microsoft account. {@code onCode} receives the code to type on the Microsoft page;
   * the promise resolves with the new account id once the player has signed in, or with null when
   * Microsoft and Xbox Live accepted the player but Mojang has not approved the launcher, so there
   * is no account to play on yet.
   */
  async signIn({ onCode, signal }) {
    const start = await net.postForm(`${AUTHORITY}devicecode`, { client_id: MICROSOFT_CLIENT_ID, scope: SCOPE }, { signal });
    if (!start.json.device_code) throw new LauncherError('ms_client_id', start.json.error_description || start.raw);
    const expiresAt = Date.now() + (start.json.expires_in || 900) * 1000;
    onCode({ code: start.json.user_code, url: start.json.verification_uri, expiresAt });
    let interval = Math.max(1, start.json.interval || 5) * 1000;
    let tokens = null;
    while (!tokens) {
      if (Date.now() > expiresAt) throw new LauncherError('ms_code_expired');
      await new Promise((resolve, reject) => {
        const abort = () => {
          clearTimeout(t);
          reject(new LauncherError('cancelled'));
        };
        const t = setTimeout(() => {
          signal?.removeEventListener('abort', abort);
          resolve();
        }, interval);
        if (signal?.aborted) abort();
        else signal?.addEventListener('abort', abort, { once: true });
      });
      const res = await net.postForm(`${AUTHORITY}token`, {
        grant_type: 'urn:ietf:params:oauth:grant-type:device_code',
        client_id: MICROSOFT_CLIENT_ID,
        device_code: start.json.device_code,
      }, { signal });
      if (res.json.access_token) {
        tokens = { access: res.json.access_token, refresh: res.json.refresh_token };
        break;
      }
      switch (res.json.error) {
        case 'authorization_pending':
          break;
        case 'slow_down':
          interval += 5000;
          break;
        case 'authorization_declined':
          throw new LauncherError('ms_declined');
        case 'expired_token':
          throw new LauncherError('ms_code_expired');
        default:
          throw new LauncherError('ms_failed', res.json.error_description || res.raw);
      }
    }
    let session;
    try {
      session = await this.minecraft(tokens.access, signal);
    } catch (e) {
      if (e.code !== 'ms_not_approved') throw e;
      this.data.gate = this.data.gate || 'microsoft';
      this.changed();
      return null;
    }
    this.data.gate = 'owner';
    const id = `microsoft:${session.uuid}`;
    const existing = this.data.accounts.find((a) => a.id === id);
    const account = existing || { id, type: 'microsoft', added: Date.now() };
    Object.assign(account, {
      name: session.name,
      uuid: session.uuid,
      skin: session.skin,
      ms: { refresh: this.protect(tokens.refresh), access: this.protect(session.token), expires: session.expires },
    });
    if (!existing) this.data.accounts.push(account);
    this.data.selected = id;
    this.changed();
    return id;
  }

  /** Microsoft token → Xbox Live → XSTS → Minecraft token and profile. */
  async minecraft(microsoftToken, signal) {
    const xbl = await net.postJson('https://user.auth.xboxlive.com/user/authenticate', {
      Properties: { AuthMethod: 'RPS', SiteName: 'user.auth.xboxlive.com', RpsTicket: `d=${microsoftToken}` },
      RelyingParty: 'http://auth.xboxlive.com',
      TokenType: 'JWT',
    }, { signal });
    if (!xbl.ok) throw new LauncherError('ms_xbox', `xbox live ${xbl.status}`);
    const userHash = xbl.json.DisplayClaims && xbl.json.DisplayClaims.xui && xbl.json.DisplayClaims.xui[0] && xbl.json.DisplayClaims.xui[0].uhs;
    if (!userHash) throw new LauncherError('ms_xbox', 'no user hash');
    const xsts = await net.postJson('https://xsts.auth.xboxlive.com/xsts/authorize', {
      Properties: { SandboxId: 'RETAIL', UserTokens: [xbl.json.Token] },
      RelyingParty: 'rp://api.minecraftservices.com/',
      TokenType: 'JWT',
    }, { signal });
    if (xsts.status === 401) throw new LauncherError(xboxError(Number(xsts.json.XErr)), `xsts ${xsts.json.XErr}`);
    if (!xsts.ok) throw new LauncherError('ms_xbox', `xsts ${xsts.status}`);
    const login = await net.postJson('https://api.minecraftservices.com/authentication/login_with_xbox', {
      identityToken: `XBL3.0 x=${userHash};${xsts.json.Token}`,
    }, { signal });
    if (login.status === 403) throw new LauncherError('ms_not_approved', login.raw);
    if (!login.ok) throw new LauncherError('ms_minecraft', `login_with_xbox ${login.status}`);
    const token = login.json.access_token;
    const expires = Date.now() + (login.json.expires_in || 86400) * 1000;
    const profile = await net.send('https://api.minecraftservices.com/minecraft/profile', {
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
      signal,
    });
    if (profile.status === 404) throw new LauncherError('ms_no_game');
    if (!profile.ok) throw new LauncherError('ms_minecraft', `profile ${profile.status}`);
    const active = (profile.json.skins || []).find((s) => s.state === 'ACTIVE');
    return {
      token,
      expires,
      uuid: dashed(profile.json.id),
      name: profile.json.name,
      skin: active ? { url: active.url.replace(/^http:/, 'https:'), slim: active.variant === 'SLIM' } : null,
    };
  }

  /**
   * What the game needs to start as the selected account, refreshing a Microsoft session that is
   * about to expire.
   */
  async session(id) {
    const account = id ? this.data.accounts.find((a) => a.id === id) : this.current();
    if (!account) throw new LauncherError('no_account');
    account.lastUsed = Date.now();
    if (account.type === 'offline') {
      this.changed();
      return { type: 'offline', name: account.name, uuid: account.uuid, accessToken: '0' };
    }
    const ms = account.ms || {};
    if (!ms.access || !ms.expires || ms.expires - Date.now() < REFRESH_MARGIN) {
      const refresh = ms.refresh ? this.unprotect(ms.refresh) : null;
      if (!refresh) throw new LauncherError('ms_relogin');
      const res = await net.postForm(`${AUTHORITY}token`, { grant_type: 'refresh_token', client_id: MICROSOFT_CLIENT_ID, refresh_token: refresh, scope: SCOPE });
      if (!res.json.access_token) {
        if (res.status === 400) {
          ms.broken = true;
          this.changed();
          throw new LauncherError('ms_relogin', res.json.error_description || '');
        }
        throw new LauncherError('ms_failed', res.raw || `HTTP ${res.status}`);
      }
      const session = await this.minecraft(res.json.access_token);
      account.name = session.name;
      account.skin = session.skin;
      account.ms = {
        refresh: this.protect(res.json.refresh_token || refresh),
        access: this.protect(session.token),
        expires: session.expires,
      };
    }
    this.changed();
    return { type: 'microsoft', name: account.name, uuid: account.uuid, accessToken: this.unprotect(account.ms.access), xuid: '' };
  }

  // ------------------------------------------------------------------ skins

  /** The account's skin as a data: URL, so the interface can cut out the face. */
  async skin(id) {
    const account = this.data.accounts.find((a) => a.id === id);
    if (!account) return null;
    if (account.skin && account.skin.url) {
      const key = account.skin.url;
      if (this.skinCache.has(key)) return this.skinCache.get(key);
      const file = path.join(this.cacheDir, 'skins', `${crypto.createHash('sha1').update(key).digest('hex')}.png`);
      let data = null;
      try {
        data = fs.readFileSync(file);
      } catch {
        try {
          data = await net.getBuffer(key, { timeout: 10000, retries: 1 });
          fs.mkdirSync(path.dirname(file), { recursive: true });
          fs.writeFileSync(file, data);
        } catch {
          data = null;
        }
      }
      if (data) {
        const url = `data:image/png;base64,${data.toString('base64')}`;
        this.skinCache.set(key, url);
        return url;
      }
    }
    return this.defaultSkin(account.uuid);
  }

  /** Reads the default skin straight from an installed game jar (no copies of Mojang's art). */
  defaultSkin(uuid) {
    const { name, slim } = defaultSkinFor(uuid);
    const entry = `assets/minecraft/textures/entity/player/${slim ? 'slim' : 'wide'}/${name}.png`;
    if (this.skinCache.has(entry)) return this.skinCache.get(entry);
    let ids = [];
    try {
      ids = fs.readdirSync(this.versionsDir).filter((d) => fs.existsSync(path.join(this.versionsDir, d, `${d}.jar`)));
    } catch {
      return null;
    }
    ids.sort((a, b) => compareVersions(b, a));
    for (const id of ids) {
      try {
        const data = withZip(path.join(this.versionsDir, id, `${id}.jar`), (zip) => zip.get(entry));
        if (data) {
          const url = `data:image/png;base64,${data.toString('base64')}`;
          this.skinCache.set(entry, url);
          return url;
        }
      } catch {
        // A jar that is still downloading or broken: try the next one.
      }
    }
    return null;
  }
}

module.exports = { Accounts, offlineUuid, defaultSkinFor, NAME };
