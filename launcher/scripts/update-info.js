'use strict';

// Writes dist/latest.yml for the installer just built: the file the launcher reads from a GitHub
// release to learn about a new version (the same layout electron-builder's own updater uses).

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const root = path.join(__dirname, '..');
const { version } = require(path.join(root, 'package.json'));
const name = `AuroraLauncher-Setup-${version}.exe`;
const file = path.join(root, 'dist', name);
if (!fs.existsSync(file)) {
  console.error(`update-info: ${name} is not in dist/`);
  process.exit(1);
}
const data = fs.readFileSync(file);
const sha512 = crypto.createHash('sha512').update(data).digest('base64');
const yml = [
  `version: ${version}`,
  'files:',
  `  - url: ${name}`,
  `    sha512: ${sha512}`,
  `    size: ${data.length}`,
  `path: ${name}`,
  `sha512: ${sha512}`,
  `releaseDate: '${new Date().toISOString()}'`,
  '',
].join('\n');
fs.writeFileSync(path.join(root, 'dist', 'latest.yml'), yml);
console.log(`update-info: dist/latest.yml for ${version} (${(data.length / 1048576).toFixed(1)} MB)`);
