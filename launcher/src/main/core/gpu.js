'use strict';

// Which graphics card Windows gives the game: the same per-program choice as Settings → System →
// Display → Graphics, stored for the Java executable that runs the game. Laptops often start Java
// on the slow built-in GPU; "high" asks for the discrete one.

const { execFile } = require('child_process');

const KEY = 'HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences';
const VALUES = { saving: 'GpuPreference=1;', high: 'GpuPreference=2;' };

function reg(args) {
  return new Promise((resolve) => {
    execFile('reg.exe', args, { windowsHide: true }, (error) => resolve(!error));
  });
}

/**
 * Sets the preference for one executable: "high", "saving", or "auto" to let Windows decide
 * again. Only an entry the launcher wrote itself is removed ({@code ours}).
 */
async function setPreference(exe, mode, ours) {
  if (process.platform !== 'win32' || !exe) return false;
  if (mode === 'auto') {
    return ours ? reg(['delete', KEY, '/v', exe, '/f']) : true;
  }
  const value = VALUES[mode];
  if (!value) return false;
  return reg(['add', KEY, '/v', exe, '/t', 'REG_SZ', '/d', value, '/f']);
}

module.exports = { setPreference };
