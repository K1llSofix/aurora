import { errorText } from './i18n.js';
import { toast } from './dom.js';

/**
 * The preload bridge answers {ok, value} or {ok: false, error}; this unwraps it into plain
 * promises that reject with an Error carrying {code, detail, params} as .info.
 */
function unwrap(group) {
  const out = {};
  for (const [key, fn] of Object.entries(group)) {
    if (typeof fn !== 'function') {
      out[key] = unwrap(fn);
    } else if (/^on[A-Z]/.test(key) || key === 'pathOf') {
      out[key] = fn;
    } else {
      out[key] = async (...args) => {
        const r = await fn(...args);
        if (r && r.ok) return r.value;
        const e = new Error((r && r.error && r.error.code) || 'unknown');
        e.info = (r && r.error) || { code: 'unknown' };
        throw e;
      };
    }
  }
  return out;
}

export const api = unwrap(window.aurora);

/** Shows a failed call to the player in their language. */
export function showError(e) {
  const info = e && e.info ? e.info : { code: 'unknown', detail: e && e.message };
  if (info.code === 'cancelled') return;
  toast(errorText(info), { error: true, detail: info.code === 'unknown' || info.code === 'network' ? info.detail : null });
}

// ------------------------------------------------------------------ shared state

export const state = {
  info: null,
  settings: null,
  installs: [],
  accounts: [],
  servers: [],
  pings: new Map(),
  news: null,
  game: { state: 'idle' },
  progress: null,
  logs: [],
  page: 'home',
};

const listeners = new Map();

export function on(key, fn) {
  if (!listeners.has(key)) listeners.set(key, new Set());
  listeners.get(key).add(fn);
  return () => listeners.get(key).delete(fn);
}

export function set(key, value) {
  state[key] = value;
  emit(key);
}

export function emit(key) {
  for (const fn of listeners.get(key) || []) {
    try {
      fn(state[key]);
    } catch (e) {
      console.error(e);
    }
  }
}

export function selectedInstall() {
  return state.installs.find((i) => i.id === state.settings.selectedInstall) || state.installs[0] || null;
}

export function currentAccount() {
  return state.accounts.find((a) => a.selected) || state.accounts[0] || null;
}

export async function refreshInstalls() {
  set('installs', await api.installs.list());
}

/** Pings a server through the main process, sharing results between pages. */
export function ping(address, force = false) {
  const key = address.toLowerCase();
  const known = state.pings.get(key);
  if (!force && known && known.promise) return known.promise;
  const promise = api.servers.ping(address, force).catch(() => ({ online: false }));
  state.pings.set(key, { promise, result: known && known.result });
  promise.then((result) => {
    state.pings.set(key, { promise, result });
    emit('pings');
  });
  return promise;
}

export function pingResult(address) {
  const known = state.pings.get(address.toLowerCase());
  return known ? known.result : undefined;
}
