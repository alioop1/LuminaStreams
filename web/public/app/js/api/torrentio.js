/**
 * LuminaStreams — Torrentio API Client
 * Fetches, parses, and sorts streaming sources from Torrentio.
 */
(() => {
  'use strict';

  const TORRENTIO_BASE = window.CONST.TORRENTIO_BASE;

  /* ── Quality Score Map ────────────────────────────────────────────── */
  const QUALITY_MAP = {
    '4k':    4,
    '2160p': 4,
    'uhd':   4,
    '1080p': 3,
    '720p':  2,
    '480p':  1,
    '360p':  0,
  };

  /* ── Title Parsing ────────────────────────────────────────────────── */

  /**
   * Parse a Torrentio stream title to extract structured metadata.
   * @param {string} title  Raw stream title string
   * @returns {Object} { quality, codec, size, seeders, group, resolution, hdr, audio }
   */
  function parseStreamTitle(title) {
    if (!title) return {};

    const t = title;

    // Quality / Resolution
    let quality = null;
    if (/2160p|4k|uhd/i.test(t))      quality = '4K';
    else if (/1080p/i.test(t))         quality = '1080p';
    else if (/720p/i.test(t))          quality = '720p';
    else if (/480p/i.test(t))          quality = '480p';
    else if (/360p/i.test(t))          quality = '360p';

    // Codec
    let codec = null;
    if (/hevc|h\.?265|x\.?265/i.test(t))  codec = 'HEVC';
    else if (/h\.?264|x\.?264/i.test(t))  codec = 'x264';
    else if (/av1/i.test(t))               codec = 'AV1';
    else if (/xvid/i.test(t))              codec = 'XviD';
    else if (/vp9/i.test(t))               codec = 'VP9';

    // File size — look for patterns like "1.5 GB", "800 MB", "💾 2.3 GB"
    let size = null;
    const sizeMatch = t.match(/([\d.]+)\s*(GB|MB|TB)/i);
    if (sizeMatch) {
      const num  = parseFloat(sizeMatch[1]);
      const unit = sizeMatch[2].toUpperCase();
      size = `${num} ${unit}`;
    }

    // Size in bytes (for sorting)
    let sizeBytes = 0;
    if (sizeMatch) {
      const num  = parseFloat(sizeMatch[1]);
      const unit = sizeMatch[2].toUpperCase();
      if (unit === 'TB') sizeBytes = num * 1024 * 1024 * 1024 * 1024;
      else if (unit === 'GB') sizeBytes = num * 1024 * 1024 * 1024;
      else if (unit === 'MB') sizeBytes = num * 1024 * 1024;
    }

    // Seeders — look for "👤 123" or "S:123" or just numbers after seeders context
    let seeders = null;
    const seederMatch = t.match(/👤\s*(\d+)/);
    if (seederMatch) {
      seeders = parseInt(seederMatch[1], 10);
    }

    // Release group — usually at the end after a dash
    let group = null;
    const groupMatch = t.match(/-\s*([A-Za-z0-9]+)(?:\s|$|\[)/);
    if (groupMatch) {
      group = groupMatch[1];
    }

    // HDR
    let hdr = null;
    if (/dolby\s*vision|dv/i.test(t))  hdr = 'DV';
    else if (/hdr10\+/i.test(t))       hdr = 'HDR10+';
    else if (/hdr10/i.test(t))         hdr = 'HDR10';
    else if (/hdr/i.test(t))           hdr = 'HDR';

    // Audio
    let audio = null;
    let audioChannels = null;
    let isWebAudio = false;

    if (/\batmos\b/i.test(t)) {
      audio = 'Atmos';
    } else if (/truehd/i.test(t)) {
      audio = 'TrueHD';
    } else if (/dts[\s-]?hd|dts[\s-]?ma/i.test(t)) {
      audio = 'DTS-HD';
    } else if (/\bdts\b/i.test(t)) {
      audio = 'DTS';
    } else if (/eac-?3|ddp|dd\+|dolby\s*digital\s*plus/i.test(t)) {
      audio = 'EAC3';
      isWebAudio = false;
    } else if (/dd[\s._-]?5[\s._-]?1|ac-?3|dolby\s*digital/i.test(t)) {
      audio = 'DD5.1';
      isWebAudio = false;
    } else if (/\baac\b|\baac2\.0\b|\baac5\.1\b/i.test(t)) {
      audio = 'AAC';
      isWebAudio = true;
    } else if (/\bmp3\b/i.test(t)) {
      audio = 'MP3';
      isWebAudio = true;
    } else if (/\bopus\b/i.test(t)) {
      audio = 'Opus';
      isWebAudio = true;
    } else if (/\bflac\b/i.test(t)) {
      audio = 'FLAC';
      isWebAudio = true;
    }

    if (/7[\s._-]1/i.test(t))                audioChannels = '7.1';
    else if (/5[\s._-]1/i.test(t))           audioChannels = '5.1';
    else if (/2[\s._-]0|stereo/i.test(t))    audioChannels = '2.0';

    // Auto-detect AAC / Web-compatible audio from release groups or web formats
    if (!audio) {
      if (/\b(yify|yts|psa|galaxyrg|pahe|megusta|bone|bishu|elite|fenix)\b/i.test(t) || /⚙️\s*yts/i.test(t)) {
        audio = 'AAC';
        isWebAudio = true;
      } else if (/\.mp4\b|\bmp4\b/i.test(t) && !/\bremux|bluray|dts|truehd|ddp|eac3|ac3\b/i.test(t)) {
        audio = 'AAC';
        isWebAudio = true;
      }
    }

    // Only verified web codecs work in Chrome without silence
    isWebAudio = ['AAC', 'Opus', 'MP3', 'FLAC'].includes(audio);

    // Detect Languages: Strictly English ('eng'), Russian ('rus'), Hebrew ('heb')
    const fullText = t;
    const lower = t.toLowerCase();
    const languages = [];

    // Hebrew detection
    if (/🇮🇱/i.test(fullText) || /[א-ת]/.test(fullText) || /\b(heb|hebrew|מדובב|עברית)\b/i.test(lower)) {
      languages.push('heb');
    }

    // Russian detection
    if (/🇷🇺/i.test(fullText) || /[а-яё]/i.test(fullText) || /\b(rus|russian|mvo|dvo|avo|dub|дублир|мво|дво|lostfilm|hdrezka|newstudio)\b/i.test(lower)) {
      languages.push('rus');
    }

    // Foreign-only check (discard Spanish/French/German/Italian/Hindi if neither Eng/Rus/Heb)
    const isForeignOnly = (/🇫🇷|🇪🇸|🇩🇪|🇮🇹|🇮🇳|🇹🇷|🇵🇱|🇯🇵|🇨🇳|🇸🇦/i.test(fullText) ||
      /\b(vf|vff|vostfr|french|francais|castellano|espanol|spanish|german|deutsch|italian|ita|hindi)\b/i.test(lower)) &&
      !/\b(eng|english|original|multi|dual)\b/i.test(lower);

    // English detection (default language)
    if (/🇬🇧|🇺🇸/i.test(fullText) || /\b(eng|english|yify|original)\b/i.test(lower)) {
      languages.push('eng');
    } else if (!isForeignOnly) {
      languages.push('eng');
    }

    // Filename — take everything before the first newline
    const filename = t.split('\n')[0]?.trim() || t;

    return {
      quality,
      codec,
      size,
      sizeBytes,
      seeders,
      group,
      hdr,
      audio,
      audioChannels,
      isWebAudio,
      languages,
      filename,
    };
  }

  /**
   * Extract the info hash from a Torrentio stream object.
   * The hash is usually embedded in the infoHash property or the URL.
   * @param {Object} stream  Raw stream object from Torrentio
   * @returns {string|null}
   */
  function extractHash(stream) {
    if (stream.infoHash) return stream.infoHash.toLowerCase();

    // Try to extract from the URL
    if (stream.url) {
      const m = stream.url.match(/\/([a-fA-F0-9]{40})\//);
      if (m) return m[1].toLowerCase();
    }

    // Try from behaviorHints
    if (stream.behaviorHints?.infoHash) {
      return stream.behaviorHints.infoHash.toLowerCase();
    }

    return null;
  }

  /**
   * Compute a quality score for sorting.
   * In Web browser mode, prioritize AAC/compatible audio to avoid muted/silent playback.
   * @param {Object} parsed  Parsed metadata
   * @returns {number}
   */
  function qualityScore(parsed) {
    let score = 0;
    const isWeb = !window.electronAPI;

    // Base quality
    if (parsed.quality) {
      const q = parsed.quality.toLowerCase();
      score += (QUALITY_MAP[q] || 0) * 100;
    }

    // Default language preference: English gets a solid boost
    if (parsed.languages && parsed.languages.includes('eng')) {
      score += 50;
    }

    // HDR bonus
    if (parsed.hdr) score += 20;

    // Codec preference (HEVC is more efficient)
    if (parsed.codec === 'HEVC') score += 10;
    else if (parsed.codec === 'AV1') score += 12;
    else if (parsed.codec === 'x264') score += 5;

    // Audio scoring:
    // In browsers, Chrome/Edge/Firefox cannot decode AC3, EAC3, DTS or TrueHD and will play 100% SILENTLY.
    // Give verified Web Audio (AAC) a massive boost (+2000) so Chrome users ALWAYS get loud, working sound!
    if (isWeb) {
      if (parsed.isWebAudio || parsed.audio === 'AAC' || parsed.audio === 'MP3' || parsed.audio === 'Opus') {
        score += 2000;
      } else if (['Atmos', 'TrueHD', 'DTS', 'DTS-HD', 'DD5.1', 'EAC3'].includes(parsed.audio)) {
        score -= 1000; // Heavy penalty on Web so silent streams are never picked first!
      } else {
        score -= 200;
      }
    } else {
      if (parsed.audio === 'Atmos') score += 8;
      else if (parsed.audio === 'TrueHD') score += 6;
      else if (parsed.audio === 'DTS-HD') score += 5;
    }

    return score;
  }

  /* ── API ──────────────────────────────────────────────────────────── */

  /**
   * Fetch available streams from Torrentio for a given media.
   * Filtered strictly to English, Russian, and Hebrew!
   *
   * @param {'movie'|'series'} type
   * @param {string} imdbId         e.g. 'tt1234567' or 'tt1234567:2:5' for S02E05
   * @returns {Promise<Array<Object>>}  Sorted array of StreamSource objects
   */
  async function getStreams(type, imdbId) {
    if (!imdbId) return [];

    const adToken = (window.AllDebrid && window.AllDebrid.getToken()) || 'GkRRCMwdGBSvjzWqnNP8';
    const url = `${TORRENTIO_BASE}/alldebrid=${adToken}/stream/${type}/${imdbId}.json`;

    try {
      const res = await fetch(url);

      if (!res.ok) {
        if (res.status === 404) return [];
        throw new Error(`Torrentio ${res.status}: ${res.statusText}`);
      }

      const data = await res.json();
      const rawStreams = data.streams || [];

      if (rawStreams.length === 0) return [];

      // Parse and enrich each stream
      const parsed = rawStreams.map((stream, index) => {
        const title = stream.title || stream.name || '';
        const meta  = parseStreamTitle(title);
        const hash  = extractHash(stream);
        const name  = stream.name || meta.filename || 'Unknown';
        const isCached = name.includes('AD+') || name.includes('RD+') || !!stream.url;

        return {
          id:          `stream-${index}-${hash || index}`,
          title:       title,
          name:        name,
          url:         stream.url || null,
          infoHash:    hash,
          fileIdx:     stream.fileIdx ?? null,
          quality:     meta.quality || 'Unknown',
          codec:       meta.codec,
          size:        meta.size,
          sizeBytes:   meta.sizeBytes,
          seeders:     meta.seeders,
          group:       meta.group,
          hdr:         meta.hdr,
          audio:       meta.audio,
          audioChannels: meta.audioChannels,
          isWebAudio:  meta.isWebAudio,
          languages:   meta.languages || ['eng'],
          filename:    meta.filename,
          score:       qualityScore(meta),
          isCached:    isCached,
          behaviorHints: stream.behaviorHints || {},
        };
      });

      // Filter out streams that do not have English, Russian, or Hebrew
      const validStreams = parsed.filter(s => s.languages && s.languages.length > 0);

      // Sort: cached first, then by quality score (desc), then by size (desc)
      validStreams.sort((a, b) => {
        // Cached items first
        if (a.isCached !== b.isCached) return a.isCached ? -1 : 1;
        // Higher quality score first
        if (b.score !== a.score) return b.score - a.score;
        // Larger file size first
        return (b.sizeBytes || 0) - (a.sizeBytes || 0);
      });

      return validStreams;

    } catch (err) {
      console.error('[Torrentio] getStreams failed:', err);
      throw err;
    }
  }

  /**
   * Get streams for a TV episode.
   * @param {string} imdbId   Show IMDb ID
   * @param {number} season   Season number
   * @param {number} episode  Episode number
   * @returns {Promise<Array>}
   */
  function getEpisodeStreams(imdbId, season, episode) {
    return getStreams('series', `${imdbId}:${season}:${episode}`);
  }

  /* ── Export ────────────────────────────────────────────────────────── */
  window.Torrentio = Object.freeze({
    getStreams,
    getEpisodeStreams,
    parseStreamTitle,
  });
})();
