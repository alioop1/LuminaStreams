'use strict';
/**
 * The player window: the Android TV player's controls over mpv.
 *
 * A native video surface can't sit under HTML inside one Chromium window, so the
 * player uses two windows:
 *  - videoWin: an opaque, black, fullscreen window that mpv draws into (--wid);
 *  - overlayWin: a transparent window owned by videoWin, kept exactly on top
 *    of it, that renders the Android player UI (the shared web app's
 *    player-overlay.html + js/player/androidPlayer.js) and takes all input.
 */
const { BrowserWindow, screen } = require('electron');
const path = require('path');

class PlayerWindow {
  constructor() {
    this.videoWin = null;
    this.overlayWin = null;
    this.onClosed = null;
  }

  get isOpen() {
    return !!(this.videoWin && !this.videoWin.isDestroyed());
  }

  /**
   * Opens both windows on the display that holds `nearWindow`.
   * @param {string} overlayUrl  the controls page (served by the app's local server)
   * @returns {Promise<string>} the video window handle for mpv's --wid
   */
  async open(nearWindow, overlayUrl) {
    this.close();
    const display = nearWindow
      ? screen.getDisplayMatching(nearWindow.getBounds())
      : screen.getPrimaryDisplay();

    const video = new BrowserWindow({
      ...display.bounds,
      frame: false,
      backgroundColor: '#000000',
      title: 'LuminaStreams',
      icon: path.join(__dirname, '..', 'build', 'icon.png'),
      show: false,
      fullscreen: true,
      webPreferences: { sandbox: true, contextIsolation: true, nodeIntegration: false },
    });
    video.loadURL('data:text/html,<html style="background:#000"></html>');

    const overlay = new BrowserWindow({
      ...display.bounds,
      parent: video,
      frame: false,
      transparent: true,
      backgroundColor: '#00000000',
      hasShadow: false,
      resizable: false,
      skipTaskbar: true,
      show: false,
      webPreferences: {
        preload: path.join(__dirname, 'overlay', 'preload.js'),
        sandbox: true,
        contextIsolation: true,
        nodeIntegration: false,
      },
    });

    const sync = () => {
      if (overlay.isDestroyed() || video.isDestroyed()) return;
      overlay.setBounds(video.getBounds());
    };
    ['move', 'resize', 'enter-full-screen', 'leave-full-screen', 'restore', 'maximize'].forEach(e => video.on(e, sync));
    // Keep keyboard input on the controls whenever the player is activated.
    video.on('focus', () => { if (!overlay.isDestroyed()) overlay.focus(); });
    video.on('closed', () => {
      if (!overlay.isDestroyed()) overlay.destroy();
      this.videoWin = null;
      this.overlayWin = null;
      if (this.onClosed) this.onClosed();
    });

    this.videoWin = video;
    this.overlayWin = overlay;

    await overlay.loadURL(overlayUrl);
    video.show();
    sync();
    overlay.show();
    overlay.focus();

    const handle = video.getNativeWindowHandle();
    return (handle.length >= 8 ? handle.readBigUInt64LE(0) : BigInt(handle.readUInt32LE(0))).toString();
  }

  send(channel, payload) {
    if (this.overlayWin && !this.overlayWin.isDestroyed()) this.overlayWin.webContents.send(channel, payload);
  }

  toggleFullscreen() {
    if (!this.isOpen) return;
    this.videoWin.setFullScreen(!this.videoWin.isFullScreen());
  }

  close() {
    if (this.videoWin && !this.videoWin.isDestroyed()) this.videoWin.close();
    this.videoWin = null;
    this.overlayWin = null;
  }
}

module.exports = { PlayerWindow };
