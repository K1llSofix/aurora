'use strict';

const { contextBridge, ipcRenderer, webUtils } = require('electron');

const call = (channel, ...args) => ipcRenderer.invoke(channel, ...args);

/** Subscribes to an event from the main process; returns a function that unsubscribes. */
function on(channel, fn) {
  const listener = (_event, payload) => fn(payload);
  ipcRenderer.on(channel, listener);
  return () => ipcRenderer.removeListener(channel, listener);
}

contextBridge.exposeInMainWorld('aurora', {
  app: {
    info: () => call('app:info'),
    openExternal: (url) => call('app:openExternal', url),
    openFolder: (kind, installId) => call('app:openFolder', kind, installId),
    copy: (text) => call('app:copy', text),
    showItem: (file) => call('app:showItem', file),
    showLauncher: () => call('app:showLauncher'),
    quit: () => call('app:quit'),
    whatsNew: (lang) => call('app:whatsNew', lang),
    whatsNewSeen: () => call('app:whatsNewSeen'),
    changelog: (lang) => call('app:changelog', lang),
    modTheme: () => call('app:modTheme'),
    onEvent: (fn) => on('app:event', fn),
  },
  window: {
    minimize: () => call('window:minimize'),
    toggleMaximize: () => call('window:toggleMaximize'),
    close: () => call('window:close'),
    onState: (fn) => on('window:state', fn),
  },
  settings: {
    get: () => call('settings:get'),
    set: (patch) => call('settings:set', patch),
    onChanged: (fn) => on('settings:changed', fn),
  },
  installs: {
    list: () => call('installs:list'),
    save: (inst) => call('installs:save', inst),
    remove: (id) => call('installs:remove', id),
    select: (id) => call('installs:select', id),
    versions: (snapshots) => call('installs:versions', snapshots),
    shortcut: (id, where, pngs) => call('installs:shortcut', id, where, pngs),
    refreshShortcuts: (id, pngs) => call('installs:refreshShortcuts', id, pngs),
    clone: (id, opts) => call('installs:clone', id, opts),
    exportPack: (id) => call('installs:export', id),
    copySettings: (fromId, toId, parts) => call('installs:copySettings', fromId, toId, parts),
    onExportProgress: (fn) => on('installs:exportProgress', fn),
  },
  icons: {
    catalog: () => call('icons:catalog'),
    saveCustom: (dataUrl) => call('icons:saveCustom', dataUrl),
  },
  game: {
    launch: (opts) => call('game:launch', opts),
    cancel: () => call('game:cancel'),
    kill: () => call('game:kill'),
    state: () => call('game:state'),
    logs: () => call('game:logs'),
    ackExit: () => call('game:ackExit'),
    repair: (id) => call('game:repair', id),
    onState: (fn) => on('game:state', fn),
    onProgress: (fn) => on('game:progress', fn),
    onLog: (fn) => on('game:log', fn),
  },
  accounts: {
    list: () => call('accounts:list'),
    select: (id) => call('accounts:select', id),
    remove: (id) => call('accounts:remove', id),
    addOffline: (name) => call('accounts:addOffline', name),
    skin: (id) => call('accounts:skin', id),
    offlineUnlocked: () => call('accounts:offlineUnlocked'),
    signIn: () => call('accounts:signIn'),
    cancelSignIn: () => call('accounts:cancelSignIn'),
    onCode: (fn) => on('accounts:code', fn),
    onChanged: (fn) => on('accounts:changed', fn),
  },
  skins: {
    get: (id) => call('skins:get', id),
    save: (id, dataUrl, slim) => call('skins:save', id, dataUrl, slim),
    reset: (id) => call('skins:reset', id),
    byName: (name) => call('skins:byName', name),
    gallery: (query) => call('skins:gallery', query),
    texture: (hash) => call('skins:texture', hash),
    pick: () => call('skins:pick'),
  },
  servers: {
    list: () => call('servers:list'),
    save: (server) => call('servers:save', server),
    remove: (id) => call('servers:remove', id),
    move: (id, to) => call('servers:move', id, to),
    ping: (address, force) => call('servers:ping', address, force),
  },
  mods: {
    list: (installId) => call('mods:list', installId),
    setEnabled: (installId, file, enabled) => call('mods:setEnabled', installId, file, enabled),
    remove: (installId, file) => call('mods:remove', installId, file),
    pick: (installId) => call('mods:pick', installId),
    addPaths: (installId, paths) => call('mods:addPaths', installId, paths),
    search: (query) => call('mods:search', query),
    install: (installId, project) => call('mods:install', installId, project),
    installed: (installId) => call('mods:installed', installId),
    onProgress: (fn) => on('mods:progress', fn),
  },
  packs: {
    list: (installId, kind) => call('packs:list', installId, kind),
    setEnabled: (installId, kind, file, on) => call('packs:setEnabled', installId, kind, file, on),
    remove: (installId, kind, file) => call('packs:remove', installId, kind, file),
    pick: (installId, kind) => call('packs:pick', installId, kind),
    addPaths: (installId, kind, paths) => call('packs:addPaths', installId, kind, paths),
    search: (kind, query) => call('packs:search', kind, query),
    install: (installId, kind, project) => call('packs:install', installId, kind, project),
    installed: (installId, kind) => call('packs:installed', installId, kind),
  },
  content: {
    check: (installId, kind) => call('content:check', installId, kind),
    update: (installId, kind, list) => call('content:update', installId, kind, list),
    onProgress: (fn) => on('content:progress', fn),
  },
  worlds: {
    list: (installId) => call('worlds:list', installId),
    size: (installId, folder) => call('worlds:size', installId, folder),
    backups: (installId) => call('worlds:backups', installId),
    backup: (installId, folder) => call('worlds:backup', installId, folder),
    restore: (installId, file, label) => call('worlds:restore', installId, file, label),
    remove: (installId, folder) => call('worlds:remove', installId, folder),
    removeBackup: (installId, file) => call('worlds:removeBackup', installId, file),
    showBackup: (installId, file) => call('worlds:showBackup', installId, file),
    open: (installId, folder) => call('worlds:open', installId, folder),
    onProgress: (fn) => on('worlds:progress', fn),
  },
  shots: {
    list: () => call('shots:list'),
    thumb: (file) => call('shots:thumb', file),
    image: (file) => call('shots:image', file),
    copy: (file) => call('shots:copy', file),
    remove: (file) => call('shots:remove', file),
  },
  crash: {
    fix: (action, installId) => call('crash:fix', action, installId),
    share: () => call('logs:share'),
  },
  updates: {
    state: () => call('updates:state'),
    check: () => call('updates:check'),
    install: () => call('updates:install'),
    onState: (fn) => on('updates:state', fn),
  },
  modpacks: {
    search: (query) => call('modpacks:search', query),
    versions: (project) => call('modpacks:versions', project),
    icon: (url) => call('modpacks:icon', url),
    read: (file) => call('modpacks:read', file),
    pick: () => call('modpacks:pick'),
    install: (opts) => call('modpacks:install', opts),
    cancel: () => call('modpacks:cancel'),
    onProgress: (fn) => on('modpacks:progress', fn),
  },
  news: {
    get: () => call('news:get'),
  },
  java: {
    pick: () => call('java:pick'),
    probe: (path) => call('java:probe', path),
  },
  dialog: {
    pickFolder: (title) => call('dialog:pickFolder', title),
  },
  files: {
    // Files dropped on the window: Electron no longer exposes File.path to pages.
    pathOf: (file) => webUtils.getPathForFile(file),
  },
});
