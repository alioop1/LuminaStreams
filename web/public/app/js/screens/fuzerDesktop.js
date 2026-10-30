/**
 * LuminaStreams — Fuzer screen for the Windows desktop app.
 * The desktop shell (window.electronAPI.fuzer) fetches Fuzer pages with the
 * user's own session — sign-in happens in Fuzer's login window (reCAPTCHA).
 * This screen parses the pages like Android's FuzerEngine and plays releases
 * through AllDebrid (torrent file upload) and the native player.
 */
(() => {
  'use strict';

  const { tr, escapeHtml } = window.Helpers;

  const CATEGORIES = [
    { id: 7,  en: 'HD Movies',        he: 'סרטים HD' },
    { id: 9,  en: '4K / Bluray',      he: 'סרטים 4K / Bluray' },
    { id: 60, en: 'Dubbed Movies',    he: 'סרטים מדובבים' },
    { id: 8,  en: 'HD Series',        he: 'סדרות HD' },
    { id: 61, en: 'Israeli Series',   he: 'סדרות ישראליות' },
  ];
  const BASE = 'https://www.fuzer.xyz';

  let container = null;
  let catId = 7;
  let query = '';
  let items = [];
  let loadToken = 0;

  const api = () => window.electronAPI.fuzer;

  /* ── Parser (port of FuzerEngine.parseHtmlToMovies) ─────────────────── */
  function parse(html) {
    const doc = new DOMParser().parseFromString(html, 'text/html');
    let rows = [...doc.querySelectorAll('tr.trow1, tr.trow2, tr.torrent_row')];
    if (!rows.length) rows = [...doc.querySelectorAll('table.torrents tr')].slice(1);
    if (!rows.length) rows = [...doc.querySelectorAll('tr')].filter(r => r.querySelector('a[href*="showthread"]'));

    const cutRe = /(\b(19|20)\d{2}\b|\bS\d+E?|\bSeason|\b720p|\b1080p|\b2160p|\b4K\b)/i;
    const seasonRe = /(?:S|Season|עונה)\s*(\d{1,2})/i;
    const seen = new Set();
    const out = [];

    for (const row of rows) {
      const link = row.querySelector('a[href*="showthread"]');
      if (!link) continue;
      const rawTitle = link.textContent.trim();
      if (rawTitle.length < 2) continue;
      const href = (link.getAttribute('href') || '').replace(/&amp;/g, '&');
      const url = href.startsWith('http') ? href : `${BASE}/${href.replace(/^\//, '')}`;
      if (seen.has(url)) continue;
      seen.add(url);

      let poster = (link.getAttribute('imgsrc') || '').trim();
      if (!poster) poster = row.querySelector('img[src]')?.getAttribute('src') || '';
      if (poster && !poster.startsWith('http')) poster = `${BASE}/${poster.replace(/^\//, '')}`;

      const quality = /2160p|4K/i.test(rawTitle) ? '4K' : /1080p/i.test(rawTitle) ? 'FHD' : /720p/i.test(rawTitle) ? 'HD' : '';

      let clean = rawTitle;
      const cut = clean.search(cutRe);
      if (cut > 0) clean = clean.slice(0, cut);
      clean = clean.replace(/[._-]/g, ' ')
        .replace(/(HebDub|Dubbed|Remux|KNiVES|SPARKS|BluRay|WEB-DL|HDRip|BRRip|DVDRip)/gi, '')
        .replace(/[^\p{L}\p{N}\s]/gu, '')
        .replace(/\s+/g, ' ').trim();
      if (clean.length < 2) clean = rawTitle.slice(0, 60);

      const tds = [...row.children].filter(c => c.tagName === 'TD');
      const num = el => parseInt((el?.textContent || '').replace(/,/g, '').trim(), 10);
      const seeders = num(tds[5]) || num(row.querySelector('td.r22')) || 0;
      const leechers = num(tds[6]) || num(row.querySelector('td.r23')) || 0;
      // One decimal only: textContent runs cells together ("8.6" + "58.2 GB").
      const rating = parseFloat((row.textContent.match(/IMDB:\s*(\d{1,2}(?:\.\d)?)/) || [])[1]) || 0;

      out.push({
        url, title: clean, rawTitle, poster, quality, rating, seeders, leechers,
        size: (tds[3]?.textContent || '').trim(),
        isSeries: seasonRe.test(rawTitle) || rawTitle.includes('עונה'),
      });
    }
    return out;
  }

  /* ── Rendering ───────────────────────────────────────────────────────── */
  function shell() {
    container.innerHTML = `
      <div class="page fuzer-desktop">
        <div class="fuzer-desktop-head">
          <h1 class="fuzer-desktop-title">✨ Fuzer</h1>
          <form class="fuzer-desktop-search" id="fz-search">
            <input id="fz-query" type="search" placeholder="${tr('Search Fuzer…', 'חפש ב-Fuzer…')}" value="${escapeHtml(query)}">
            <button class="ps5-btn-glass" type="submit">${tr('Search', 'חפש')}</button>
          </form>
          <button class="ps5-btn-glass" id="fz-signout">${tr('Sign out', 'התנתק')}</button>
        </div>
        <div class="ps5-sources-filters" id="fz-cats">
          ${CATEGORIES.map(c => `<button class="ps5-filter-pill${!query && c.id === catId ? ' active' : ''}" data-cat="${c.id}">${tr(c.en, c.he)}</button>`).join('')}
        </div>
        <div class="fuzer-desktop-list" id="fz-list"></div>
      </div>
    `;
    container.querySelector('#fz-search').addEventListener('submit', e => {
      e.preventDefault();
      query = container.querySelector('#fz-query').value.trim();
      shell();
      load();
    });
    container.querySelectorAll('[data-cat]').forEach(btn => btn.addEventListener('click', () => {
      catId = Number(btn.dataset.cat);
      query = '';
      shell();
      load();
    }));
    container.querySelector('#fz-signout').addEventListener('click', async () => {
      await api().signOut();
      renderSignIn();
    });
  }

  function setList(html) {
    const list = container && container.querySelector('#fz-list');
    if (list) list.innerHTML = html;
  }

  function renderItems() {
    if (!items.length) {
      setList(`<div class="ps5-sources-empty">${tr('Nothing found.', 'לא נמצאו תוצאות.')}</div>`);
      return;
    }
    setList(items.map((it, i) => `
      <button class="fuzer-desktop-row" data-idx="${i}">
        ${it.poster ? `<img class="fuzer-desktop-poster" src="${escapeHtml(it.poster)}" alt="" loading="lazy">` : '<div class="fuzer-desktop-poster"></div>'}
        <div class="fuzer-desktop-info">
          <div class="fuzer-desktop-name">${escapeHtml(it.title)}</div>
          <div class="fuzer-desktop-raw">${escapeHtml(it.rawTitle)}</div>
          <div class="fuzer-desktop-meta">
            ${it.quality ? `<span class="fuzer-badge">${it.quality}</span>` : ''}
            ${it.isSeries ? `<span class="fuzer-badge alt">${tr('Series', 'סדרה')}</span>` : ''}
            ${it.size ? `<span>💾 ${escapeHtml(it.size)}</span>` : ''}
            <span class="up">▲ ${it.seeders}</span><span class="down">▼ ${it.leechers}</span>
            ${it.rating ? `<span>IMDb ${it.rating}</span>` : ''}
          </div>
        </div>
        <span class="fuzer-desktop-play" data-status>▶ ${tr('Play', 'צפה')}</span>
      </button>
    `).join(''));
    container.querySelectorAll('.fuzer-desktop-row').forEach(row => {
      row.addEventListener('click', () => play(items[Number(row.dataset.idx)], row));
    });
  }

  function renderSignIn() {
    container.innerHTML = `
      <div class="page" style="max-width:560px;margin:14vh auto 0;padding:0 20px;text-align:center;">
        <div style="font-size:44px;margin-bottom:12px;">✨</div>
        <h1 style="font-size:22px;font-weight:900;margin-bottom:10px;">Fuzer</h1>
        <p style="color:rgba(255,255,255,0.75);line-height:1.7;font-size:15px;margin-bottom:22px;">
          ${tr('Fuzer requires signing in (with reCAPTCHA). Sign in once and the app remembers you.',
               'Fuzer דורש התחברות (כולל אימות reCAPTCHA). התחבר פעם אחת והאפליקציה תזכור אותך.')}
        </p>
        <button class="ps5-btn-play" id="fz-signin" style="margin:0 auto;">${tr('Sign in to Fuzer', 'התחבר ל-Fuzer')}</button>
      </div>
    `;
    container.querySelector('#fz-signin').addEventListener('click', async () => {
      if (await api().signIn()) {
        shell();
        load();
      }
    });
  }

  /* ── Data ────────────────────────────────────────────────────────────── */
  async function load() {
    const token = ++loadToken;
    setList(`<div class="ps5-sources-loading"><div class="ps5-spinner"></div><span>${tr('Loading…', 'טוען…')}</span></div>`);
    try {
      // Fuzer search expects windows-1255 percent-encoding for Hebrew.
      const path = query
        ? `/browse.php?ref_=basic&matchquery=any&query=${encodeWin1255(query)}`
        : `/browse.php?cat=${catId}&page=1`;
      const page = await api().page(path);
      if (token !== loadToken || !container) return;
      if (!page.signedIn) { renderSignIn(); return; }
      items = parse(page.html);
      renderItems();
    } catch (err) {
      if (token === loadToken) setList(`<div class="ps5-sources-empty">${escapeHtml(err.message)}</div>`);
    }
  }

  /** Percent-encode text as windows-1255 (Android FuzerEngine does the same). */
  function encodeWin1255(text) {
    let out = '';
    for (const ch of text) {
      const code = ch.codePointAt(0);
      if (code >= 0x05D0 && code <= 0x05EA) out += '%' + (code - 0x05D0 + 0xE0).toString(16).toUpperCase();
      else out += encodeURIComponent(ch);
    }
    return out;
  }

  async function play(item, row) {
    const status = row.querySelector('[data-status]');
    const setStatus = text => { if (status) status.textContent = text; };
    row.disabled = true;
    try {
      setStatus(tr('Downloading torrent…', 'מוריד טורנט…'));
      const bytes = await api().torrent(item.url);
      const url = await window.AllDebrid.resolveTorrentFile(bytes, setStatus);
      window.Router.navigate('player', {
        url,
        title: item.title,
        showTitle: item.title,
        type: item.isSeries ? 'series' : 'movie',
        poster: item.poster,
      });
    } catch (err) {
      setStatus(`⚠ ${err.message}`);
      row.disabled = false;
    }
  }

  /* ── Lifecycle ───────────────────────────────────────────────────────── */
  async function render(el) {
    container = el;
    container.innerHTML = `<div class="ps5-sources-loading" style="margin-top:20vh"><div class="ps5-spinner"></div></div>`;
    const { signedIn } = await api().status();
    if (!container) return;
    if (!signedIn) { renderSignIn(); return; }
    shell();
    load();
  }

  function destroy() {
    container = null;
    loadToken++;
  }

  window.FuzerDesktop = Object.freeze({ render, destroy, parse });
})();
