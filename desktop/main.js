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
const { NativePlayer, findPlayers } = require('./src/player');
const { PlayerWindow } = require('./src/playerWindow');
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
const playerWindow = new PlayerWindow();
let overlayInit = null; // title/artwork/subtitles for the player controls
let overlayWebSubs = null; // last web-subtitle list, resent if the controls reload

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
    // With mpv, video plays inside the player window under the Android controls.
    const { mpv } = findPlayers({ resourcesPath: app.isPackaged ? process.resourcesPath : null, devVendorDir: path.join(__dirname, 'vendor') });
    if (mpv) {
      overlayInit = { ...(opts.overlay || {}), title: opts.title || '' };
      overlayWebSubs = null;
      const resume = overlayInit.resumeSeconds > 30 && !opts.live;
      const wid = await playerWindow.open(mainWindow);
      return player.play({ ...opts, wid, startPaused: resume, startSeconds: resume ? overlayInit.resumeSeconds : opts.startSeconds });
    }
    return player.play(opts);
  });
  ipcMain.on('player:stop', () => { player.stop(); playerWindow.close(); });
  ipcMain.on('player:webSubs', (_e, payload) => {
    overlayWebSubs = payload;
    playerWindow.send('overlay:event', { type: 'subtitles', ...payload });
  });

  // ── Player controls overlay → mpv ──────────────────────────────────────
  ipcMain.on('overlay:ready', () => {
    if (overlayInit) playerWindow.send('overlay:init', overlayInit);
    if (overlayWebSubs) playerWindow.send('overlay:event', { type: 'subtitles', ...overlayWebSubs });
  });
  const ASPECT = {
    NORMAL: { 'video-aspect-override': '-1', panscan: 0, keepaspect: true },
    ZOOM: { 'video-aspect-override': '-1', panscan: 1, keepaspect: true },
    STRETCH: { 'video-aspect-override': '-1', panscan: 0, keepaspect: false },
    '16:9': { 'video-aspect-override': '16:9', panscan: 0, keepaspect: true },
    '4:3': { 'video-aspect-override': '4:3', panscan: 0, keepaspect: true },
    '21:9': { 'video-aspect-override': '21:9', panscan: 0, keepaspect: true },
  };
  const SETTABLE = new Set(['aid', 'sid', 'speed', 'sub-delay', 'volume', 'mute']);
  const quitPlayer = () => player.command('quit').catch(() => player.stop());
  ipcMain.handle('overlay:cmd', async (_e, name, args = []) => {
    switch (name) {
      case 'togglePause': return player.command('cycle', 'pause');
      case 'seek': return player.command('seek', Number(args[0]) || 0, 'absolute');
      case 'setProperty':
        if (!SETTABLE.has(args[0])) throw new Error('Not allowed');
        return player.setProperty(args[0], args[1]);
      case 'webSub': return player.addSubtitle(String(args[0]), args[1], args[2]);
      case 'aspect': {
        const props = ASPECT[args[0]] || ASPECT.NORMAL;
        for (const [k, v] of Object.entries(props)) await player.setProperty(k, v);
        return true;
      }
      case 'syncToHeard': {
        // The line on screen is being spoken now: shift subtitles by the gap.
        const [start, pos, delay] = await Promise.all([
          player.getProperty('sub-start').catch(() => null),
          player.getProperty('time-pos'),
          player.getProperty('sub-delay'),
        ]);
        if (typeof start !== 'number') return false;
        await player.setProperty('sub-delay', (delay || 0) + (pos - start));
        return true;
      }
      case 'retry': return player.retry();
      case 'resume':
        if (args[0] === 'restart') {
          await player.command('seek', 0, 'absolute');
          mainWindow?.webContents.send('player:restart', {});
        }
        return player.setProperty('pause', false);
      case 'playNext':
        mainWindow?.webContents.send('player:next', overlayInit && overlayInit.nextEpisode);
        return quitPlayer();
      case 'fullscreen': return playerWindow.toggleFullscreen();
      case 'close': return quitPlayer();
      default: throw new Error(`Unknown player command ${name}`);
    }
  });
  playerWindow.onClosed = () => player.stop(); // window closed directly (Alt+F4)

  // Fuzer (sign-in window + session-backed requests; see src/fuzer.js)
  ipcMain.handle('fuzer:status', () => fuzer.status());
  ipcMain.handle('fuzer:signIn', e => fuzer.signIn(fromSender(e)));
  ipcMain.handle('fuzer:signOut', () => fuzer.signOut());
  ipcMain.handle('fuzer:page', (_e, pathOrUrl) => fuzer.fetchPage(pathOrUrl));
  ipcMain.handle('fuzer:torrent', async (_e, pathOrUrl) => new Uint8Array(await fuzer.downloadTorrent(pathOrUrl)));

  const forward = channel => payload => mainWindow?.webContents.send(channel, payload);
  player.on('progress', forward('player:progress'));
  player.on('state', state => playerWindow.send('overlay:state', state));
  player.on('playback-error', ({ message }) => playerWindow.send('overlay:event', { type: 'error', message }));
  player.on('eof', () => {
    // Finished: the next episode starts like Android's up-next countdown would.
    if (overlayInit && overlayInit.nextEpisode) mainWindow?.webContents.send('player:next', overlayInit.nextEpisode);
    quitPlayer();
  });
  player.on('ended', payload => {
    playerWindow.close();
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
