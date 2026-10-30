'use strict';
/**
 * Bridge between the shared web app and the desktop shell.
 * The web app detects `window.electronAPI` and turns on its title bar and
 * native playback; on the website this object simply doesn't exist.
 */
const { contextBridge, ipcRenderer } = require('electron');

function subscribe(channel, callback) {
  const handler = (_event, payload) => callback(payload);
  ipcRenderer.on(channel, handler);
  return () => ipcRenderer.removeListener(channel, handler);
}

contextBridge.exposeInMainWorld('electronAPI', {
  isDesktop: true,
  platform: process.platform,

  minimize: () => ipcRenderer.send('window:minimize'),
  maximize: () => ipcRenderer.send('window:maximize'),
  close: () => ipcRenderer.send('window:close'),
  toggleFullscreen: () => ipcRenderer.send('window:fullscreen'),
  onFullscreenChanged: callback => subscribe('window:maximized', callback),

  nativePlayer: {
    /** @returns {Promise<{available: boolean, player: 'mpv'|'vlc'|null}>} */
    info: () => ipcRenderer.invoke('player:info'),
    /** @returns {Promise<{player: string, session: number}>} */
    play: opts => ipcRenderer.invoke('player:play', opts),
    stop: () => ipcRenderer.send('player:stop'),
    onProgress: callback => subscribe('player:progress', callback),
    onEnded: callback => subscribe('player:ended', callback),
    onError: callback => subscribe('player:error', callback),
    /** Web subtitles for the player's Web Subtitles menu: {list: [{url, lang, source}], selected} */
    setWebSubs: payload => ipcRenderer.send('player:webSubs', payload),
    /** "From start" chosen in the resume dialog. */
    onRestart: callback => subscribe('player:restart', callback),
    /** Up-next card / end of episode: {season, episode} */
    onNext: callback => subscribe('player:next', callback),
  },

  fuzer: {
    /** @returns {Promise<{signedIn: boolean}>} */
    status: () => ipcRenderer.invoke('fuzer:status'),
    /** Opens Fuzer's login page; resolves true once signed in. */
    signIn: () => ipcRenderer.invoke('fuzer:signIn'),
    signOut: () => ipcRenderer.invoke('fuzer:signOut'),
    /** @returns {Promise<{signedIn: boolean, html: string}>} */
    page: pathOrUrl => ipcRenderer.invoke('fuzer:page', pathOrUrl),
    /** @returns {Promise<Uint8Array>} .torrent bytes */
    torrent: pathOrUrl => ipcRenderer.invoke('fuzer:torrent', pathOrUrl),
  },
});
