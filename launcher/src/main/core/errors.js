'use strict';

/**
 * A failure the player should read about. {@code code} picks the message in the interface
 * language; {@code detail} goes to the log and the "details" line.
 */
class LauncherError extends Error {
  constructor(code, detail = '', params = {}) {
    super(`${code}${detail ? ': ' + detail : ''}`);
    this.code = code;
    this.detail = String(detail || '');
    this.params = params;
  }
}

/** Turns any error into something that survives IPC: {code, detail, params}. */
function describe(e) {
  if (e instanceof LauncherError) {
    return { code: e.code, detail: e.detail, params: e.params };
  }
  if (e && (e.name === 'AbortError' || e.userAbort)) {
    return { code: 'cancelled', detail: '' };
  }
  const text = e && e.message ? e.message : String(e);
  if (/ENOTFOUND|ECONNREFUSED|ECONNRESET|ETIMEDOUT|EAI_AGAIN|Timed out|stalled|fetch failed|ERR_INTERNET|ERR_NAME|ERR_CONNECTION|ERR_TIMED_OUT|ERR_NETWORK/i.test(text)) {
    return { code: 'network', detail: text };
  }
  if (/ENOSPC/.test(text)) {
    return { code: 'disk_full', detail: text };
  }
  if (/EPERM|EACCES|EBUSY/.test(text)) {
    return { code: 'file_locked', detail: text };
  }
  return { code: 'unknown', detail: text };
}

module.exports = { LauncherError, describe };
