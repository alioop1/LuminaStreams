'use strict';
/**
 * Native playback for the desktop app (the Stremio approach): the UI stays in the
 * web app, video goes to a real player that handles everything browsers cannot —
 * MKV, HEVC, AC3/EAC3/DTS/TrueHD, embedded SRT/ASS/PGS, raw MPEG-TS IPTV.
 *
 * mpv runs embedded (--wid) in the player window, under the Android-style
 * controls overlay (see playerWindow.js / overlay/). VLC is a fallback with its
 * own window. Both report position back so watch progress syncs everywhere.
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
 * Download a subtitle file and store it as UTF-8. Hebrew sources often serve
 * windows-1255 labelled as UTF-8, which mpv/VLC would otherwise garble.
 * @returns {Promise<string|null>} local file path
 */
async function prepareSubtitle(url) {
  const dir = path.join(os.tmpdir(), 'luminastreams-subs');
  fs.mkdirSync(dir, { recursive: true });
  try {
    const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT } });
    if (!res.ok) return null;
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
    return file;
  } catch {
    return null; // a missing subtitle must never block playback
  }
}

// Android subtitle sizes (small/medium/large/xlarge) → mpv sub-font-size
const SUB_SIZES = { small: 42, medium: 52, large: 62, xlarge: 74 };

class NativePlayer extends EventEmitter {
  constructor(locations) {
    super();
    this.locations = locations;
    this.proc = null;
    this.session = 0; // tags events so a closing player never ends a newer one
    this._poll = null;
    this._pipe = null;
    this._requests = new Map();
    this._requestId = 0;
    this._lastEmit = 0;
    this._opts = null;
    this._resetState();
  }

  _resetState() {
    this.state = {
      position: 0, duration: 0, paused: false, buffering: true, started: false,
      decoder: null, codec: null, width: 0, height: 0, fps: 0,
      tracks: [], subDelay: 0, speed: 1, volume: 100, muted: false,
    };
  }

  info() {
    const { mpv, vlc } = findPlayers(this.locations);
    return { available: !!(mpv || vlc), player: mpv ? 'mpv' : (vlc ? 'vlc' : null), mpv, vlc };
  }

  /**
   * @param {{url: string, title?: string, startSeconds?: number, startPaused?: boolean,
   *          subtitleUrls?: string[], live?: boolean, wid?: string,
   *          subStyle?: {yellow?: boolean, size?: string}}} opts
   */
  async play(opts) {
    this.stop();
    const session = ++this.session;
    const { mpv, vlc } = findPlayers(this.locations);
    if (!mpv && !vlc) throw new Error('No native player found');
    this._resetState();
    this._opts = opts;
    const subs = (await Promise.all((opts.subtitleUrls || []).slice(0, 2).map(prepareSubtitle))).filter(Boolean);
    if (session !== this.session) return { player: null, session }; // superseded meanwhile
    if (mpv) this._playMpv(mpv, opts, subs, session);
    else this._playVlc(vlc, opts, subs, session);
    return { player: mpv ? 'mpv' : 'vlc', session };
  }

  stop() {
    if (this._poll) { clearInterval(this._poll); this._poll = null; }
    if (this._pipe) { this._pipe.destroy(); this._pipe = null; }
    for (const { reject } of this._requests.values()) reject(new Error('Player stopped'));
    this._requests.clear();
    if (this.proc) {
      const proc = this.proc;
      this.proc = null;
      try { proc.kill(); } catch {}
    }
  }

  // ── Commands (mpv JSON IPC) ──────────────────────────────────────────
  command(...args) {
    if (!this._pipe) return Promise.reject(new Error('Player not ready'));
    const request_id = ++this._requestId;
    return new Promise((resolve, reject) => {
      this._requests.set(request_id, { resolve, reject });
      this._pipe.write(JSON.stringify({ command: args, request_id }) + '\n');
      setTimeout(() => {
        if (this._requests.delete(request_id)) reject(new Error('mpv did not answer'));
      }, 5000);
    });
  }

  setProperty(name, value) { return this.command('set_property', name, value); }
  getProperty(name) { return this.command('get_property', name); }

  /** Download a web subtitle (UTF-8) and add it as the active subtitle track. */
  async addSubtitle(url, title, lang) {
    const file = await prepareSubtitle(url);
    if (!file) throw new Error('Subtitle download failed');
    await this.command('sub-add', file, 'select', title || 'Web', lang || '');
    return file;
  }

  /** Reload the stream from the current position (error recovery). */
  async retry() {
    if (!this._opts) return;
    const pos = Math.floor(this.state.position || 0);
    await this.command('loadfile', this._opts.url, 'replace', -1, pos > 1 ? `start=${pos}` : '');
    this.state.buffering = true;
    this._emitState(true);
  }

  // ── Internal ─────────────────────────────────────────────────────────
  _emitState(force) {
    const now = Date.now();
    if (!force && now - this._lastEmit < 250) return;
    this._lastEmit = now;
    const s = this.state;
    this.emit('state', { session: this.session, ...s });
    // Compact progress for the app window (watch-progress sync).
    this.emit('progress', {
      session: this.session, position: s.position, duration: s.duration,
      paused: s.paused, decoder: s.decoder, codec: s.codec,
    });
  }

