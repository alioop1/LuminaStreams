'use strict';
/**
 * Fuzer access for the desktop app.
 * A browser page can't reach Fuzer (no CORS headers, its own cookies), but the
 * desktop app's main process can. Fuzer's login is protected by reCAPTCHA, so
 * the user signs in once in a Fuzer window; the session lives in its own
 * persistent cookie store ("persist:fuzer") and every request reuses it.
 * Pages are returned as decoded HTML; the web app parses them (it has a DOM).
 */
const { BrowserWindow, session } = require('electron');

const BASE = 'https://www.fuzer.xyz';
const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36';

function fuzerSession() {
  const ses = session.fromPartition('persist:fuzer');
  ses.setUserAgent(USER_AGENT);
  return ses;
}

/** Only Fuzer URLs may be requested through this bridge. */
function toFuzerUrl(pathOrUrl) {
  const url = new URL(String(pathOrUrl), BASE + '/');
  if (url.protocol !== 'https:' || !/(^|\.)fuzer\.xyz$/i.test(url.hostname)) {
    throw new Error('Not a Fuzer URL');
  }
  return url.toString();
}

function decode(buffer) {
  // Fuzer serves windows-1255 Hebrew; fall back to UTF-8 if that's what it is.
  const bytes = new Uint8Array(buffer);
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  } catch {
    return new TextDecoder('windows-1255').decode(bytes);
  }
}

async function request(pathOrUrl) {
  const res = await fuzerSession().fetch(toFuzerUrl(pathOrUrl), {
    headers: { Referer: `${BASE}/browse.php`, 'Accept-Language': 'he-IL,he;q=0.9,en;q=0.8' },
  });
  return res;
}

const isLoginUrl = url => {
  try { return /\/login|auth-next/i.test(new URL(url).pathname); } catch { return false; }
};
// Session fetch doesn't always expose the final URL, so also recognise the login
// page by its form (new auth-next page or the old vBulletin one).
const isLoginPage = html => /id="password-form"|auth-next\/public\/login|name="vb_login_username"/i.test(html);

/** True when the stored session is accepted (signed-out requests land on login.php). */
async function status() {
  try {
    const res = await request('/index.php');
    const html = decode(await res.arrayBuffer());
    return { signedIn: !isLoginUrl(res.url) && !isLoginPage(html) };
  } catch (err) {
    return { signedIn: false, error: err.message };
  }
}

/** Fetch a Fuzer page as text. Reports signedIn:false when redirected to login. */
async function fetchPage(pathOrUrl) {
  const res = await request(pathOrUrl);
  const html = decode(await res.arrayBuffer());
  if (isLoginUrl(res.url) || isLoginPage(html)) return { signedIn: false, html: '' };
  return { signedIn: true, html };
}

/** Resolve a thread or attachment URL to .torrent bytes. */
async function downloadTorrent(pathOrUrl) {
  let url = toFuzerUrl(pathOrUrl);
  if (/showthread\.php/i.test(url)) {
    const page = await fetchPage(url);
    if (!page.signedIn) throw new Error('Fuzer session expired');
    const m = page.html.match(/href="([^"]*attachment\.php[^"]*)"/i);
    if (!m) throw new Error('Torrent link not found on the Fuzer page');
    url = toFuzerUrl(m[1].replace(/&amp;/g, '&'));
  }
  const res = await request(url);
  if (isLoginUrl(res.url)) throw new Error('Fuzer session expired');
  const bytes = Buffer.from(await res.arrayBuffer());
  if (!bytes.length || /<html/i.test(bytes.subarray(0, 200).toString('latin1'))) {
    throw new Error('Fuzer returned a page instead of a torrent file');
  }
  return bytes;
}

/** Show Fuzer's own login page; resolves true once the user is signed in. */
function signIn(parent) {
  return new Promise(resolve => {
    const win = new BrowserWindow({
      parent,
      modal: !!parent,
      width: 520,
      height: 720,
      title: 'Fuzer',
      backgroundColor: '#070b14',
      autoHideMenuBar: true,
      webPreferences: { partition: 'persist:fuzer', contextIsolation: true, nodeIntegration: false, sandbox: true },
    });
    let done = false;
    const finish = ok => {
      if (done) return;
      done = true;
      resolve(ok);
      if (!win.isDestroyed()) win.close();
    };
    const check = (_e, url) => {
      try {
        const u = new URL(url);
        if (/fuzer\.xyz$/i.test(u.hostname) && !isLoginUrl(url)) finish(true);
      } catch {}
    };
    win.webContents.on('did-navigate', check);
    win.webContents.on('did-redirect-navigation', check);
    win.on('closed', () => finish(false));
    win.webContents.setUserAgent(USER_AGENT);
    win.loadURL(`${BASE}/login.php`);
  });
}

async function signOut() {
  await fuzerSession().clearStorageData({ storages: ['cookies'] });
}

module.exports = { status, fetchPage, downloadTorrent, signIn, signOut };
