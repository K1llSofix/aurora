'use strict';

const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const crypto = require('crypto');
const { once } = require('events');

let userAgent = 'AuroraLauncher/1.0';
// Plain Node fetch until the app is ready; main.js then switches to Electron's network stack,
// which honours the system proxy just like a browser would.
let impl = (url, init) => globalThis.fetch(url, init);

function configure({ fetch, agent } = {}) {
  if (fetch) impl = fetch;
  if (agent) userAgent = agent;
}

class HttpError extends Error {
  constructor(status, url, body = '') {
    super(`HTTP ${status} for ${url}`);
    this.status = status;
    this.url = url;
    this.body = body;
  }
}

/** True for failures worth another try: the network, timeouts and server-side errors. */
function retryable(e) {
  if (e && e.name === 'AbortError' && e.userAbort) return false;
  if (e instanceof HttpError) return e.status >= 500 || e.status === 429 || e.status === 408;
  return !(e && e.permanent);
}

const delay = (ms, signal) => new Promise((resolve, reject) => {
  const t = setTimeout(resolve, ms);
  signal?.addEventListener('abort', () => {
    clearTimeout(t);
    reject(signal.reason);
  }, { once: true });
});

async function withRetries(fn, attempts = 3, signal) {
  let last;
  for (let i = 0; i < attempts; i++) {
    if (signal?.aborted) throw signal.reason;
    try {
      return await fn(i);
    } catch (e) {
      last = e;
      if (signal?.aborted || !retryable(e) || i === attempts - 1) break;
      await delay(500 * Math.pow(3, i), signal);
    }
  }
  throw last;
}

/** Runs one request with a deadline covering the body too; {@code consume} reads the response. */
async function call(url, opts, consume) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(Object.assign(new Error(`Timed out: ${url}`), { name: 'TimeoutError' })), opts.timeout ?? 20000);
  const forward = () => ctrl.abort(opts.signal.reason);
  opts.signal?.addEventListener('abort', forward, { once: true });
  try {
    const res = await impl(url, {
      method: opts.method || 'GET',
      headers: { 'User-Agent': userAgent, ...opts.headers },
      body: opts.body,
      redirect: 'follow',
      signal: ctrl.signal,
    });
    return await consume(res);
  } finally {
    clearTimeout(timer);
    opts.signal?.removeEventListener('abort', forward);
  }
}

function getJson(url, opts = {}) {
  return withRetries(() => call(url, opts, async (res) => {
    if (!res.ok) throw new HttpError(res.status, url, await res.text().catch(() => ''));
    return res.json();
  }), opts.retries ?? 3, opts.signal);
}

function getText(url, opts = {}) {
  return withRetries(() => call(url, opts, async (res) => {
    if (!res.ok) throw new HttpError(res.status, url);
    return res.text();
  }), opts.retries ?? 3, opts.signal);
}

function getBuffer(url, opts = {}) {
  return withRetries(() => call(url, opts, async (res) => {
    if (!res.ok) throw new HttpError(res.status, url);
    return Buffer.from(await res.arrayBuffer());
  }), opts.retries ?? 3, opts.signal);
}

/** A request whose status matters to the caller (sign-in APIs): never throws on 4xx/5xx. */
async function send(url, opts = {}) {
  return call(url, opts, async (res) => {
    const raw = await res.text().catch(() => '');
    let json = {};
    try {
      json = raw ? JSON.parse(raw) : {};
    } catch {
      json = {};
    }
    return { status: res.status, ok: res.ok, json: json && typeof json === 'object' ? json : {}, raw: raw.slice(0, 400) };
  });
}

function postForm(url, form, opts = {}) {
  return send(url, {
    ...opts,
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json', ...opts.headers },
    body: new URLSearchParams(form).toString(),
  });
}

function postJson(url, body, opts = {}) {
  return send(url, {
    ...opts,
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...opts.headers },
    body: JSON.stringify(body),
  });
}

async function sha1File(file) {
  const hash = crypto.createHash('sha1');
  for await (const chunk of fs.createReadStream(file)) hash.update(chunk);
  return hash.digest('hex');
}

/** Checks a file against its expected size, and its SHA-1 too when {@code deep}. */
async function fileMatches(file, { size, sha1 } = {}, deep = false) {
  try {
    const st = await fsp.stat(file);
    if (!st.isFile()) return false;
    if (size != null && st.size !== size) return false;
    if (deep && sha1) return (await sha1File(file)) === sha1.toLowerCase();
    return true;
  } catch {
    return false;
  }
}