  _onExit(proc, session) {
    if (session !== this.session) return; // a newer play() owns the state now
    if (this._poll) { clearInterval(this._poll); this._poll = null; }
    if (this._pipe) { this._pipe.destroy(); this._pipe = null; }
    if (this.proc === proc) this.proc = null;
    this.emit('ended', { session, position: this.state.position, duration: this.state.duration });
  }

  _playMpv(exe, opts, subs, session) {
    const pipe = `\\\\.\\pipe\\luminastreams-mpv-${process.pid}-${Date.now()}`;
    const style = opts.subStyle || {};
    const args = [
      opts.url,
      `--force-media-title=${opts.title || 'LuminaStreams'}`,
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
      // Android subtitle look: yellow or white, bold, outlined with a soft shadow.
      `--sub-color=${style.yellow ? '#FFE600' : '#FFFFFF'}`,
      `--sub-font-size=${SUB_SIZES[style.size] || SUB_SIZES.medium}`,
      '--sub-bold=yes',
      '--sub-border-size=2.6',
      '--sub-shadow-offset=1.5',
      '--sub-shadow-color=#000000A0',
    ];
    if (opts.wid) {
      // Embedded under the HTML controls overlay: mpv draws video only.
      args.push(`--wid=${opts.wid}`, '--osc=no', '--osd-level=0', '--idle=yes',
        '--input-default-bindings=no', '--input-vo-keyboard=no', '--cursor-autohide=no');
    } else {
      args.push('--fullscreen', '--osc=yes', '--idle=no', '--keep-open=no');
    }
    if (opts.startPaused) args.push('--pause=yes');
    if (opts.startSeconds > 1 && !opts.live) args.push(`--start=${Math.floor(opts.startSeconds)}`);
    for (const file of subs) args.push(`--sub-file=${file}`);
    if (subs.length) args.push('--sid=auto');

    const proc = spawn(exe, args, { stdio: 'ignore', windowsHide: false });
    this.proc = proc;
    proc.on('exit', () => this._onExit(proc, session));
    proc.on('error', err => { this.emit('error', err.message); this._onExit(proc, session); });

    const OBSERVED = ['time-pos', 'duration', 'pause', 'paused-for-cache', 'hwdec-current', 'video-codec',
      'video-params/w', 'video-params/h', 'container-fps', 'track-list', 'sub-delay', 'speed', 'volume', 'mute'];

    let attempts = 0;
    const connect = () => {
      if (this.proc !== proc) return;
      const sock = net.connect(pipe);
      sock.on('connect', () => {
        this._pipe = sock;
        OBSERVED.forEach((name, i) => sock.write(JSON.stringify({ command: ['observe_property', i + 1, name] }) + '\n'));
        this.emit('ready', { session });
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
          this._handleMpvMessage(msg, session);
        }
      });
      sock.on('error', () => {
        sock.destroy();
        if (!this._pipe && attempts++ < 50) setTimeout(connect, 200);
      });
    };
    setTimeout(connect, 300);
  }

  _handleMpvMessage(msg, session) {
    if (msg.request_id && this._requests.has(msg.request_id)) {
      const { resolve, reject } = this._requests.get(msg.request_id);
      this._requests.delete(msg.request_id);
      if (msg.error === 'success') resolve(msg.data);
      else reject(new Error(msg.error));
      return;
    }
    const s = this.state;
    if (msg.event === 'property-change') {
      const v = msg.data;
      switch (msg.name) {
        case 'time-pos':
          if (typeof v === 'number') {
            s.position = v;
            if (v > 0) { s.started = true; s.buffering = false; }
          }
          break;
        case 'duration': if (typeof v === 'number') s.duration = v; break;
        case 'pause': s.paused = !!v; break;
        case 'paused-for-cache': s.buffering = !!v; break;
        case 'hwdec-current': s.decoder = v || 'no'; break;
        case 'video-codec': s.codec = v || null; break;
        case 'video-params/w': s.width = v || 0; break;
        case 'video-params/h': s.height = v || 0; break;
        case 'container-fps': s.fps = v || 0; break;
        case 'track-list':
          s.tracks = (v || []).filter(t => t.type === 'audio' || t.type === 'sub').map(t => ({
            id: t.id, type: t.type, lang: t.lang || '', title: t.title || '', codec: t.codec || '',
            channels: t['demux-channel-count'] || 0, selected: !!t.selected, external: !!t.external,
            externalFile: t['external-filename'] || '',
          }));
          break;
        case 'sub-delay': s.subDelay = v || 0; break;
        case 'speed': s.speed = v || 1; break;
        case 'volume': s.volume = v || 0; break;
        case 'mute': s.muted = !!v; break;
        default: break;
      }
      this._emitState(msg.name !== 'time-pos');
      return;
    }
    if (msg.event === 'end-file') {
      if (msg.reason === 'error') {
        this.emit('playback-error', { session, message: msg.file_error || 'Playback error' });
      } else if (msg.reason === 'eof') {
        this.emit('eof', { session });
      }
    }
  }

  // ── VLC: own window, HTTP status interface ───────────────────────────
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
        const st = await res.json();
        if (typeof st.time === 'number') this.state.position = st.time;
        if (typeof st.length === 'number' && st.length > 0) this.state.duration = st.length;
        this.state.paused = st.state === 'paused';
        this._emitState(true);
      } catch {
        // VLC still starting.
      }
    }, 1000);
  }
}

module.exports = { NativePlayer, findPlayers, prepareSubtitle };
