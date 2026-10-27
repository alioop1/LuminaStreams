/**
 * LuminaStreams — Global Constants
 * All API keys, base URLs, image helpers, genre maps, studio/network IDs
 */
(() => {
  'use strict';

  /* ── API Keys & Base URLs ─────────────────────────────────────────── */
  const TMDB_API_KEY   = '9ab4a284f0c028007b78925852196b79';
  const TMDB_BASE      = 'https://api.themoviedb.org/3';
  const OMDB_API_KEY   = 'e61dc86b';
  const OMDB_BASE      = 'https://www.omdbapi.com';
  const RD_BASE        = 'https://api.real-debrid.com/rest/1.0';
  const RD_CLIENT_ID   = 'X245A4XAIBGVM';
  const TORRENTIO_BASE = 'https://torrentio.strem.fun';

  /* ── TMDB Image Base URLs ─────────────────────────────────────────── */
  const IMAGE_BASE_W300     = 'https://image.tmdb.org/t/p/w300';
  const IMAGE_BASE_W500     = 'https://image.tmdb.org/t/p/w500';
  const IMAGE_BASE_W780     = 'https://image.tmdb.org/t/p/w780';
  const IMAGE_BASE_W1280    = 'https://image.tmdb.org/t/p/w1280';
  const IMAGE_BASE_ORIGINAL = 'https://image.tmdb.org/t/p/original';

  /* ── Image URL Helpers ────────────────────────────────────────────── */

  /**
   * Build a poster image URL (w500 by default).
   * @param {string|null} path  TMDB poster_path
   * @returns {string} Full URL or placeholder
   */
  function posterUrl(path) {
    if (!path) return 'assets/img/poster-placeholder.png';
    return `${IMAGE_BASE_W500}${path}`;
  }

  /**
   * Build a backdrop image URL (w1280 by default).
   * @param {string|null} path  TMDB backdrop_path
   * @returns {string} Full URL or placeholder
   */
  function backdropUrl(path) {
    if (!path) return 'assets/img/backdrop-placeholder.png';
    return `${IMAGE_BASE_W1280}${path}`;
  }

  /**
   * Build a profile/actor image URL (w300).
   * @param {string|null} path  TMDB profile_path
   * @returns {string}
   */
  function profileUrl(path) {
    if (!path) return 'assets/img/avatar-placeholder.png';
    return `${IMAGE_BASE_W300}${path}`;
  }

  /**
   * Build an original-quality image URL.
   * @param {string|null} path
   * @returns {string}
   */
  function originalUrl(path) {
    if (!path) return '';
    return `${IMAGE_BASE_ORIGINAL}${path}`;
  }

  /* ── Genre Maps (TMDB ID → Hebrew name) ───────────────────────────── */
  const MOVIE_GENRES = {
    28:    'אקשן',
    12:    'הרפתקאות',
    16:    'אנימציה',
    35:    'קומדיה',
    80:    'פשע',
    99:    'דוקומנטרי',
    18:    'דרמה',
    10751: 'משפחה',
    14:    'פנטזיה',
    36:    'היסטוריה',
    27:    'אימה',
    10402: 'מוזיקה',
    9648:  'מסתורין',
    10749: 'רומנטיקה',
    878:   'מדע בדיוני',
    10770: 'סרט טלוויזיה',
    53:    'מותחן',
    10752: 'מלחמה',
    37:    'מערבון',
  };

  const TV_GENRES = {
    10759: 'אקשן והרפתקאות',
    16:    'אנימציה',
    35:    'קומדיה',
    80:    'פשע',
    99:    'דוקומנטרי',
    18:    'דרמה',
    10751: 'משפחה',
    10762: 'ילדים',
    9648:  'מסתורין',
    10763: 'חדשות',
    10764: 'ריאליטי',
    10765: 'מדע בדיוני ופנטזיה',
    10766: 'סדרת אופרה',
    10767: 'טוק שואו',
    10768: 'מלחמה ופוליטיקה',
    37:    'מערבון',
  };

  /* ── Studios / Streaming Networks ─────────────────────────────────── */
  const STUDIOS = {
    netflix: {
      providerId: 8,
      networkId:  213,
      name:       'Netflix',
      logo:       'assets/img/studios/netflix.png',
    },
    apple: {
      providerId: 350,
      networkId:  2552,
      name:       'Apple TV+',
      logo:       'assets/img/studios/apple.png',
    },
    hbo: {
      providerId: '1899|384',
      networkId:  '49|3186',
      name:       'HBO / Max',
      logo:       'assets/img/studios/hbo.png',
    },
    amazon: {
      providerId: 119,
      networkId:  1024,
      name:       'Amazon Prime',
      logo:       'assets/img/studios/amazon.png',
    },
    disney: {
      providerId: '337|2739',
      networkId:  2739,
      name:       'Disney+',
      logo:       'assets/img/studios/disney.png',
    },
    paramount: {
      providerId: '531|1770',
      networkId:  '4330|67',
      name:       'Paramount+',
      logo:       'assets/img/studios/paramount.png',
    },
    hulu: {
      providerId: 15,
      networkId:  453,
      name:       'Hulu',
      logo:       'assets/img/studios/hulu.png',
    },
  };

  /* ── Subtitle Sources ─────────────────────────────────────────────── */
  const SUBTITLE_SOURCES = {
    wizdom: {
      name: 'Wizdom',
      base: 'https://4b139a4b7f94-wizdom-stremio-v2.baby-beamup.club',
      buildUrl(type, imdbId, season, episode) {
        const id = (type === 'series' && season != null && episode != null)
          ? `${imdbId}:${season}:${episode}`
          : imdbId;
        return `${this.base}/subtitles/${type}/${id}.json`;
      },
    },
    ktuvit: {
      name: 'Ktuvit',
      base: 'https://4b139a4b7f94-ktuvit-stremio.baby-beamup.club',
      buildUrl(type, imdbId, season, episode) {
        const id = (type === 'series' && season != null && episode != null)
          ? `${imdbId}:${season}:${episode}`
          : imdbId;
        return `${this.base}/subtitles/${type}/${id}.json`;
      },
    },
    opensubtitles: {
      name: 'OpenSubtitles',
      base: 'https://opensubtitles-v3.strem.io',
      buildUrl(type, imdbId, season, episode) {
        const id = (type === 'series' && season != null && episode != null)
          ? `${imdbId}:${season}:${episode}`
          : imdbId;
        return `${this.base}/subtitles/${type}/${id}.json`;
      },
    },
    opensubsCommunity: {
      name: 'OpenSubs Community',
      base: 'https://opensubtitles.stremio.homes/heb',
      buildUrl(type, imdbId, season, episode) {
        const id = (type === 'series' && season != null && episode != null)
          ? `${imdbId}:${season}:${episode}`
          : imdbId;
        return `${this.base}/subtitles/${type}/${id}.json`;
      },
    },
  };

  /* ── Quality Scoring ──────────────────────────────────────────────── */
  const QUALITY_SCORES = {
    '4k':    4,
    '2160p': 4,
    '1080p': 3,
    '720p':  2,
    '480p':  1,
    '360p':  0,
  };

  /* ── App Theme Colors ─────────────────────────────────────────────── */
  const THEME = {
    BG:     '#070B14',
    NAVY:   '#0D1525',
    CYAN:   '#00D4FF',
    PURPLE: '#7B61FF',
    RED:    '#FF3B5C',
    TEXT:   '#EEF2FF',
  };

  /* ── Export ────────────────────────────────────────────────────────── */
  window.CONST = Object.freeze({
    TMDB_API_KEY,
    TMDB_BASE,
    OMDB_API_KEY,
    OMDB_BASE,
    RD_BASE,
    RD_CLIENT_ID,
    TORRENTIO_BASE,

    IMAGE_BASE_W300,
    IMAGE_BASE_W500,
    IMAGE_BASE_W780,
    IMAGE_BASE_W1280,
    IMAGE_BASE_ORIGINAL,

    posterUrl,
    backdropUrl,
    profileUrl,
    originalUrl,

    MOVIE_GENRES,
    TV_GENRES,
    STUDIOS,
    SUBTITLE_SOURCES,
    QUALITY_SCORES,
    THEME,
  });
})();
