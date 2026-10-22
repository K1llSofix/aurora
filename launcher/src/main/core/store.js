'use strict';

const fs = require('fs');
const path = require('path');

/** Reads JSON, returning {@code fallback} when the file is missing or broken. */
function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

/** Writes through a temporary file so a crash mid-write never leaves half a config behind. */
function writeJson(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const tmp = file + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2));
  fs.renameSync(tmp, file);
}

/**
 * A JSON document kept in memory and written back shortly after each change, so a burst of
 * edits (dragging a slider) costs one write.
 */
class JsonStore {
  constructor(file, defaults) {
    this.file = file;
    const loaded = readJson(file, null);
    this.data = merge(structuredClone(defaults), loaded && typeof loaded === 'object' ? loaded : {});
    this.timer = null;
  }

  save() {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.flush(), 250);
  }

  flush() {
    clearTimeout(this.timer);
    this.timer = null;
    try {
      writeJson(this.file, this.data);
    } catch (e) {
      console.error('[store] could not write', this.file, e);
    }
  }
}

/** Fills in keys from {@code defaults} that the saved file does not have yet. */
function merge(defaults, saved) {
  for (const [key, value] of Object.entries(saved)) {
    const base = defaults[key];
    if (base && value && typeof base === 'object' && typeof value === 'object' && !Array.isArray(base) && !Array.isArray(value)) {
      defaults[key] = merge(base, value);
    } else {
      defaults[key] = value;
    }
  }
  return defaults;
}

module.exports = { readJson, writeJson, JsonStore };
