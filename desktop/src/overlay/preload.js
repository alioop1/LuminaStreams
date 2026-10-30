'use strict';
/** Bridge for the player controls overlay (overlay/player.html). */
const { contextBridge, ipcRenderer } = require('electron');

function subscribe(channel, callback) {
  const handler = (_event, payload) => callback(payload);
  ipcRenderer.on(channel, handler);
  return () => ipcRenderer.removeListener(channel, handler);
}

contextBridge.exposeInMainWorld('player', {
  /** Title, artwork, language, subtitles list, resume point, next episode. */
  onInit: callback => subscribe('overlay:init', callback),
  /** Live playback state: position, duration, paused, tracks, decoder, … */
  onState: callback => subscribe('overlay:state', callback),
  /** {type: 'error'|'eof'|'toast', ...} */
  onEvent: callback => subscribe('overlay:event', callback),
  /** Send a player command, e.g. cmd('seek', 120) — resolves with mpv's answer. */
  cmd: (name, ...args) => ipcRenderer.invoke('overlay:cmd', name, args),
  ready: () => ipcRenderer.send('overlay:ready'),
});
