/**
 * LuminaStreams — OMDB API Client
 * Fetches ratings (IMDb, Rotten Tomatoes, Metacritic) by IMDb ID.
 */
(() => {
  'use strict';

  const API_KEY = window.CONST.OMDB_API_KEY;
  const BASE    = window.CONST.OMDB_BASE;

  /* ── In-memory cache to avoid redundant calls ─────────────────────── */
  const cache = new Map();
  const CACHE_TTL = 30 * 60 * 1000; // 30 minutes

  /**
   * Fetch ratings for a given IMDb ID.
   * Returns an object with normalised values from all three sources.
   *
   * @param {string} imdbId  e.g. 'tt1234567'
   * @returns {Promise<{ imdb: string|null, rottenTomatoes: string|null, metacritic: string|null, raw: Object }>}
   */
  async function getRatings(imdbId) {
    if (!imdbId) {
      return { imdb: null, rottenTomatoes: null, metacritic: null, raw: null };
    }

    // Check cache
    const cached = cache.get(imdbId);
    if (cached && Date.now() - cached.ts < CACHE_TTL) {
      return cached.data;
    }

    const url = `${BASE}/?apikey=${API_KEY}&i=${encodeURIComponent(imdbId)}&plot=short`;

    try {
      const res = await fetch(url);

      if (!res.ok) {
        throw new Error(`OMDB ${res.status}: ${res.statusText}`);
      }

      const data = await res.json();

      if (data.Response === 'False') {
        console.warn(`[OMDB] No data for ${imdbId}: ${data.Error}`);
        return { imdb: null, rottenTomatoes: null, metacritic: null, raw: null };
      }

      // Parse the Ratings array
      let imdbRating     = null;
      let rottenTomatoes = null;
      let metacritic     = null;

      // Direct fields
      if (data.imdbRating && data.imdbRating !== 'N/A') {
        imdbRating = data.imdbRating;
      }

      if (data.Metascore && data.Metascore !== 'N/A') {
        metacritic = data.Metascore;
      }

      // Ratings array (more detailed)
      if (Array.isArray(data.Ratings)) {
        data.Ratings.forEach(r => {
          switch (r.Source) {
            case 'Internet Movie Database':
              if (!imdbRating) imdbRating = r.Value.replace('/10', '');
              break;
            case 'Rotten Tomatoes':
              rottenTomatoes = r.Value;
              break;
            case 'Metacritic':
              if (!metacritic) metacritic = r.Value.replace('/100', '');
              break;
          }
        });
      }

      const result = {
        imdb:           imdbRating,
        rottenTomatoes: rottenTomatoes,
        metacritic:     metacritic,
        raw: {
          title:    data.Title,
          year:     data.Year,
          rated:    data.Rated,
          runtime:  data.Runtime,
          awards:   data.Awards,
          boxOffice: data.BoxOffice,
          imdbVotes: data.imdbVotes,
        },
      };

      // Store in cache
      cache.set(imdbId, { data: result, ts: Date.now() });

      return result;

    } catch (err) {
      console.error('[OMDB] Fetch failed:', err);
      return { imdb: null, rottenTomatoes: null, metacritic: null, raw: null };
    }
  }

  /**
   * Clear the in-memory ratings cache.
   */
  function clearCache() {
    cache.clear();
  }

  /* ── Export ────────────────────────────────────────────────────────── */
  window.OMDB = Object.freeze({
    getRatings,
    clearCache,
  });
})();
