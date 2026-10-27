/**
 * LuminaStreams — Subtitle Aggregator
 * Fetches subtitles from 4 sources in parallel, deduplicates,
 * and returns sorted results (Hebrew first, then English).
 */
(() => {
  'use strict';

  /* ── Source Definitions ───────────────────────────────────────────── */

  const SOURCES = [
    {
      name: 'Wizdom',
      base: 'https://4b139a4b7f94-wizdom-stremio-v2.baby-beamup.club',
    },
    {
      name: 'Ktuvit',
      base: 'https://4b139a4b7f94-ktuvit-stremio.baby-beamup.club',
    },
    {
      name: 'OpenSubtitles',
      base: 'https://opensubtitles-v3.strem.io',
    },
    {
      name: 'OpenSubs Community',
      base: 'https://opensubtitles.stremio.homes/heb',
    },
  ];

  /* ── Language Sorting Priority ────────────────────────────────────── */
  const LANG_PRIORITY = {
    'heb': 0, 'he':  0, 'hebrew': 0,
    'eng': 1, 'en':  1, 'english': 1,
    'ara': 2, 'ar':  2, 'arabic': 2,
    'rus': 3, 'ru':  3, 'russian': 3,
  };

  /**
   * Build the subtitle URL for a source.
   * @param {Object} source  Source definition { name, base }
   * @param {'movie'|'series'} type
   * @param {string} imdbId
   * @param {number|null} season
   * @param {number|null} episode
   * @returns {string}
   */
  function buildUrl(source, type, imdbId, season, episode) {
    const id = (type === 'series' && season != null && episode != null)
      ? `${imdbId}:${season}:${episode}`
      : imdbId;
    return `${source.base}/subtitles/${type}/${id}.json`;
  }

  /**
   * Fetch subtitles from a single source.
   * Returns an empty array on failure (never throws).
   * @param {Object} source
   * @param {'movie'|'series'} type
   * @param {string} imdbId
   * @param {number|null} season
   * @param {number|null} episode
   * @returns {Promise<Array<{ language: string, url: string, source: string, lang3: string }>>}
   */
  async function fetchFromSource(source, type, imdbId, season, episode) {
    const url = buildUrl(source, type, imdbId, season, episode);

    try {
      const controller = new AbortController();
      const timeoutId  = setTimeout(() => controller.abort(), 10000); // 10s timeout

      const res = await fetch(url, { signal: controller.signal });
      clearTimeout(timeoutId);

      if (!res.ok) return [];

      const data = await res.json();
      const subtitles = data.subtitles || [];

      return subtitles.map(sub => {
        // Normalize the subtitle object
        const lang3 = normalizeLanguage(sub.lang || sub.language || 'unknown');
        const langDisplay = getLanguageDisplayName(lang3);

        return {
          id:       sub.id || `${source.name}-${lang3}-${Math.random().toString(36).slice(2, 8)}`,
          language: langDisplay,
          lang3:    lang3,
          url:      sub.url || sub.SubDownloadLink || '',
          source:   source.name,
          rating:   sub.SubRating || sub.rating || 0,
          encoding: sub.SubEncoding || sub.encoding || 'UTF-8',
          fps:      sub.SubFps || sub.fps || null,
          filename: sub.SubFileName || sub.filename || '',
        };
      }).filter(sub => sub.url); // Only keep subs with valid URLs

    } catch (err) {
      if (err.name !== 'AbortError') {
        console.warn(`[Subtitles] ${source.name} fetch failed:`, err.message);
      }
      return [];
    }
  }

  /**
   * Normalize a language code to ISO 639-3 (3-letter).
   * @param {string} lang
   * @returns {string}
   */
  function normalizeLanguage(lang) {
    if (!lang) return 'unknown';
    const l = lang.toLowerCase().trim();

    const map = {
      'he': 'heb', 'hebrew': 'heb', 'heb': 'heb', 'עברית': 'heb',
      'en': 'eng', 'english': 'eng', 'eng': 'eng',
      'ar': 'ara', 'arabic': 'ara', 'ara': 'ara',
      'ru': 'rus', 'russian': 'rus', 'rus': 'rus',
      'fr': 'fre', 'french': 'fre', 'fre': 'fre',
      'es': 'spa', 'spanish': 'spa', 'spa': 'spa',
      'de': 'ger', 'german': 'ger', 'ger': 'ger',
      'pt': 'por', 'portuguese': 'por', 'por': 'por',
      'it': 'ita', 'italian': 'ita', 'ita': 'ita',
      'tr': 'tur', 'turkish': 'tur', 'tur': 'tur',
      'pl': 'pol', 'polish': 'pol', 'pol': 'pol',
      'nl': 'dut', 'dutch': 'dut', 'dut': 'dut',
      'ja': 'jpn', 'japanese': 'jpn', 'jpn': 'jpn',
      'ko': 'kor', 'korean': 'kor', 'kor': 'kor',
      'zh': 'chi', 'chinese': 'chi', 'chi': 'chi',
      'ro': 'rum', 'romanian': 'rum', 'rum': 'rum',
      'cs': 'cze', 'czech': 'cze', 'cze': 'cze',
      'hu': 'hun', 'hungarian': 'hun', 'hun': 'hun',
    };

    return map[l] || l;
  }

  /**
   * Get the display name for a language code.
   * @param {string} lang3
   * @returns {string}
   */
  function getLanguageDisplayName(lang3) {
    const names = {
      'heb': 'עברית',
      'eng': 'English',
      'ara': 'العربية',
      'rus': 'Русский',
      'fre': 'Français',
      'spa': 'Español',
      'ger': 'Deutsch',
      'por': 'Português',
      'ita': 'Italiano',
      'tur': 'Türkçe',
      'pol': 'Polski',
      'dut': 'Nederlands',
      'jpn': '日本語',
      'kor': '한국어',
      'chi': '中文',
      'rum': 'Română',
      'cze': 'Čeština',
      'hun': 'Magyar',
    };
    return names[lang3] || lang3;
  }

  /* ── Main API ─────────────────────────────────────────────────────── */

  /**
   * Fetch subtitles from all 4 sources in parallel, deduplicate, and sort.
   *
   * @param {'movie'|'series'} type
   * @param {string} imdbId   e.g. 'tt1234567'
   * @param {number|null} [season]   Season number (for TV)
   * @param {number|null} [episode]  Episode number (for TV)
   * @returns {Promise<Array<{ id, language, lang3, url, source, rating, encoding, fps, filename }>>}
   */
  async function fetchAll(type, imdbId, season = null, episode = null) {
    if (!imdbId) return [];

    // Fetch from all sources in parallel
    const results = await Promise.allSettled(
      SOURCES.map(source => fetchFromSource(source, type, imdbId, season, episode))
    );

    // Flatten all successful results
    let allSubs = [];
    results.forEach(result => {
      if (result.status === 'fulfilled' && Array.isArray(result.value)) {
        allSubs = allSubs.concat(result.value);
      }
    });

    // Deduplicate by URL (keep first occurrence, which preserves source priority)
    const seen = new Set();
    const deduped = [];

    allSubs.forEach(sub => {
      const key = sub.url.toLowerCase();
      if (!seen.has(key)) {
        seen.add(key);
        deduped.push(sub);
      }
    });

    // Sort: Hebrew first, then English, then others; within same language, by rating desc
    deduped.sort((a, b) => {
      const priorityA = LANG_PRIORITY[a.lang3] ?? 99;
      const priorityB = LANG_PRIORITY[b.lang3] ?? 99;

      if (priorityA !== priorityB) return priorityA - priorityB;

      // Same language — sort by rating (higher is better)
      return (b.rating || 0) - (a.rating || 0);
    });

    return deduped;
  }

  /**
   * Fetch subtitles only in a specific language.
   * @param {'movie'|'series'} type
   * @param {string} imdbId
   * @param {string} langCode  e.g. 'heb', 'eng'
   * @param {number|null} season
   * @param {number|null} episode
   * @returns {Promise<Array>}
   */
  async function fetchByLanguage(type, imdbId, langCode, season = null, episode = null) {
    const all = await fetchAll(type, imdbId, season, episode);
    const normalized = normalizeLanguage(langCode);
    return all.filter(sub => sub.lang3 === normalized);
  }

  /**
   * Fetch only Hebrew subtitles (convenience method).
   * @param {'movie'|'series'} type
   * @param {string} imdbId
   * @param {number|null} season
   * @param {number|null} episode
   * @returns {Promise<Array>}
   */
  function fetchHebrew(type, imdbId, season = null, episode = null) {
    return fetchByLanguage(type, imdbId, 'heb', season, episode);
  }

  /**
   * Get available language list from fetched subtitles.
   * @param {Array} subtitles  Result from fetchAll
   * @returns {Array<{ lang3, name, count }>}
   */
  function getAvailableLanguages(subtitles) {
    const langMap = {};

    subtitles.forEach(sub => {
      if (!langMap[sub.lang3]) {
        langMap[sub.lang3] = {
          lang3: sub.lang3,
          name:  sub.language,
          count: 0,
        };
      }
      langMap[sub.lang3].count++;
    });

    return Object.values(langMap).sort((a, b) => {
      const pA = LANG_PRIORITY[a.lang3] ?? 99;
      const pB = LANG_PRIORITY[b.lang3] ?? 99;
      return pA - pB;
    });
  }

  /* ── Export ────────────────────────────────────────────────────────── */
  window.Subtitles = Object.freeze({
    fetchAll,
    fetchByLanguage,
    fetchHebrew,
    getAvailableLanguages,
    SOURCES,
  });
})();
