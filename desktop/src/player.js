'use strict';
/**
 * Native playback for the desktop app (the Stremio approach): the UI stays in the
 * web app, video goes to a real player that handles everything browsers cannot —
 * MKV, HEVC, AC3/EAC3/DTS/TrueHD, embedded SRT/ASS/PGS, raw MPEG-TS IPTV.
 *
 * Preference: mpv (bundled, then installed) → VLC → none (the web player is used).
 * Both report position back so watch progress syncs like on every other platform.
 */
const { spawn } = require('child_process');
const { EventEmitter } = require('events');
const crypto = require('crypto');
const fs = require('fs');
const net = require('net');
const os = require('os');
const path = require('path');

const USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36';

function firstExisting(paths) {
  return paths.find(p => p && fs.existsSync(p)) || null;
}

/** mpv installed by winget lives in a versioned package folder. */
function wingetMpv() {
  const base = path.join(process.env.LOCALAPPDATA || '', 'Microsoft', 'WinGet', 'Packages');
  try {
    const dir = fs.readdirSync(base).find(d => d.toLowerCase().startsWith('shinchiro.mpv'));
    return dir ? path.join(base, dir, 'mpv.exe') : null;
  } catch {
    return null;
  }
}

function findPlayers({ resourcesPath, devVendorDir }) {
  const mpv = firstExisting([
    resourcesPath && path.join(resourcesPath, 'mpv', 'mpv.exe'),
    devVendorDir && path.join(devVendorDir, 'mpv', 'mpv.exe'),
    path.join(process.env.LOCALAPPDATA || '', 'Microsoft', 'WinGet', 'Links', 'mpv.exe'),
    wingetMpv(),
    path.join(process.env.ProgramFiles || 'C:\\Program Files', 'mpv', 'mpv.exe'),
    path.join(os.homedir(), 'scoop', 'apps', 'mpv', 'current', 'mpv.exe'),
  ]);
  const vlc = firstExisting([
    path.join(process.env.ProgramFiles || 'C:\\Program Files', 'VideoLAN', 'VLC', 'vlc.exe'),
    path.join(process.env['ProgramFiles(x86)'] || 'C:\\Program Files (x86)', 'VideoLAN', 'VLC', 'vlc.exe'),
  ]);
  return { mpv, vlc };
}

/**
 * Download subtitle files and store them as UTF-8. Hebrew sources often serve
 * windows-1255 labelled as UTF-8, which both players would otherwise garble.
 */
async function prepareSubtitles(urls) {
  const dir = path.join(os.tmpdir(), 'luminastreams-subs');
  fs.mkdirSync(dir, { recursive: true });
  const files = [];
  for (const url of urls.slice(0, 3)) {
    try {
      const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT } });
      if (!res.ok) continue;
      const bytes = new Uint8Array(await res.arrayBuffer());
      let text;
      try {
        text = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
      } catch {
        text = new TextDecoder('windows-1255').decode(bytes);
      }
      const ext = /\.vtt(\?|$)/i.test(url) || text.trimStart().startsWith('WEBVTT') ? '.vtt' : '.srt';
      const file = path.join(dir, crypto.createHash('sha1').update(url).digest('hex').slice(0, 16) + ext);
      fs.writeFileSync(file, text, 'utf8');
      files.push(file);
    } catch {
      // A missing subtitle must never block playback.
    }
  }
  return files;
}

class NativePlayer extends EventEmitter {
  constructor(locations) {
    super();
    this.locations = locations;
    this.proc = null;
    this.position = 0;
    this.duration = 0;
    this.paused = false;
    this.decoder = null; // e.g. 'd3d11va' (GPU) or 'no' (CPU)
    this.codec = null;
    this._poll = null;
    this._pipe = null;
    this._lastEmit = 0;
    this.session = 0; // tags events so a closing player never ends a newer one
  }

  info() {
    const { mpv, vlc } = findPlayers(this.locations);
    return { available: !!(mpv || vlc), player: mpv ? 'mpv' : (vlc ? 'vlc' : null), mpv, vlc };
  }

  /**
   * @param {{url: string, title?: string, startSeconds?: number,
   *          subtitleUrls?: string[], live?: boolean}} opts
   */
  async play(opts) {
    this.stop();
    const session = ++this.session;
    const { mpv, vlc } = findPlayers(this.locations);
    if (!mpv && !vlc) throw new Error('No native player found');
    this.position = 0;
    this.duration = 0;
    this.decoder = null;
    this.codec = null;
    const subs = await prepareSubtitles(opts.subtitleUrls || []);
    if (session !== this.session) return { player: null, session }; // superseded meanwhile
    if (mpv) this._playMpv(mpv, opts, subs, session);
    else this._playVlc(vlc, opts, subs, session);
    return { player: mpv ? 'mpv' : 'vlc', session };
  }

  stop() {
    if (this._poll) { clearInterval(this._poll); this._poll = null; }
    if (this._pipe) { this._pipe.destroy(); this._pipe = null; }
    if (this.proc) {
      const proc = this.proc;
      this.proc = null;
      try { proc.kill(); } catch {}
    }
  }

  _emitProgress(force) {
    const now = Date.now();
    if (!force && now - this._lastEmit < 1000) return;
    this._lastEmit = now;
    this.emit('progress', {
      session: this.session, position: this.position, duration: this.duration, paused: this.paused,
      decoder: this.decoder, codec: this.codec,
    });
  }

