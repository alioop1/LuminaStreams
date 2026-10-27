/**
 * LuminaStreams — Internationalisation (i18n)
 * Complete Hebrew / English translation dictionary.
 * 100+ keys covering the entire application UI.
 */
(() => {
  'use strict';

  /* ── Translation Dictionary ───────────────────────────────────────── */
  const translations = {

    /* ── Navigation & Sidebar ─────────────────────────────────────── */
    'nav.home':            { en: 'Home',              he: 'דף הבית' },
    'nav.movies':          { en: 'Movies',            he: 'סרטים' },
    'nav.series':          { en: 'TV Series',         he: 'סדרות' },
    'nav.search':          { en: 'Search',            he: 'חיפוש' },
    'nav.watchlist':       { en: 'Watchlist',         he: 'רשימת צפייה' },
    'nav.iptv':            { en: 'Live TV',           he: 'שידור חי' },
    'nav.fuzer':           { en: 'Fuzer',             he: 'פוזר' },
    'nav.settings':        { en: 'Settings',          he: 'הגדרות' },
    'nav.player':          { en: 'Player',            he: 'נגן' },
    'nav.details':         { en: 'Details',           he: 'פרטים' },
    'nav.auth':            { en: 'Authentication',    he: 'אימות' },
    'nav.discover':        { en: 'Discover',          he: 'גלה' },

    /* ── Home Screen ──────────────────────────────────────────────── */
    'home.trending':       { en: 'Trending Now',      he: 'פופולרי עכשיו' },
    'home.continueWatch':  { en: 'Continue Watching', he: 'המשך צפייה' },
    'home.popular_movies': { en: 'Popular Movies',    he: 'סרטים פופולריים' },
    'home.popular_series': { en: 'Popular Series',    he: 'סדרות פופולריות' },
    'home.top_rated':      { en: 'Top Rated',         he: 'הכי מדורגים' },
    'home.new_releases':   { en: 'New Releases',      he: 'יצאו לאחרונה' },
    'home.recommended':    { en: 'Recommended',       he: 'מומלצים' },
    'home.watchlist':      { en: 'My Watchlist',      he: 'הרשימה שלי' },
    'home.hero_play':      { en: 'Play',              he: 'הפעל' },
    'home.hero_info':      { en: 'More Info',         he: 'מידע נוסף' },

    /* ── Buttons ──────────────────────────────────────────────────── */
    'btn.play':            { en: 'Play',              he: 'הפעל' },
    'btn.trailer':         { en: 'Trailer',           he: 'טריילר' },
    'btn.add_watchlist':   { en: 'Add to Watchlist',  he: 'הוסף לרשימה' },
    'btn.remove_watchlist':{ en: 'Remove',            he: 'הסר מהרשימה' },
    'btn.watch_now':       { en: 'Watch Now',         he: 'צפה עכשיו' },
    'btn.load_more':       { en: 'Load More',         he: 'טען עוד' },
    'btn.retry':           { en: 'Retry',             he: 'נסה שוב' },
    'btn.cancel':          { en: 'Cancel',            he: 'ביטול' },
    'btn.save':            { en: 'Save',              he: 'שמור' },
    'btn.close':           { en: 'Close',             he: 'סגור' },
    'btn.back':            { en: 'Back',              he: 'חזור' },
    'btn.apply':           { en: 'Apply',             he: 'החל' },
    'btn.reset':           { en: 'Reset',             he: 'אפס' },
    'btn.clear':           { en: 'Clear',             he: 'נקה' },
    'btn.confirm':         { en: 'Confirm',           he: 'אשר' },
    'btn.select_source':   { en: 'Select Source',     he: 'בחר מקור' },
    'btn.copy':            { en: 'Copy',              he: 'העתק' },
    'btn.share':           { en: 'Share',             he: 'שתף' },
    'btn.filter':          { en: 'Filter',            he: 'סנן' },
    'btn.sort':            { en: 'Sort',              he: 'מיין' },
    'btn.login':           { en: 'Login',             he: 'התחבר' },
    'btn.logout':          { en: 'Logout',            he: 'התנתק' },
    'btn.connect':         { en: 'Connect',           he: 'התחבר' },
    'btn.disconnect':      { en: 'Disconnect',        he: 'נתק' },

    /* ── Search ───────────────────────────────────────────────────── */
    'search.placeholder':  { en: 'Search movies, series, people…', he: 'חפש סרטים, סדרות, שחקנים…' },
    'search.no_results':   { en: 'No results found',  he: 'לא נמצאו תוצאות' },
    'search.results_for':  { en: 'Results for',       he: 'תוצאות עבור' },
    'search.recent':       { en: 'Recent Searches',   he: 'חיפושים אחרונים' },
    'search.clear_history':{ en: 'Clear History',     he: 'נקה היסטוריה' },
    'search.trending':     { en: 'Trending Searches', he: 'חיפושים פופולריים' },

    /* ── Details Screen ───────────────────────────────────────────── */
    'details.cast':        { en: 'Cast',              he: 'שחקנים' },
    'details.overview':    { en: 'Overview',          he: 'תקציר' },
    'details.seasons':     { en: 'Seasons',           he: 'עונות' },
    'details.episodes':    { en: 'Episodes',          he: 'פרקים' },
    'details.similar':     { en: 'Similar',           he: 'דומים' },
    'details.reviews':     { en: 'Reviews',           he: 'ביקורות' },
    'details.director':    { en: 'Director',          he: 'במאי' },
    'details.creators':    { en: 'Creators',          he: 'יוצרים' },
    'details.runtime':     { en: 'Runtime',           he: 'משך' },
    'details.release_date':{ en: 'Release Date',      he: 'תאריך יציאה' },
    'details.first_air':   { en: 'First Air Date',    he: 'תאריך שידור ראשון' },
    'details.status':      { en: 'Status',            he: 'סטטוס' },
    'details.budget':      { en: 'Budget',            he: 'תקציב' },
    'details.revenue':     { en: 'Revenue',           he: 'הכנסות' },
    'details.language':    { en: 'Language',           he: 'שפה' },
    'details.genres':      { en: 'Genres',            he: 'ז\'אנרים' },
    'details.rating':      { en: 'Rating',            he: 'דירוג' },
    'details.season':      { en: 'Season',            he: 'עונה' },
    'details.episode':     { en: 'Episode',           he: 'פרק' },
    'details.networks':    { en: 'Networks',          he: 'רשתות' },
    'details.production':  { en: 'Production',        he: 'הפקה' },
    'details.imdb':        { en: 'IMDb',              he: 'IMDb' },
    'details.rt':          { en: 'Rotten Tomatoes',   he: 'Rotten Tomatoes' },
    'details.metacritic':  { en: 'Metacritic',        he: 'Metacritic' },
    'details.no_overview': { en: 'No overview available.', he: 'אין תקציר זמין.' },

    /* ── Streams / Sources ────────────────────────────────────────── */
    'streams.title':       { en: 'Available Sources',  he: 'מקורות זמינים' },
    'streams.cached':      { en: 'Cached',             he: 'במטמון' },
    'streams.no_streams':  { en: 'No streams found',   he: 'לא נמצאו מקורות' },
    'streams.loading':     { en: 'Finding streams…',   he: 'מחפש מקורות…' },
    'streams.seeders':     { en: 'Seeders',            he: 'סידרים' },
    'streams.size':        { en: 'Size',               he: 'גודל' },
    'streams.quality':     { en: 'Quality',            he: 'איכות' },
    'streams.codec':       { en: 'Codec',              he: 'קודק' },
    'streams.source':      { en: 'Source',             he: 'מקור' },
    'streams.resolving':   { en: 'Resolving link…',    he: 'מפענח קישור…' },

    /* ── Player ───────────────────────────────────────────────────── */
    'player.play':         { en: 'Play',               he: 'הפעל' },
    'player.pause':        { en: 'Pause',              he: 'השהה' },
    'player.mute':         { en: 'Mute',               he: 'השתק' },
    'player.unmute':       { en: 'Unmute',              he: 'בטל השתקה' },
    'player.fullscreen':   { en: 'Fullscreen',          he: 'מסך מלא' },
    'player.exit_fs':      { en: 'Exit Fullscreen',     he: 'צא ממסך מלא' },
    'player.subtitles':    { en: 'Subtitles',           he: 'כתוביות' },
    'player.no_subs':      { en: 'No Subtitles',        he: 'ללא כתוביות' },
    'player.audio':        { en: 'Audio Track',         he: 'רצועת שמע' },
    'player.speed':        { en: 'Speed',               he: 'מהירות' },
    'player.next_ep':      { en: 'Next Episode',        he: 'פרק הבא' },
    'player.prev_ep':      { en: 'Previous Episode',    he: 'פרק קודם' },
    'player.skip_intro':   { en: 'Skip Intro',          he: 'דלג על פתיח' },
    'player.loading':      { en: 'Loading…',            he: 'טוען…' },
    'player.buffering':    { en: 'Buffering…',          he: 'אוגר…' },
    'player.error':        { en: 'Playback Error',      he: 'שגיאת ניגון' },
    'player.volume':       { en: 'Volume',              he: 'עוצמה' },
    'player.pip':          { en: 'Picture in Picture',   he: 'תמונה בתמונה' },
    'player.cast':         { en: 'Cast to device',       he: 'שדר למכשיר' },

    /* ── Settings ─────────────────────────────────────────────────── */
    'settings.title':      { en: 'Settings',            he: 'הגדרות' },
    'settings.general':    { en: 'General',              he: 'כללי' },
    'settings.language':   { en: 'Language',             he: 'שפה' },
    'settings.lang_en':    { en: 'English',              he: 'אנגלית' },
    'settings.lang_he':    { en: 'Hebrew',               he: 'עברית' },
    'settings.accounts':   { en: 'Accounts',             he: 'חשבונות' },
    'settings.rd_status':  { en: 'AllDebrid Status',     he: 'סטטוס AllDebrid' },
    'settings.rd_connect': { en: 'Connect AllDebrid',    he: 'חבר AllDebrid' },
    'settings.rd_connected':{ en: 'Connected',           he: 'מחובר' },
    'settings.rd_disconnected':{ en: 'Not Connected',    he: 'לא מחובר' },
    'settings.appearance': { en: 'Appearance',           he: 'מראה' },
    'settings.player':     { en: 'Player',               he: 'נגן' },
    'settings.subtitles':  { en: 'Subtitles',            he: 'כתוביות' },
    'settings.about':      { en: 'About',                he: 'אודות' },
    'settings.version':    { en: 'Version',              he: 'גרסה' },
    'settings.clear_data': { en: 'Clear All Data',       he: 'מחק את כל הנתונים' },
    'settings.clear_cache':{ en: 'Clear Cache',          he: 'נקה מטמון' },
    'settings.iptv_url':   { en: 'IPTV Playlist URL',    he: 'כתובת פלייליסט IPTV' },
    'settings.fuzer_auth': { en: 'Fuzer Authentication',  he: 'אימות פוזר' },
    'settings.autoplay':   { en: 'Autoplay next episode', he: 'הפעל אוטומטית פרק הבא' },
    'settings.quality':    { en: 'Preferred Quality',     he: 'איכות מועדפת' },

    /* ── Auth / AllDebrid ─────────────────────────────────────────── */
    'auth.title':          { en: 'Connect AllDebrid',    he: 'חבר AllDebrid' },
    'auth.instruction':    { en: 'Visit the URL below and enter the code:', he: 'גש לכתובת הבאה והזן את הקוד:' },
    'auth.waiting':        { en: 'Waiting for authorization…', he: 'ממתין לאישור…' },
    'auth.success':        { en: 'Successfully connected!',    he: 'החיבור הצליח!' },
    'auth.failed':         { en: 'Authorization failed',       he: 'האימות נכשל' },
    'auth.expired':        { en: 'Code expired, please retry', he: 'הקוד פג תוקף, נסה שוב' },

    /* ── Filters / Discover ───────────────────────────────────────── */
    'filter.genre':        { en: 'Genre',               he: 'ז\'אנר' },
    'filter.year':         { en: 'Year',                he: 'שנה' },
    'filter.rating':       { en: 'Rating',              he: 'דירוג' },
    'filter.sort_by':      { en: 'Sort By',             he: 'מיין לפי' },
    'filter.popularity':   { en: 'Popularity',          he: 'פופולריות' },
    'filter.release_date': { en: 'Release Date',        he: 'תאריך יציאה' },
    'filter.vote_avg':     { en: 'Vote Average',        he: 'ממוצע הצבעות' },
    'filter.network':      { en: 'Network',             he: 'רשת' },
    'filter.all':          { en: 'All',                 he: 'הכל' },

    /* ── Status & Error Messages ──────────────────────────────────── */
    'status.loading':      { en: 'Loading…',            he: 'טוען…' },
    'status.no_data':      { en: 'No data available',   he: 'אין מידע זמין' },
    'status.offline':      { en: 'You are offline',     he: 'אין חיבור לאינטרנט' },
    'status.error':        { en: 'Something went wrong', he: 'משהו השתבש' },
    'status.timeout':      { en: 'Request timed out',    he: 'הבקשה נכשלה (זמן קצוב)' },
    'status.rate_limit':   { en: 'Too many requests',    he: 'יותר מדי בקשות' },
    'status.not_found':    { en: 'Not Found',            he: 'לא נמצא' },
    'status.empty_watchlist': { en: 'Your watchlist is empty', he: 'רשימת הצפייה ריקה' },
    'status.empty_history':   { en: 'No search history', he: 'אין היסטוריית חיפוש' },
    'status.connecting':   { en: 'Connecting…',          he: 'מתחבר…' },
    'status.buffering':    { en: 'Buffering…',           he: 'אוגר…' },

    /* ── IPTV ─────────────────────────────────────────────────────── */
    'iptv.title':          { en: 'Live TV',              he: 'שידור חי' },
    'iptv.no_channels':    { en: 'No channels found',    he: 'לא נמצאו ערוצים' },
    'iptv.add_playlist':   { en: 'Add Playlist',         he: 'הוסף פלייליסט' },
    'iptv.favorites':      { en: 'Favorite Channels',    he: 'ערוצים מועדפים' },
    'iptv.all_channels':   { en: 'All Channels',         he: 'כל הערוצים' },
    'iptv.epg':            { en: 'TV Guide',             he: 'לוח שידורים' },
    'iptv.categories':     { en: 'Categories',           he: 'קטגוריות' },

    /* ── Fuzer ────────────────────────────────────────────────────── */
    'fuzer.title':         { en: 'Fuzer',                he: 'פוזר' },
    'fuzer.search':        { en: 'Search Fuzer…',        he: 'חפש בפוזר…' },
    'fuzer.no_results':    { en: 'No torrents found',    he: 'לא נמצאו טורנטים' },

    /* ── Misc ─────────────────────────────────────────────────────── */
    'misc.movie':          { en: 'Movie',                he: 'סרט' },
    'misc.series':         { en: 'Series',               he: 'סדרה' },
    'misc.person':         { en: 'Person',               he: 'אדם' },
    'misc.or':             { en: 'or',                   he: 'או' },
    'misc.and':            { en: 'and',                  he: 'ו' },
    'misc.yes':            { en: 'Yes',                  he: 'כן' },
    'misc.no':             { en: 'No',                   he: 'לא' },
    'misc.unknown':        { en: 'Unknown',              he: 'לא ידוע' },
    'misc.app_name':       { en: 'LuminaStreams',        he: 'LuminaStreams' },
    'misc.copyright':      { en: '© 2026 LuminaStreams', he: '© 2026 LuminaStreams' },
  };

  /* ── Language State ───────────────────────────────────────────────── */

  function getLanguage() {
    return (window.Storage && window.Storage.getLanguage)
      ? window.Storage.getLanguage()
      : 'he';
  }

  function setLanguage(lang) {
    if (window.Storage && window.Storage.setLanguage) {
      window.Storage.setLanguage(lang);
    }
    document.documentElement.setAttribute('lang', lang);
    document.documentElement.setAttribute('dir', lang === 'he' ? 'rtl' : 'ltr');
  }

  /* ── Translation Functions ────────────────────────────────────────── */

  /**
   * Look up a translation key and return the string for the current language.
   * Falls back to English, then to the raw key.
   * @param {string} key  Dot-notation key, e.g. 'nav.home'
   * @param {Object} [vars]  Optional interpolation map { name: 'value' }
   * @returns {string}
   */
  function t(key, vars) {
    const lang = getLanguage();
    const entry = translations[key];
    if (!entry) {
      console.warn(`[I18n] Missing key: "${key}"`);
      return key;
    }
    let str = entry[lang] || entry.en || key;
    // Simple interpolation: {{variable}}
    if (vars) {
      Object.keys(vars).forEach(k => {
        str = str.replace(new RegExp(`{{${k}}}`, 'g'), vars[k]);
      });
    }
    return str;
  }

  /**
   * Inline translation shorthand — returns he or en based on current language.
   * @param {string} en
   * @param {string} he
   * @returns {string}
   */
  function tr(en, he) {
    return getLanguage() === 'he' ? he : en;
  }

  /**
   * Get all keys that start with a given prefix (useful for building sections).
   * @param {string} prefix  e.g. 'nav'
   * @returns {Object} { key: translatedString, … }
   */
  function getSection(prefix) {
    const lang = getLanguage();
    const result = {};
    Object.keys(translations).forEach(key => {
      if (key.startsWith(prefix + '.')) {
        result[key] = translations[key][lang] || translations[key].en || key;
      }
    });
    return result;
  }

  /**
   * Check if a translation key exists.
   * @param {string} key
   * @returns {boolean}
   */
  function has(key) {
    return key in translations;
  }

  /* ── Export ────────────────────────────────────────────────────────── */
  window.I18n = Object.freeze({
    t,
    tr,
    getLanguage,
    setLanguage,
    getSection,
    has,
    translations,
  });
})();
