'use strict';

// Copies the newest Aurora mod build (../build/libs) into resources/mods, which the installer
// ships next to the launcher. Run `gradlew build` in the mod project first.

const fs = require('fs');
const path = require('path');

const LIBS = path.join(__dirname, '..', '..', 'build', 'libs');
const OUT = path.join(__dirname, '..', 'resources', 'mods');

const jars = fs.existsSync(LIBS)
  ? fs.readdirSync(LIBS).filter((n) => /^aurora-.*\.jar$/i.test(n) && !/-(sources|dev)\.jar$/i.test(n))
  : [];
if (!jars.length) {
  console.error(`No Aurora jar in ${LIBS}. Build the mod first: gradlew build`);
  process.exit(1);
}
const newest = jars
  .map((name) => ({ name, time: fs.statSync(path.join(LIBS, name)).mtimeMs }))
  .sort((a, b) => b.time - a.time)[0].name;

fs.mkdirSync(OUT, { recursive: true });
for (const old of fs.readdirSync(OUT)) {
  if (/\.jar$/i.test(old) && old !== newest) fs.rmSync(path.join(OUT, old));
}
fs.copyFileSync(path.join(LIBS, newest), path.join(OUT, newest));
console.log(`Bundled ${newest}`);
