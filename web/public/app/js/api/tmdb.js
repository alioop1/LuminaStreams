/**
 * LuminaStreams — TMDB API Client
 * Full wrapper around The Movie Database API v3.
 */
(() => {
  'use strict';

  /** TMDB content language follows the app language (Hebrew UI → he-IL, English → en-US). */
  function contentLanguage() {
    const lang = window.I18n && window.I18n.getLanguage ? window.I18n.getLanguage() : 'he';
    return lang === 'en' ? 'en-US' : 'he-IL';
  }

  const API_KEY = window.CONST.TMDB_API_KEY;
  const BASE    = window.CONST.TMDB_BASE;

  /* ── Internal Fetch with Retry ────────────────────────────────────── */

  /**
   * Fetch JSON from TMDB with one automatic retry on failure.
   * @param {string} endpoint  Path after base, e.g. '/trending/all/week'
   * @param {Object} [params]  Query parameters (api_key is added automatically)
   * @param {number} [retries] Remaining retries
   * @returns {Promise<Object>}
   */
  async function tmdbFetch(endpoint, params = {}, retries = 1) {
    const url = new URL(`${BASE}${endpoint}`);
    url.searchParams.set('api_key', API_KEY);

    Object.entries(params).forEach(([key, value]) => {
      if (value != null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    });

    try {
      const res = await fetch(url.toString());

      if (!res.ok) {
        // Rate-limited — wait and retry
        if (res.status === 429 && retries > 0) {
          const retryAfter = parseInt(res.headers.get('Retry-After') || '2', 10);
          await sleep(retryAfter * 1000);
          return tmdbFetch(endpoint, params, retries - 1);
        }
        throw new Error(`TMDB ${res.status}: ${res.statusText} — ${endpoint}`);
      }

      return await res.json();
    } catch (err) {
      if (retries > 0 && !err.message.includes('TMDB')) {
        await sleep(1000);
        return tmdbFetch(endpoint, params, retries - 1);
      }
      console.error('[TMDB] Fetch failed:', err);
      throw err;
    }
  }

  function sleep(ms) {
    return new Promise(r => setTimeout(r, ms));
  }

  /* ── Trending ─────────────────────────────────────────────────────── */

  /**
   * Get trending movies / TV / all for this week.
   * @param {'all'|'movie'|'tv'} type
   * @param {number} page
   * @returns {Promise<Object>}
   */
  function getTrending(type = 'all', page = 1) {
    return tmdbFetch(`/trending/${type}/week`, {
      page,
      language: contentLanguage(),
    });
  }

  /* ── Discover ─────────────────────────────────────────────────────── */

  /**
   * Discover movies with optional genre filter.
   * @param {number|null} genreId
   * @param {number} page
   * @returns {Promise<Object>}
   */
  function discoverMovies(genreId = null, page = 1) {
    const params = {
      page,
      language:           contentLanguage(),
      sort_by:            'popularity.desc',
      include_adult:      false,
      'vote_count.gte':   50,
    };
    if (genreId) params.with_genres = genreId;
    return tmdbFetch('/discover/movie', params);
  }

  /**
   * Discover TV shows with optional genre filter.
   * @param {number|null} genreId
   * @param {number} page
   * @returns {Promise<Object>}
   */
  function discoverTv(genreId = null, page = 1) {
    const params = {
      page,
      language:           contentLanguage(),
      sort_by:            'popularity.desc',
      include_adult:      false,
      'vote_count.gte':   50,
    };
    if (genreId) params.with_genres = genreId;
    return tmdbFetch('/discover/tv', params);
  }

  /**
   * Discover content by streaming studio / provider.
   * @param {string} studioKey  Key from CONST.STUDIOS (e.g. 'netflix')
   * @param {'movie'|'tv'} type
   * @param {number} page
   * @returns {Promise<Object>}
   */
  function discoverByStudio(studioKey, type = 'movie', page = 1) {
    const studio = window.CONST.STUDIOS[studioKey];
    if (!studio) throw new Error(`Unknown studio: ${studioKey}`);

    const params = {
      page,
      language:      contentLanguage(),
      sort_by:       'popularity.desc',
      include_adult: false,
      watch_region:  'IL',
    };

    if (type === 'movie') {
      params.with_watch_providers = studio.providerId;
      return tmdbFetch('/discover/movie', params);
    } else {
      // For TV: use with_networks for more accurate results
      params.with_networks = studio.networkId;
      return tmdbFetch('/discover/tv', params);
    }
  }

  /**
   * Discover with advanced filters.
   * @param {Object} filters
   * @returns {Promise<Object>}
   */
  function discoverWithFilters({
    type = 'movie',
    genre = null,
    year = null,
    rating = null,
    language = null,
    network = null,
    runtime = null,
    sortBy = 'popularity.desc',
    page = 1,
  } = {}) {
    const endpoint = type === 'tv' ? '/discover/tv' : '/discover/movie';
    const params = {
      page,
      language:      contentLanguage(),
      sort_by:       sortBy,
      include_adult: false,
    };

    if (genre)   params.with_genres = genre;
    if (rating)  params['vote_average.gte'] = rating;
    if (network) params.with_networks = network;

    if (year) {
      if (type === 'movie') {
        params.primary_release_year = year;
      } else {
        params.first_air_date_year = year;
      }
    }

    if (language) {
      params.with_original_language = language;
    }

    if (runtime) {
      // runtime can be { min, max }
      if (runtime.min) params['with_runtime.gte'] = runtime.min;
      if (runtime.max) params['with_runtime.lte'] = runtime.max;
    }

    return tmdbFetch(endpoint, params);
  }

  /* ── Details ──────────────────────────────────────────────────────── */

  /**
   * Get full movie details with credits, videos, external IDs, images, reviews.
   * @param {number} id
   * @returns {Promise<Object>}
   */
  function getMovieDetails(id) {
    return tmdbFetch(`/movie/${id}`, {
      language:          contentLanguage(),
      append_to_response: 'credits,videos,external_ids,images,reviews',
      include_image_language: 'he,en,null',
      // Trailers: Hebrew ones rarely exist, so also accept English/untagged videos.
      include_video_language: 'he,en,null',
    });
  }

  /**
   * Get full TV show details with credits, videos, external IDs, images, reviews.
   * @param {number} id
   * @returns {Promise<Object>}
   */
  function getTvDetails(id) {
    return tmdbFetch(`/tv/${id}`, {
      language:          contentLanguage(),
      append_to_response: 'credits,videos,external_ids,images,reviews',
      include_image_language: 'he,en,null',
      // Trailers: Hebrew ones rarely exist, so also accept English/untagged videos.
      include_video_language: 'he,en,null',
    });
  }

  /**
   * Get a specific TV season's episodes.
   * @param {number} tvId
   * @param {number} seasonNum
   * @returns {Promise<Object>}
   */
  function getTvSeason(tvId, seasonNum) {
    return tmdbFetch(`/tv/${tvId}/season/${seasonNum}`, {
      language: contentLanguage(),
    });
  }

  /**
   * Get a specific episode's details.
   * @param {number} tvId
   * @param {number} seasonNum
   * @param {number} episodeNum
   * @returns {Promise<Object>}
   */
  function getTvEpisode(tvId, seasonNum, episodeNum) {
    return tmdbFetch(`/tv/${tvId}/season/${seasonNum}/episode/${episodeNum}`, {
      language: contentLanguage(),
    });
  }

  /* ── Search ───────────────────────────────────────────────────────── */

  /**
   * Multi-search across movies, TV, and people.
   * @param {string} query
   * @param {number} page
   * @returns {Promise<Object>}
   */
  function searchMulti(query, page = 1) {
    return tmdbFetch('/search/multi', {
      query,
      page,
      language:      contentLanguage(),
      include_adult: false,
    });
  }

  /**
   * Search only movies.
   * @param {string} query
   * @param {number} page
   * @returns {Promise<Object>}
   */
  function searchMovies(query, page = 1) {
    return tmdbFetch('/search/movie', {
      query,
      page,
      language:      contentLanguage(),
      include_adult: false,
    });
  }

  /**
   * Search only TV shows.
   * @param {string} query
   * @param {number} page
   * @returns {Promise<Object>}
   */
  function searchTv(query, page = 1) {
    return tmdbFetch('/search/tv', {
      query,
      page,
      language:      contentLanguage(),
      include_adult: false,
    });
  }

  /* ── Similar / Recommendations ────────────────────────────────────── */

  /**
   * Get similar movies or TV shows.
   * @param {'movie'|'tv'} type
   * @param {number} id
   * @param {number} page
   * @returns {Promise<Object>}
   */
  function getSimilar(type, id, page = 1) {
    return tmdbFetch(`/${type}/${id}/similar`, {
      page,
      language: contentLanguage(),
    });
  }

  /**
   * Get recommendations for a movie or TV show.
   * @param {'movie'|'tv'} type
   * @param {number} id
   * @param {number} page
   * @returns {Promise<Object>}
   */
  function getRecommendations(type, id, page = 1) {
    return tmdbFetch(`/${type}/${id}/recommendations`, {
      page,
      language: contentLanguage(),
    });
  }

  /* ── People ───────────────────────────────────────────────────────── */

  /**
   * Get a person's details and combined credits.
   * @param {number} personId
   * @returns {Promise<Object>}
   */
  function getPersonDetails(personId) {
    return tmdbFetch(`/person/${personId}`, {
      language:          contentLanguage(),
      append_to_response: 'combined_credits,images',
    });
  }

  /* ── Lists / Charts ───────────────────────────────────────────────── */

  function getPopularMovies(page = 1) {
    return tmdbFetch('/movie/popular', { page, language: contentLanguage() });
  }

  function getTopRatedMovies(page = 1) {
    return tmdbFetch('/movie/top_rated', { page, language: contentLanguage() });
  }

  function getUpcomingMovies(page = 1) {
    return tmdbFetch('/movie/upcoming', { page, language: contentLanguage(), region: 'IL' });
  }

  function getNowPlayingMovies(page = 1) {
    return tmdbFetch('/movie/now_playing', { page, language: contentLanguage(), region: 'IL' });
  }

  function getPopularTv(page = 1) {
    return tmdbFetch('/tv/popular', { page, language: contentLanguage() });
  }

  function getTopRatedTv(page = 1) {
    return tmdbFetch('/tv/top_rated', { page, language: contentLanguage() });
  }

  function getOnTheAirTv(page = 1) {
    return tmdbFetch('/tv/on_the_air', { page, language: contentLanguage() });
  }

  /* ── Watch Providers ──────────────────────────────────────────────── */

  /**
   * Get watch providers for a movie or TV show in Israel.
   * @param {'movie'|'tv'} type
   * @param {number} id
   * @returns {Promise<Object>}
   */
  async function getWatchProviders(type, id) {
    const data = await tmdbFetch(`/${type}/${id}/watch/providers`);
    return data.results?.IL || data.results?.US || null;
  }

  /* ── Export ────────────────────────────────────────────────────────── */
  window.TMDB = Object.freeze({
    // Trending
    getTrending,

    // Discover
    discoverMovies,
    discoverTv,
    discoverByStudio,
    discoverWithFilters,

    // Details
    getMovieDetails,
    getTvDetails,
    getTvSeason,
    getTvEpisode,

    // Search
    searchMulti,
    searchMovies,
    searchTv,

    // Similar
    getSimilar,
    getRecommendations,

    // People
    getPersonDetails,

    // Charts
    getPopularMovies,
    getTopRatedMovies,
    getUpcomingMovies,
    getNowPlayingMovies,
    getPopularTv,
    getTopRatedTv,
    getOnTheAirTv,

    // Providers
    getWatchProviders,
  });
})();
