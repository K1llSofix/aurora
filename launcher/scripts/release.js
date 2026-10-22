'use strict';

// Publishes the installer built by `npm run dist` as a GitHub release of LAUNCHER_REPO, where the
// launchers already out there take their updates from: tag v<version>, with the installer and
// latest.yml attached. Needs a GitHub token that may write the repository's contents in GH_TOKEN.
//   npm run release -- "What changed"

const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const { version } = require(path.join(root, 'package.json'));
const { LAUNCHER_REPO } = require(path.join(root, 'src', 'main', 'defaults.js'));
const token = process.env.GH_TOKEN || process.env.GITHUB_TOKEN;
const notes = process.argv.slice(2).join(' ').trim();

const API = 'https://api.github.com';
const headers = { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json', 'User-Agent': 'aurora-release', 'X-GitHub-Api-Version': '2022-11-28' };

async function call(url, opts, what) {
  const res = await fetch(url, opts);
  if (!res.ok) throw new Error(`${what}: HTTP ${res.status} ${await res.text()}`);
  return res.json();
}

async function main() {
  if (!LAUNCHER_REPO) throw new Error('LAUNCHER_REPO is empty in src/main/defaults.js');
  if (!token) throw new Error(`set GH_TOKEN to a GitHub token that can write to ${LAUNCHER_REPO}`);
  const dist = path.join(root, 'dist');
  const installer = path.join(dist, `AuroraLauncher-Setup-${version}.exe`);
  const latest = path.join(dist, 'latest.yml');
  if (!fs.existsSync(installer) || !fs.existsSync(latest)) throw new Error(`build ${version} first: npm run dist`);
  // latest.yml must describe this very build, or launchers would refuse the download.
  if (!fs.readFileSync(latest, 'utf8').startsWith(`version: ${version}\n`)) throw new Error('dist/latest.yml is from another build: run npm run dist again');
  const files = [installer, latest, `${installer}.blockmap`].filter((f) => fs.existsSync(f));

  // A draft first: launchers only see the release once every file is there.
  const tag = `v${version}`;
  const release = await call(`${API}/repos/${LAUNCHER_REPO}/releases`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ tag_name: tag, name: `Aurora Launcher ${version}`, body: notes || `Aurora Launcher ${version}`, draft: true }),
  }, `creating ${tag}`);
  for (const file of files) {
    const name = path.basename(file);
    const url = release.upload_url.replace(/\{.*\}$/, `?name=${encodeURIComponent(name)}`);
    await call(url, { method: 'POST', headers: { ...headers, 'Content-Type': 'application/octet-stream' }, body: fs.readFileSync(file) }, `uploading ${name}`);
    console.log(`release: uploaded ${name}`);
  }
  const done = await call(`${API}/repos/${LAUNCHER_REPO}/releases/${release.id}`, { method: 'PATCH', headers, body: JSON.stringify({ draft: false }) }, `publishing ${tag}`);
  console.log(`release: ${done.html_url}`);
}

main().catch((e) => {
  console.error(`release: ${e.message}`);
  process.exit(1);
});
