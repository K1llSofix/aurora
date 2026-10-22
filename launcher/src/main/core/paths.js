'use strict';

const fs = require('fs');
const path = require('path');
const os = require('os');

/**
 * Everything the launcher keeps lives under one folder, laid out like the official launcher
 * (versions, libraries, assets, runtime) so downloads are shared between installations.
 */
function defaultRoot() {
  if (process.env.AURORA_HOME) {
    return path.resolve(process.env.AURORA_HOME);
  }
  switch (process.platform) {
    case 'win32':
      return path.join(process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming'), '.aurora');
    case 'darwin':
      return path.join(os.homedir(), 'Library', 'Application Support', 'aurora');
    default:
      return path.join(os.homedir(), '.aurora');
  }
}

function create(root = defaultRoot()) {
  // Java canonicalises classpath entries (following junctions and the AppData redirection of
  // packaged Windows apps) and Fabric compares them with the classpath as given, so the two must
  // agree or Mixin gets loaded twice.
  try {
    fs.mkdirSync(root, { recursive: true });
    root = fs.realpathSync.native(root);
  } catch {
    // Keep the path as given; the launcher reports the real problem when it writes there.
  }
  return {
    root,
    versions: path.join(root, 'versions'),
    libraries: path.join(root, 'libraries'),
    assets: path.join(root, 'assets'),
    runtime: path.join(root, 'runtime'),
    natives: path.join(root, 'natives'),
    instances: path.join(root, 'instances'),
    cache: path.join(root, 'cache'),
    logs: path.join(root, 'logs'),
    skins: path.join(root, 'skins'),
    icons: path.join(root, 'icons'),
    settings: path.join(root, 'launcher.json'),
    accounts: path.join(root, 'accounts.json'),
  };
}

module.exports = { defaultRoot, create };