  _onExit(proc, session) {
    if (session !== this.session) return; // a newer play() owns the state now
    if (this._poll) { clearInterval(this._poll); this._poll = null; }
    if (this._pipe) { this._pipe.destroy(); this._pipe = null; }
    if (this.proc === proc) this.proc = null;
    this.emit('ended', { session, position: this.position, duration: this.duration });
  }

  // ── mpv: JSON IPC over a named pipe ──────────────────────────────────
  _playMpv(exe, opts, subs, session) {
    const pipe = `\\\\.\\pipe\\luminastreams-mpv-${process.pid}-${Date.now()}`;
    const args = [
      opts.url,
      `--force-media-title=${opts.title || 'LuminaStreams'}`,
      '--fullscreen',
      '--keep-open=no',
      '--idle=no',
      '--osc=yes',
      // Predictable behaviour: ignore any separate mpv install's settings.
      '--no-config',
      // Windows-native rendering (Direct3D 11) and decoding: GPU first via
      // DirectX D3D11VA/DXVA2, automatic fallback to FFmpeg on the CPU for any
      // codec or profile the GPU can't decode.
      '--vo=gpu-next',
      '--gpu-api=d3d11',
      '--hwdec=auto-safe',
      // HDR10/Dolby Vision base layers go to HDR displays as HDR.
      '--target-colorspace-hint=yes',
      '--cache=yes',
      '--demuxer-max-bytes=200MiB',
      '--save-position-on-quit=no',
      `--user-agent=${USER_AGENT}`,
      `--input-ipc-server=${pipe}`,
      // Hebrew first, like the Android app's default; embedded tracks included.
      '--slang=he,heb,iw,en,eng',
    ];
    if (opts.startSeconds > 1 && !opts.live) args.push(`--start=${Math.floor(opts.startSeconds)}`);
    for (const file of subs) args.push(`--sub-file=${file}`);
    if (subs.length) args.push('--sid=auto');

    const proc = spawn(exe, args, { stdio: 'ignore', windowsHide: false });
    this.proc = proc;
    proc.on('exit', () => this._onExit(proc, session));
    proc.on('error', err => { this.emit('error', err.message); this._onExit(proc, session); });

    let attempts = 0;
    const connect = () => {
      if (this.proc !== proc) return;
      const sock = net.connect(pipe);
      sock.on('connect', () => {
        this._pipe = sock;
        const send = cmd => sock.write(JSON.stringify({ command: cmd }) + '\n');
        send(['observe_property', 1, 'time-pos']);
        send(['observe_property', 2, 'duration']);
        send(['observe_property', 3, 'pause']);
        send(['observe_property', 4, 'hwdec-current']);
        send(['observe_property', 5, 'video-codec']);
      });
      let buffer = '';
      sock.on('data', chunk => {
        buffer += chunk.toString('utf8');
        let nl;
        while ((nl = buffer.indexOf('\n')) >= 0) {
          const line = buffer.slice(0, nl);
          buffer = buffer.slice(nl + 1);
          let msg;
          try { msg = JSON.parse(line); } catch { continue; }
          if (msg.event !== 'property-change') continue;
          if (msg.name === 'time-pos' && typeof msg.data === 'number') this.position = msg.data;
          if (msg.name === 'duration' && typeof msg.data === 'number') this.duration = msg.data;
          if (msg.name === 'pause') { this.paused = !!msg.data; this._emitProgress(true); continue; }
          if (msg.name === 'hwdec-current') { this.decoder = msg.data || 'no'; this._emitProgress(true); continue; }
          if (msg.name === 'video-codec') { this.codec = msg.data || null; continue; }
          this._emitProgress(false);
        }
      });
      sock.on('error', () => {
        sock.destroy();
        if (!this._pipe && attempts++ < 50) setTimeout(connect, 200);
      });
    };
    setTimeout(connect, 300);
  }

  // ── VLC: HTTP status interface ───────────────────────────────────────
  _playVlc(exe, opts, subs, session) {
    const password = crypto.randomBytes(12).toString('hex');
    const port = 41542;
    const args = [
      opts.url,
      '--fullscreen',
      '--play-and-exit',
      '--no-video-title-show',
      `--meta-title=${opts.title || 'LuminaStreams'}`,
      `--http-user-agent=${USER_AGENT}`,
      '--extraintf=http',
      '--http-host=127.0.0.1',
      `--http-port=${port}`,
      `--http-password=${password}`,
    ];
    if (opts.startSeconds > 1 && !opts.live) args.push(`--start-time=${Math.floor(opts.startSeconds)}`);
    if (subs[0]) args.push(`--sub-file=${subs[0]}`);

    const proc = spawn(exe, args, { stdio: 'ignore' });
    this.proc = proc;
    proc.on('exit', () => this._onExit(proc, session));
    proc.on('error', err => { this.emit('error', err.message); this._onExit(proc, session); });

    const auth = 'Basic ' + Buffer.from(':' + password).toString('base64');
    this._poll = setInterval(async () => {
      if (this.proc !== proc) return;
      try {
        const res = await fetch(`http://127.0.0.1:${port}/requests/status.json`, { headers: { Authorization: auth } });
        if (!res.ok) return;
        const s = await res.json();
        if (typeof s.time === 'number') this.position = s.time;
        if (typeof s.length === 'number' && s.length > 0) this.duration = s.length;
        this.paused = s.state === 'paused';
        this._emitProgress(true);
      } catch {
        // VLC still starting.
      }
    }, 1000);
  }
}

module.exports = { NativePlayer, findPlayers };
