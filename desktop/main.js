'use strict';
/**
 * LuminaStreams for Windows.
 * The window shows the same web app as the website (one UI, one account sync);
 * video plays in mpv or VLC so every format works (see src/player.js).
 */
const { app, BrowserWindow, ipcMain, shell, screen } = require('electron');
const fs = require('fs');
const path = require('path');
const { startServer } = require('./src/server');
const { NativePlayer } = require('./src/player');
const fuzer = require('./src/fuzer');

// Packaged: the web app and mpv ship in resources/. Dev: read them from the repo.
const WEB_DIR = app.isPackaged
  ? path.join(process.resourcesPath, 'web')
  : path.join(__dirname, '..', 'web', 'public', 'app');

const player = new NativePlayer({
  resourcesPath: app.isPackaged ? process.resourcesPath : null,
  devVendorDir: path.join(__dirname, 'vendor'),
});

let mainWindow = null;

// ── Window size/position memory ──────────────────────────────────────────
const stateFile = () => path.join(app.getPath('userData'), 'window-state.json');

function loadWindowState() {
  try {
    const s = JSON.parse(fs.readFileSync(stateFile(), 'utf8'));
    // Ignore a saved position that is no longer on any connected display.
    const visible = screen.getAllDisplays().some(d => {
      const b = d.workArea;
      return s.x >= b.x - 50 && s.y >= b.y - 50 && s.x < b.x + b.width && s.y < b.y + b.height;
    });
    return visible ? s : { width: s.width, height: s.height, maximized: s.maximized };
  } catch {
    return { width: 1440, height: 900, maximized: true };
  }
}

function saveWindowState(win) {
  try {
    const b = win.getNormalBounds();
    fs.writeFileSync(stateFile(), JSON.stringify({ ...b, maximized: win.isMaximized() }));
  } catch {}
}

// ── Main window ──────────────────────────────────────────────────────────
function createWindow(port) {
  const state = loadWindowState();
  const win = new BrowserWindow({
    width: state.width || 1440,
    height: state.height || 900,
    x: state.x,
    y: state.y,
    minWidth: 960,
    minHeight: 600,
    // Windows draws its own minimize/maximize/close over the app's 54px header;
    // the header is the drag area (body.is-electron rules in app.css).
    titleBarStyle: 'hidden',
    titleBarOverlay: { color: '#070b14', symbolColor: '#ffffff', height: 54 },
    backgroundColor: '#070b14',
    title: 'LuminaStreams',
    icon: path.join(__dirname, 'build', 'icon.png'),
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  if (state.maximized) win.maximize();
  win.once('ready-to-show', () => win.show());

  // External links open in the default browser, never inside the app.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/i.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (event, url) => {
    if (!url.startsWith(`http://127.0.0.1:${port}/`)) {
      event.preventDefault();
      if (/^https?:/i.test(url)) shell.openExternal(url);
    }
  });

  // F11 = fullscreen (Kodi/Stremio habit); Esc leaves fullscreen.
  win.webContents.on('before-input-event', (event, input) => {
    if (input.type !== 'keyDown') return;
    if (input.key === 'F11') {
      win.setFullScreen(!win.isFullScreen());
      event.preventDefault();
    } else if (input.key === 'Escape' && win.isFullScreen()) {
      win.setFullScreen(false);
    }
  });

  const notifyMax = () => win.webContents.send('window:maximized', win.isMaximized() || win.isFullScreen());
  ['maximize', 'unmaximize', 'enter-full-screen', 'leave-full-screen'].forEach(e => win.on(e, notifyMax));
  win.on('close', () => saveWindowState(win));
  win.on('closed', () => { mainWindow = null; player.stop(); });

  win.loadURL(`http://127.0.0.1:${port}/index.html`);
  return win;
}

// ── IPC: window controls + native player ─────────────────────────────────
function registerIpc() {
  const fromSender = e => BrowserWindow.fromWebContents(e.sender);
  ipcMain.on('window:minimize', e => fromSender(e)?.minimize());
  ipcMain.on('window:maximize', e => {
    const win = fromSender(e);
    if (!win) return;
    if (win.isFullScreen()) win.setFullScreen(false);
    else if (win.isMaximized()) win.unmaximize();
    else win.maximize();
  });
  ipcMain.on('window:close', e => fromSender(e)?.close());
  ipcMain.on('window:fullscreen', e => {
    const win = fromSender(e);
    if (win) win.setFullScreen(!win.isFullScreen());
  });

  ipcMain.handle('player:info', () => player.info());
  ipcMain.handle('player:play', async (_e, opts) => {
    if (!opts || typeof opts.url !== 'string' || !/^(https?|rtmp|rtsp):/i.test(opts.url)) {
      throw new Error('Invalid stream URL');
    }
    return player.play(opts);
  });
  ipcMain.on('player:stop', () => player.stop());

  // Fuzer (sign-in window + session-backed requests; see src/fuzer.js)
  ipcMain.handle('fuzer:status', () => fuzer.status());
  ipcMain.handle('fuzer:signIn', e => fuzer.signIn(fromSender(e)));
  ipcMain.handle('fuzer:signOut', () => fuzer.signOut());
  ipcMain.handle('fuzer:page', (_e, pathOrUrl) => fuzer.fetchPage(pathOrUrl));
  ipcMain.handle('fuzer:torrent', async (_e, pathOrUrl) => new Uint8Array(await fuzer.downloadTorrent(pathOrUrl)));

  const forward = channel => payload => mainWindow?.webContents.send(channel, payload);
  player.on('progress', forward('player:progress'));
  player.on('ended', payload => {
    forward('player:ended')(payload);
    // Bring the app back to the front once the player window closes.
    if (mainWindow) { mainWindow.show(); mainWindow.focus(); }
  });
  player.on('error', forward('player:error'));
}

// ── Startup ──────────────────────────────────────────────────────────────
// Development runs use their own profile, so they can run beside the installed app.
if (!app.isPackaged) app.setPath('userData', path.join(app.getPath('appData'), 'LuminaStreams-dev'));

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });

  app.whenReady().then(async () => {
    const { port } = await startServer(WEB_DIR);
    registerIpc();
    mainWindow = createWindow(port);
  });

  app.on('window-all-closed', () => {
    player.stop();
    app.quit();
  });
}
