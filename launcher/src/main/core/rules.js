'use strict';

const os = require('os');

const OS_NAME = { win32: 'windows', darwin: 'osx', linux: 'linux' }[process.platform] || process.platform;
const ARCH = { ia32: 'x86', x64: 'x86_64', arm64: 'arm64' }[process.arch] || process.arch;

function compareVersions(a, b) {
  const pa = String(a).split(/[.\-]/).map((n) => parseInt(n, 10) || 0);
  const pb = String(b).split(/[.\-]/).map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] || 0) - (pb[i] || 0);
    if (d) return d < 0 ? -1 : 1;
  }
  return 0;
}

function osMatches(rule) {
  if (!rule) return true;
  if (rule.name && rule.name !== OS_NAME) return false;
  // Version files use "x86" for 32-bit Java only, which ARCH reports for ia32 alone.
  if (rule.arch && rule.arch !== ARCH) return false;
  if (rule.version) {
    try {
      if (!new RegExp(rule.version).test(os.release())) return false;
    } catch {
      return false;
    }
  }
  if (rule.versionRange) {
    // min is inclusive and max exclusive, so a build right on the boundary gets exactly one of
    // two mutually exclusive argument sets (26.3 picks ZGC or G1 this way).
    const release = os.release();
    if (rule.versionRange.min && compareVersions(release, rule.versionRange.min) < 0) return false;
    if (rule.versionRange.max && compareVersions(release, rule.versionRange.max) >= 0) return false;
  }
  return true;
}

/** Evaluates a version file's rule list: the last matching rule decides, no rules means allowed. */
function allowed(rules, features = {}) {
  if (!Array.isArray(rules) || rules.length === 0) return true;
  let result = false;
  for (const rule of rules) {
    let match = osMatches(rule.os);
    if (match && rule.features) {
      for (const [key, value] of Object.entries(rule.features)) {
        if (Boolean(features[key]) !== value) {
          match = false;
          break;
        }
      }
    }
    if (match) result = rule.action === 'allow';
  }
  return result;
}

/** Maven coordinates (group:artifact:version[:classifier][@ext]) to a repository path. */
function mavenPath(name) {
  let [coords, ext = 'jar'] = name.split('@');
  const [group, artifact, version, classifier] = coords.split(':');
  const file = `${artifact}-${version}${classifier ? '-' + classifier : ''}.${ext}`;
  return [...group.split('.'), artifact, version, file].join('/');
}

/** group:artifact[:classifier], the identity used to drop duplicate libraries. */
function libraryKey(name) {
  const [coords] = name.split('@');
  const parts = coords.split(':');
  return parts.length > 3 ? `${parts[0]}:${parts[1]}:${parts[3]}` : `${parts[0]}:${parts[1]}`;
}

module.exports = { OS_NAME, ARCH, compareVersions, allowed, mavenPath, libraryKey };
