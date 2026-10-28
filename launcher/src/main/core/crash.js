'use strict';

// Reads the end of a crashed game's log and its crash report and names the likely cause in plain
// words, with a fix the launcher can apply in one click where there is one: more memory, switch a
// mod off, install a missing one, use the launcher's own Java.

const MAX_FINDINGS = 3;

function unique(list, key) {
  const seen = new Set();
  return list.filter((item) => {
    const k = key(item);
    if (seen.has(k)) return false;
    seen.add(k);
    return true;
  });
}

/**
 * @param text the log lines and the crash report, joined
 * @param mods installed mods: [{ id, name, file }] (from fabric.mod.json)
 * @param memory the installation's memory in MB
 * @param maxMemory what the PC can give the game in MB
 * @param customJava whether a Java of the player's own was used
 */
function diagnose({ text, mods = [], memory = 0, maxMemory = 0, customJava = false }) {
  const findings = [];
  const byId = new Map(mods.filter((m) => m.id).map((m) => [m.id, m]));
  const modRef = (id) => {
    const mod = byId.get(id);
    return mod ? { id, name: mod.name || id, file: mod.file } : { id, name: id, file: null };
  };

  // Out of memory: more if the PC has it to give.
  if (/java\.lang\.OutOfMemoryError|Out of memory|GC overhead limit exceeded/i.test(text)) {
    const more = Math.min(maxMemory, Math.max(memory + 1024, Math.ceil(memory * 1.5 / 512) * 512));
    findings.push({
      kind: 'memory',
      params: { memory, more },
      action: more > memory ? { type: 'memory', value: more } : null,
    });
  }

  // Fabric found mods that do not fit together before the game even started.
  for (const m of text.matchAll(/Mod '([^']+)' \(([\w.-]+)\) [^\n]*? requires (?:version [^\n]*? of|any version of) (?:mod )?'?([^'\n(]+?)'? \(([\w.-]+)\), which is missing/g)) {
    findings.push({ kind: 'missing', params: { mod: m[1], dependency: m[3].trim() }, action: { type: 'install', project: m[4], name: m[3].trim() } });
  }
  for (const m of text.matchAll(/Mod '([^']+)' \(([\w.-]+)\) [^\n]*? requires (?:version [^\n]*? of|any version of) (?:mod )?([\w.-]+), which is missing/g)) {
    findings.push({ kind: 'missing', params: { mod: m[1], dependency: m[3] }, action: { type: 'install', project: m[3], name: m[3] } });
  }
  for (const m of text.matchAll(/Mod '([^']+)' \(([\w.-]+)\) [^\n]*? is incompatible with [^\n]*? of (?:mod )?'?([^'\n(]+?)'? \(([\w.-]+)\)/g)) {
    findings.push({ kind: 'incompatible', params: { mod: m[1], other: m[3].trim() }, action: { type: 'disable', ...modRef(m[2]) } });
  }
  for (const m of text.matchAll(/Mod '([^']+)' \(([\w.-]+)\) [^\n]*? requires [^\n]*?minecraft[^\n]*?, but only the wrong version is present/gi)) {
    findings.push({ kind: 'wrongVersion', params: { mod: m[1] }, action: { type: 'disable', ...modRef(m[2]) } });
  }
  for (const m of text.matchAll(/(?:Mod ID|mod id) '?([\w.-]+)'? (?:is provided by multiple|has duplicate)|Found duplicate mods?[^\n]*?\b([\w.-]+)\b/gi)) {
    const id = m[1] || m[2];
    if (id) findings.push({ kind: 'duplicate', params: { mod: modRef(id).name }, action: null });
  }

  // A mod's mixins failed to apply: that mod does not fit this version or another mod.
  for (const m of text.matchAll(/from mod ([\w.-]+)\]\s*(?:from phase \[\w+\] )?in config \[[^\]]*\] FAILED|Mixin apply for mod ([\w.-]+) failed|mixin\.[^\n]*?from mod ([\w.-]+)[^\n]*?(?:failed|InvalidInjectionException)/gi)) {
    const id = m[1] || m[2] || m[3];
    if (id && id !== 'aurora' && id !== 'minecraft' && id !== 'fabricloader') {
      findings.push({ kind: 'mixin', params: { mod: modRef(id).name }, action: { type: 'disable', ...modRef(id) } });
    }
  }

  // A Java that cannot run this version.
  if (/UnsupportedClassVersionError|has been compiled by a more recent version of the Java Runtime/i.test(text)) {
    findings.push({ kind: 'java', params: {}, action: customJava ? { type: 'java' } : null });
  }

  // The graphics driver or OpenGL said no.
  if (/EXCEPTION_ACCESS_VIOLATION[\s\S]{0,400}(?:atio6axx|nvoglv|ig\w+icd|amdvlk|nvwgf|igxelpicd)|Pixel format not accelerated|GLFW error 65542|Couldn't set pixel format|WGL: The driver does not appear to support OpenGL|vkCreateInstance failed|VK_ERROR_INCOMPATIBLE_DRIVER/i.test(text)) {
    findings.push({ kind: 'driver', params: {}, action: { type: 'drivers' } });
  }

  // The world itself is damaged.
  if (/Exception reading [^\n]*level\.dat|Failed to load level|Couldn't load chunk[\s\S]{0,80}(?:corrupt|unexpected end)/i.test(text)) {
    findings.push({ kind: 'world', params: {}, action: { type: 'worlds' } });
  }

  return unique(findings, (f) => `${f.kind}|${f.action && f.action.file}|${f.params.mod || ''}`).slice(0, MAX_FINDINGS);
}

module.exports = { diagnose };