/**
 * Streams {@code url} into {@code dest} through a ".part" file, checking size and SHA-1 before the
 * file takes its real name, so an interrupted download never looks complete.
 */
async function download(url, dest, { sha1, size, onBytes, signal, idleTimeout = 30000 } = {}) {
  await fsp.mkdir(path.dirname(dest), { recursive: true });
  const part = dest + '.part';
  const ctrl = new AbortController();
  let idle = null;
  const arm = () => {
    clearTimeout(idle);
    idle = setTimeout(() => ctrl.abort(Object.assign(new Error(`Download stalled: ${url}`), { name: 'TimeoutError' })), idleTimeout);
  };
  const forward = () => ctrl.abort(signal.reason);
  signal?.addEventListener('abort', forward, { once: true });
  let got = 0;
  let out = null;
  let done = false;
  try {
    arm();
    const res = await impl(url, { headers: { 'User-Agent': userAgent }, redirect: 'follow', signal: ctrl.signal });
    if (!res.ok) throw new HttpError(res.status, url);
    const hash = sha1 ? crypto.createHash('sha1') : null;
    out = fs.createWriteStream(part);
    for await (const chunk of res.body) {
      arm();
      const buf = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk.buffer, chunk.byteOffset, chunk.byteLength);
      hash?.update(buf);
      got += buf.length;
      onBytes?.(buf.length);
      if (!out.write(buf)) await once(out, 'drain');
    }
    await new Promise((resolve, reject) => out.end((err) => (err ? reject(err) : resolve())));
    if (size != null && got !== size) {
      throw new Error(`Size mismatch for ${url}: ${got} != ${size}`);
    }
    if (hash && hash.digest('hex') !== sha1.toLowerCase()) {
      throw new Error(`Checksum mismatch for ${url}`);
    }
    await fsp.rename(part, dest);
    done = true;
  } catch (e) {
    if (signal?.aborted) e.userAbort = true;
    throw e;
  } finally {
    clearTimeout(idle);
    signal?.removeEventListener('abort', forward);
    if (!done) {
      // Windows refuses to delete a file that is still open, so wait for the stream to close.
      if (out && !out.closed) await new Promise((resolve) => out.destroy().once('close', resolve));
      // Roll the progress back so a retry does not count the same bytes twice.
      if (got) onBytes?.(-got);
      await fsp.rm(part, { force: true }).catch(() => {});
    }
  }
}

/**
 * Downloads every item with a small worker pool. Items: {url, path, sha1?, size?}. Progress
 * reports files and bytes; the first failure that survives its retries stops the batch.
 */
async function downloadAll(items, { concurrency = 12, onProgress, signal } = {}) {
  const totalBytes = items.reduce((sum, item) => sum + (item.size || 0), 0);
  let doneBytes = 0;
  let doneFiles = 0;
  let next = 0;
  let failure = null;
  let lastReport = 0;
  const report = (force) => {
    const now = Date.now();
    if (!onProgress || (!force && now - lastReport < 80)) return;
    lastReport = now;
    onProgress({ doneFiles, totalFiles: items.length, doneBytes, totalBytes });
  };
  report(true);
  const worker = async () => {
    while (!failure && next < items.length) {
      if (signal?.aborted) throw signal.reason;
      const item = items[next++];
      try {
        await withRetries(() => download(item.url, item.path, {
          sha1: item.sha1,
          size: item.size,
          signal,
          onBytes: (n) => {
            doneBytes += n;
            report(false);
          },
        }), 4, signal);
      } catch (e) {
        failure = failure || e;
        throw e;
      }
      doneFiles++;
      report(false);
    }
  };
  const workers = Array.from({ length: Math.max(1, Math.min(concurrency, items.length)) }, worker);
  const results = await Promise.allSettled(workers);
  report(true);
  const rejected = results.find((r) => r.status === 'rejected');
  if (rejected) throw failure || rejected.reason;
}

module.exports = {
  configure,
  HttpError,
  withRetries,
  getJson,
  getText,
  getBuffer,
  send,
  postForm,
  postJson,
  sha1File,
  fileMatches,
  download,
  downloadAll,
};
