'use strict';

// "What's new": the sections of a CHANGELOG.md ("## 1.2.0 — date") for the versions that came
// since the one the player saw last.

const fs = require('fs');
const { compareVersions } = require('./rules');

/** The sections of a changelog: [{ version, title, body }], in the order of the file. */
function sections(text) {
  const out = [];
  const lines = String(text || '').split(/\r?\n/);
  let current = null;
  for (const line of lines) {
    const m = /^## +(\d+(?:\.\d+)+)\b(.*)$/.exec(line);
    if (m) {
      current = { version: m[1], title: line.slice(3).trim(), body: [] };
      out.push(current);
    } else if (current) {
      current.body.push(line);
    }
  }
  return out.map((s) => ({ ...s, body: s.body.join('\n').trim() }));
}

/** Sections newer than {@code seen} and up to {@code now}, newest first. */
function since(file, seen, now) {
  let text = '';
  try {
    text = fs.readFileSync(file, 'utf8');
  } catch {
    return [];
  }
  return sections(text).filter((s) => compareVersions(s.version, now) <= 0 && (!seen || compareVersions(s.version, seen) > 0));
}

module.exports = { sections, since };
