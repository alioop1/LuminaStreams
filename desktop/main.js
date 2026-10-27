const { app, BrowserWindow, globalShortcut, ipcMain } = require('electron');
const path = require('path');

let mainWindow;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 850,
    minWidth: 1024,
    minHeight: 680,
    backgroundColor: '#070B14',
    icon: path.join(__dirname, 'src', 'assets', 'icon.png'),
    frame: false,
    titleBarStyle: 'hidden',
    titleBarOverlay: {
      color: '#070B14',
      symbolColor: '#EEF2FF',
      height: 38
    },
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      webSecurity: false // needed for cross-origin streaming CDN requests
    }
  });

  mainWindow.loadFile(path.join(__dirname, 'src', 'index.html'));

  // Fullscreen toggle with F11
  mainWindow.on('enter-full-screen', () => {
    mainWindow.webContents.send('fullscreen-changed', true);
  });
  mainWindow.on('leave-full-screen', () => {
    mainWindow.webContents.send('fullscreen-changed', false);
  });
}

// IPC handlers
ipcMain.handle('toggle-fullscreen', () => {
  if (mainWindow.isFullScreen()) {
    mainWindow.setFullScreen(false);
  } else {
    mainWindow.setFullScreen(true);
  }
});

ipcMain.handle('minimize', () => mainWindow.minimize());
ipcMain.handle('maximize', () => {
  if (mainWindow.isMaximized()) mainWindow.unmaximize();
  else mainWindow.maximize();
});
ipcMain.handle('close', () => mainWindow.close());
ipcMain.handle('is-maximized', () => mainWindow.isMaximized());

app.whenReady().then(() => {
  createWindow();
  globalShortcut.register('F11', () => {
    mainWindow.setFullScreen(!mainWindow.isFullScreen());
  });
});

app.on('window-all-closed', () => app.quit());
app.on('will-quit', () => globalShortcut.unregisterAll());
