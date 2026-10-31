/**
 * LuminaStreams TV — Search, 1:1 with the Android TV app
 * (presentation/search: SearchScreen.kt, SearchResultsGrid.kt, SearchCards.kt,
 * SearchFilterSidebar.kt, SearchViewModel.kt; TMDB calls from MediaRepositoryImpl.kt).
 *
 * Layout in dp (1 CSS px = 1 dp, screen 1706.67 × 960). Focus follows the Compose
 * rules of the Android screen (explicit focusProperties first, geometry otherwise),
 * lists scroll by transform with the TV pivot (Compose on Android TV brings the
 * focused item to 30% of the list), long lists are windowed.
 */
(() => {
  'use strict';
  const { tr, esc, el } = TV;
  const W = TV.W;
  const H = TV.H;
  const PAD = 48;

  /* ── SearchConstants.kt ─────────────────────────────────────────────── */
  const RED = '#E50914';
  const GOLD = '#FFCC00';

  /* ── Material icons that core.js doesn't have (24 dp viewBox) ───────── */
  const PATHS = {
    tune: 'M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z',
    history: 'M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z',
    searchOff: 'M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3 6.08 3 3.28 5.64 3.03 9h2.02C5.3 6.75 7.18 5 9.5 5 11.99 5 14 7.01 14 9.5S11.99 14 9.5 14c-.17 0-.33-.03-.5-.05v2.02c.17.02.33.03.5.03 1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zM6.47 10.82L4 13.29l-2.47-2.47-.71.71L3.29 14 .82 16.47l.71.71L4 14.71l2.47 2.47.71-.71L4.71 14l2.47-2.47z',
    cloudOff: 'M19.35 10.04C18.67 6.59 15.64 4 12 4c-1.48 0-2.85.43-4.01 1.17l1.46 1.46C10.21 6.23 11.08 6 12 6c3.04 0 5.5 2.46 5.5 5.5v.5H19c1.66 0 3 1.34 3 3 0 1.13-.64 2.11-1.56 2.62l1.45 1.45C23.16 18.16 24 16.68 24 15c0-2.64-2.05-4.78-4.65-4.96zM3 5.27l2.75 2.74C2.56 8.15 0 10.77 0 14c0 3.31 2.69 6 6 6h11.73l2 2L21 20.73 4.27 4 3 5.27zM7.73 10l8 8H6c-2.21 0-4-1.79-4-4s1.79-4 4-4h1.73z',
    delete: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
    filterList: 'M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z',
    list: 'M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z',
    videoLibrary: 'M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-8 12.5v-9l6 4.5-6 4.5z',
    sort: 'M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z',
    dateRange: 'M9 11H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2zm2-7h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V9h14v11z',
    accessTime: 'M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z',
    hd: 'M19 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-8 12H9.5v-2h-2v2H6V9h1.5v2.5h2V9H11v6zm2-6h4c.55 0 1 .45 1 1v4c0 .55-.45 1-1 1h-4V9zm1.5 4.5h2v-3h-2v3z',
    face: 'M9 11.75c-.69 0-1.25.56-1.25 1.25s.56 1.25 1.25 1.25 1.25-.56 1.25-1.25-.56-1.25-1.25-1.25zm6 0c-.69 0-1.25.56-1.25 1.25s.56 1.25 1.25 1.25 1.25-.56 1.25-1.25-.56-1.25-1.25-1.25zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8 0-.29.02-.58.05-.86 2.36-1.05 4.23-2.98 5.21-5.37C11.07 8.33 14.05 10 17.42 10c.78 0 1.53-.09 2.25-.26.21.71.33 1.47.33 2.26 0 4.41-3.59 8-8 8z',
    language: 'M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zm6.93 6h-2.95c-.32-1.25-.78-2.45-1.38-3.56 1.84.63 3.37 1.91 4.33 3.56zM12 4.04c.83 1.2 1.48 2.53 1.91 3.96h-3.82c.43-1.43 1.08-2.76 1.91-3.96zM4.26 14C4.1 13.36 4 12.69 4 12s.1-1.36.26-2h3.38c-.08.66-.14 1.32-.14 2 0 .68.06 1.34.14 2H4.26zm.82 2h2.95c.32 1.25.78 2.45 1.38 3.56-1.84-.63-3.37-1.9-4.33-3.56zm2.95-8H5.08c.96-1.66 2.49-2.93 4.33-3.56C8.81 5.55 8.35 6.75 8.03 8zM12 19.96c-.83-1.2-1.48-2.53-1.91-3.96h3.82c-.43 1.43-1.08 2.76-1.91 3.96zM14.34 14H9.66c-.09-.66-.16-1.32-.16-2 0-.68.07-1.35.16-2h4.68c.09.65.16 1.32.16 2 0 .68-.07 1.34-.16 2zm.25 5.56c.6-1.11 1.06-2.31 1.38-3.56h2.95c-.96 1.65-2.49 2.93-4.33 3.56zM16.36 14c.08-.66.14-1.32.14-2 0-.68-.06-1.34-.14-2h3.38c.16.64.26 1.31.26 2s-.1 1.36-.26 2h-3.38z',
    whatshot: 'M13.5.67s.74 2.65.74 4.8c0 2.06-1.35 3.73-3.41 3.73-2.07 0-3.63-1.67-3.63-3.73l.03-.36C5.21 7.51 4 10.62 4 14c0 4.42 3.58 8 8 8s8-3.58 8-8C20 8.61 17.41 3.8 13.5.67zM11.71 19c-1.78 0-3.22-1.4-3.22-3.14 0-1.62 1.05-2.76 2.81-3.12 1.77-.36 3.6-1.21 4.62-2.58.39 1.29.59 2.65.59 4.04 0 2.65-2.15 4.8-4.8 4.8z',
    flightTakeoff: 'M2.5 19h19v2h-19zm19.57-9.36c-.21-.8-1.04-1.28-1.84-1.06L14.92 10l-6.9-6.43-1.93.51 4.14 7.17-4.97 1.33-1.97-1.54-1.45.39 1.82 3.16.77 1.33 1.6-.43 5.31-1.42 4.35-1.16L21 11.49c.81-.23 1.28-1.05 1.07-1.85z',
    tagFaces: 'M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm3.5-9c.83 0 1.5-.67 1.5-1.5S16.33 8 15.5 8 14 8.67 14 9.5s.67 1.5 1.5 1.5zm-7 0c.83 0 1.5-.67 1.5-1.5S9.33 8 8.5 8 7 8.67 7 9.5 7.67 11 8.5 11zm3.5 6.5c2.33 0 4.31-1.46 5.11-3.5H6.89c.8 2.04 2.78 3.5 5.11 3.5z',
    theaters: 'M18 3v2h-2V3H8v2H6V3H4v18h2v-2h2v2h8v-2h2v2h2V3h-2zM8 17H6v-2h2v2zm0-4H6v-2h2v2zm0-4H6V7h2v2zm10 8h-2v-2h2v2zm0-4h-2v-2h2v2zm0-4h-2V7h2v2z',
    visibilityOff: 'M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z',
    stars: 'M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zm4.24 16L12 15.45 7.77 18l1.12-4.81-3.73-3.23 4.92-.42L12 5l1.92 4.53 4.92.42-3.73 3.23L16.23 18z',
    warning: 'M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z',
    gavel: 'M1 21h12v2H1zM5.245 8.07l2.83-2.827 14.14 14.142-2.828 2.828zM12.317 1l5.657 5.656-2.83 2.83-5.654-5.66zM3.825 9.485l5.657 5.657-2.828 2.828-5.657-5.657z',
    brush: 'M7 14c-1.66 0-3 1.34-3 3 0 1.31-1.16 2-2 2 .92 1.22 2.49 2 4 2 2.21 0 4-1.79 4-4 0-1.66-1.34-3-3-3zm13.71-9.37l-1.34-1.34c-.39-.39-1.02-.39-1.41 0L9 12.25 11.75 15l8.96-8.96c.39-.39.39-1.02 0-1.41z',
    videocam: 'M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z',
    helpOutline: 'M11 18h2v-2h-2v2zm1-16C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm0-14c-2.21 0-4 1.79-4 4h2c0-1.1.9-2 2-2s2 .9 2 2c0 2-3 1.75-3 5h2c0-2.25 3-2.5 3-5 0-2.21-1.79-4-4-4z',
    security: 'M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z',
    people: 'M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z',
    explore: 'M12 10.9c-.61 0-1.1.49-1.1 1.1s.49 1.1 1.1 1.1c.61 0 1.1-.49 1.1-1.1s-.49-1.1-1.1-1.1zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm2.19 12.19L6 18l3.81-8.19L18 6l-3.81 8.19z',
    accountBalance: 'M4 10v7h3v-7H4zm6 0v7h3v-7h-3zM2 22h19v-3H2v3zm14-12v7h3v-7h-3zm-4.5-9L2 6v2h19V6l-9.5-5z',
    musicNote: 'M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z',
    landscape: 'M14 6l-3.75 5 2.85 3.8-1.6 1.2C9.81 13.75 7 10 7 10l-6 8h22L14 6z',
    trendingUp: 'M16 6l2.29 2.29-4.88 4.88-4-4L2 16.59 3.41 18l6-6 4 4 6.3-6.29L22 12V6z',
    newReleases: 'M23 12l-2.44-2.78.34-3.68-3.61-.82-1.89-3.18L12 3 8.6 1.54 6.71 4.72l-3.61.81.34 3.68L1 12l2.44 2.78-.34 3.69 3.61.82 1.89 3.18L12 21l3.4 1.46 1.89-3.18 3.61-.82-.34-3.68L23 12zm-10 5h-2v-2h2v2zm0-4h-2V7h2v6z',
    sortByAlpha: 'M14.94 4.66h-4.72l2.36-2.36zm-4.69 14.71h4.66l-2.33 2.33zM6.1 6.27L1.6 17.73h1.84l.92-2.45h5.11l.92 2.45h1.84L7.74 6.27H6.1zm-1.13 7.37l1.94-5.18 1.94 5.18H4.97zm10.76 2.5h6.12v1.59h-8.53v-1.29l5.92-8.56h-5.88v-1.6h8.3v1.26l-5.93 8.6z',
    emojiEvents: 'M19 5h-2V3H7v2H5c-1.1 0-2 .9-2 2v1c0 2.55 1.92 4.63 4.39 4.94.63 1.5 1.98 2.63 3.61 2.96V19H7v2h10v-2h-4v-3.1c1.63-.33 2.98-1.46 3.61-2.96C19.08 12.63 21 10.55 21 8V7c0-1.1-.9-2-2-2zM5 8V7h2v3.82C5.84 10.4 5 9.3 5 8zm14 0c0 1.3-.84 2.4-2 2.82V7h2v1z',
    thumbUp: 'M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z',
    thumbsUpDown: 'M12 6c0-.55-.45-1-1-1H5.82l.66-3.18.02-.23c0-.31-.13-.59-.33-.8L5.38 0 .44 4.94C.17 5.21 0 5.59 0 6v6.5c0 .83.67 1.5 1.5 1.5h6.75c.62 0 1.15-.38 1.38-.91l2.26-5.29c.07-.17.11-.36.11-.55V6zm10.5 4h-6.75c-.62 0-1.15.38-1.38.91l-2.26 5.29c-.07.17-.11.36-.11.55V18c0 .55.45 1 1 1h5.18l-.66 3.18-.02.24c0 .31.13.59.33.8l.79.78 4.94-4.94c.27-.27.44-.65.44-1.06v-6.5c0-.83-.67-1.5-1.5-1.5z',
    eventNote: 'M17 10H7v2h10v-2zm2-7h-1V1h-2v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11zm-5-5H7v2h7v-2z',
    schedule: 'M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z',
    timer: 'M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42C16.07 4.74 14.12 4 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z',
    hourglassEmpty: 'M6 2v6h.01L6 8.01 10 12l-4 4 .01.01H6V22h12v-5.99h-.01L18 16l-4-4 4-3.99-.01-.01H18V2H6zm10 14.5V20H8v-3.5l4-4 4 4zm-4-5l-4-4V4h8v3.5l-4 4z',
    hourglassFull: 'M6 2v6h.01L6 8.01 10 12l-4 4 .01.01H6V22h12v-5.99h-.01L18 16l-4-4 4-3.99-.01-.01H18V2H6z',
    desktopWindows: 'M21 2H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h7v2H8v2h8v-2h-2v-2h7c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H3V4h18v12z',
    shoppingCart: 'M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zM1 2v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49c.08-.14.12-.31.12-.48 0-.55-.45-1-1-1H5.21l-.94-2H1zm16 16c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2z',
    wbSunny: 'M6.76 4.84l-1.8-1.79-1.41 1.41 1.79 1.79 1.42-1.41zM4 10.5H1v2h3v-2zm9-9.95h-2V3.5h2V.55zm7.45 3.91l-1.41-1.41-1.79 1.79 1.41 1.41 1.79-1.79zm-3.21 13.7l1.79 1.8 1.41-1.41-1.8-1.79-1.4 1.4zM20 10.5v2h3v-2h-3zm-8-5c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm-1 16.95h2V19.5h-2v2.95zm-7.45-3.91l1.41 1.41 1.79-1.8-1.41-1.41-1.79 1.8z',
    brightness2: 'M10 2c-1.82 0-3.53.5-5 1.35C7.99 5.08 10 8.3 10 12s-2.01 6.92-5 8.65C6.47 21.5 8.18 22 10 22c5.52 0 10-4.48 10-10S15.52 2 10 2z',
    lens: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z',
    public: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z',
    recordVoiceOver: 'M9 13c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4zm7.76-9.64l-1.68 1.69c.84 1.18.84 2.71 0 3.89l1.68 1.69c2.02-2.02 2.02-5.07 0-7.27zM20.07 2l-1.63 1.63c2.77 3.02 2.77 7.56 0 10.74L20.07 16c3.9-3.89 3.91-9.95 0-14z',
  };
  const ic = (name, size, cls = '') => (PATHS[name]
    ? `<svg class="ic ${cls}" width="${size}" height="${size}" viewBox="0 0 24 24" aria-hidden="true"><path d="${PATHS[name]}"/></svg>`
    : TV.icon(name, size, cls));

  /* ── SearchScreen.kt: CATEGORIES ────────────────────────────────────── */
  const CATEGORIES = [
    ['Top Box Office', 'הנצפים ביותר', '_popular_'], ['Action', 'אקשן', 'Action'], ['Sci-Fi', 'מדע בדיוני', 'Sci-Fi'],
    ['Comedy', 'קומדיה', 'Comedy'], ['Drama', 'דרמה', 'Drama'], ['Horror', 'אימה', 'Horror'], ['Fantasy', 'פנטזיה', 'Fantasy'],
    ['Thriller', 'מותחן', 'Thriller'], ['Crime', 'פשע', 'Crime'], ['Romance', 'רומנטיקה', 'Romance'], ['Animation', 'אנימציה', 'Animation'],
    ['War', 'מלחמה', 'War'], ['Documentary', 'דוקומנטרי', 'Documentary'], ['Family', 'משפחה', 'Family'], ['Mystery', 'מסתורין', 'Mystery'],
  ].map(([en, he, genre]) => ({ en, he, genre }));

  /* ── SearchResultsGrid.kt: YEAR_BUCKETS (Android shows these labels in every language) ── */
  const YEAR_BUCKETS = [
    { label: 'הטובים ביותר בכל הזמנים', emoji: '⭐', from: 1900, to: 2030, special: true },
    { label: '2027 – 2030', emoji: '🔮', from: 2027, to: 2030 },
    { label: '2020 – 2026', emoji: '🔥', from: 2020, to: 2026 },
    { label: '2010 – 2019', emoji: '🎬', from: 2010, to: 2019 },
    { label: '2001 – 2009', emoji: '📀', from: 2001, to: 2009 },
    { label: '1991 – 2000', emoji: '📼', from: 1991, to: 2000 },
    { label: '1900 – 1990', emoji: '🎞️', from: 1900, to: 1990 },
  ];

  /* ── MediaRepositoryImpl.tmdbGenreName + SearchViewModel.genreNameToTmdbId ── */
  const GENRE_NAME = {
    28: 'Action', 12: 'Adventure', 16: 'Animation', 35: 'Comedy', 80: 'Crime', 99: 'Documentary', 18: 'Drama', 10751: 'Family',
    14: 'Fantasy', 36: 'History', 27: 'Horror', 10402: 'Music', 9648: 'Mystery', 10749: 'Romance', 878: 'Sci-Fi', 10770: 'TV Movie',
    53: 'Thriller', 10752: 'War', 37: 'Western', 10759: 'Action & Adventure', 10762: 'Kids', 10763: 'News', 10764: 'Reality',
    10765: 'Sci-Fi & Fantasy', 10766: 'Soap', 10767: 'Talk', 10768: 'War & Politics',
  };
  const MOVIE_GENRE_ID = {
    action: 28, adventure: 12, animation: 16, anime: 16, comedy: 35, crime: 80, documentary: 99, drama: 18, family: 10751,
    fantasy: 14, history: 36, horror: 27, music: 10402, mystery: 9648, romance: 10749, 'sci-fi': 878, 'science fiction': 878,
    thriller: 53, war: 10752, western: 37,
  };
  const TV_GENRE_ID = {
    action: 10759, adventure: 10759, animation: 16, anime: 16, comedy: 35, crime: 80, documentary: 99, drama: 18, family: 10751,
    fantasy: 10765, horror: 9648, mystery: 9648, romance: 18, 'sci-fi': 10765, 'science fiction': 10765, thriller: 80, war: 10768, western: 37,
  };

  /* ── SearchViewModel.kt: SearchFilters ──────────────────────────────── */
  const DEFAULT_FILTERS = Object.freeze({
    typeFilter: 'ANY', genre: null, minYear: 1920, maxYear: 2026, minRating: 0, quality: 'ANY', dubbedOnly: false,
    sortBy: 'POPULARITY', runtime: 'ANY', ratingTier: 'ANY', language: 'ANY', network: 'ANY', mood: null,
  });
  const F = o => Object.assign({}, DEFAULT_FILTERS, o);
  const copy = (f, o) => Object.assign({}, f, o);
  const isActive = f => f.typeFilter !== 'ANY' || f.genre != null || f.minYear > 1920 || f.maxYear < 2026 || f.minRating > 0
    || f.quality !== 'ANY' || f.dubbedOnly || f.sortBy !== 'POPULARITY' || f.runtime !== 'ANY' || f.ratingTier !== 'ANY'
    || f.language !== 'ANY' || f.network !== 'ANY' || f.mood != null;

  /* ── SearchFilterSidebar.kt (its texts are English in every language, like Android) ── */
  const chip = (label, icon, sel, toggle) => ({ label, icon, sel, toggle });
  const enumChip = (label, icon, field, value, none) =>
    chip(label, icon, f => f[field] === value, f => copy(f, { [field]: f[field] === value ? none : value }));
  const GENRE_CHIPS = [
    ['Action', 'whatshot'], ['Sci-Fi', 'flightTakeoff'], ['Comedy', 'tagFaces'], ['Drama', 'theaters'], ['Horror', 'visibilityOff'],
    ['Fantasy', 'stars'], ['Thriller', 'warning'], ['Crime', 'gavel'], ['Romance', 'favorite'], ['Animation', 'brush'],
    ['Documentary', 'videocam'], ['Mystery', 'helpOutline'], ['War', 'security'], ['Family', 'people'], ['Adventure', 'explore'],
    ['History', 'accountBalance'], ['Music', 'musicNote'], ['Western', 'landscape'],
  ].map(([g, i]) => enumChip(g, i, 'genre', g, null));
  const TYPE_CHIPS = [
    enumChip('Movies', 'movie', 'typeFilter', 'MOVIE', 'ANY'), enumChip('Series', 'tv', 'typeFilter', 'TV_SHOW', 'ANY'),
    enumChip('Anime', 'brush', 'typeFilter', 'ANIME', 'ANY'),
  ];
  const SORT_CHIPS = [['Popular', 'trendingUp', 'POPULARITY'], ['Top Rated', 'star', 'RATING'], ['Newest', 'newReleases', 'NEWEST'], ['A → Z', 'sortByAlpha', 'TITLE']]
    .map(([l, i, v]) => chip(l, i, f => f.sortBy === v, f => copy(f, { sortBy: v })));
  const ratingChip = (l, i, tier, min) => chip(l, i, f => f.ratingTier === tier,
    f => copy(f, { ratingTier: f.ratingTier === tier ? 'ANY' : tier, minRating: f.ratingTier === tier ? 0 : min }));
  const RATING_CHIPS = [ratingChip('9+ Masterpiece', 'emojiEvents', 'MASTERPIECE', 9), ratingChip('7+ Great', 'thumbUp', 'GREAT', 7), ratingChip('5+ Good', 'thumbsUpDown', 'GOOD', 5)];
  const eraChip = (l, i, a, b) => chip(l, i, f => f.minYear === a && f.maxYear === b,
    f => (f.minYear === a ? copy(f, { minYear: 1920, maxYear: 2026 }) : copy(f, { minYear: a, maxYear: b })));
  const ERA_CHIPS = [
    eraChip('2020s', 'dateRange', 2020, 2026), eraChip('2010s', 'dateRange', 2010, 2019), eraChip('2000s', 'eventNote', 2000, 2009),
    eraChip('90s', 'history', 1990, 1999), eraChip('80s', 'history', 1980, 1989),
    chip('Classics', 'schedule', f => f.minYear === 1920 && f.maxYear === 1979,
      f => (f.maxYear === 1979 ? copy(f, { minYear: 1920, maxYear: 2026 }) : copy(f, { minYear: 1920, maxYear: 1979 }))),
  ];
  const RUNTIME_CHIPS = [
    enumChip('< 90 min', 'timer', 'runtime', 'SHORT', 'ANY'), enumChip('90-120 min', 'schedule', 'runtime', 'MEDIUM', 'ANY'),
    enumChip('2-3 Hours', 'hourglassEmpty', 'runtime', 'LONG', 'ANY'), enumChip('3+ Hours', 'hourglassFull', 'runtime', 'EPIC', 'ANY'),
  ];
  const LANG_CHIPS = [['English', 'ENGLISH'], ['Hebrew', 'HEBREW'], ['Korean', 'KOREAN'], ['Japanese', 'JAPANESE'], ['Spanish', 'SPANISH'], ['French', 'FRENCH']]
    .map(([l, v]) => enumChip(l, 'language', 'language', v, 'ANY'));
  const NETWORK_CHIPS = [['Netflix', 'play', 'NETFLIX'], ['HBO', 'liveTv', 'HBO'], ['Apple TV+', 'desktopWindows', 'APPLE_TV'], ['Disney+', 'face', 'DISNEY_PLUS'], ['Amazon', 'shoppingCart', 'AMAZON'], ['Hulu', 'tv', 'HULU']]
    .map(([l, i, v]) => enumChip(l, i, 'network', v, 'ANY'));
  const QUALITY_CHIPS = [['4K UHD', 'UHD'], ['1080p FHD', 'FHD'], ['720p HD', 'HD']].map(([l, v]) => enumChip(l, 'hd', 'quality', v, 'ANY'));
  const MOOD_CHIPS = [['Feel Good', 'wbSunny', 'Feel Good'], ['Dark & Gritty', 'brightness2', 'Dark'], ['Mind-Bending', 'lens', 'Mind-Bending'], ['Epic', 'public', 'Epic'], ['Romantic', 'favoriteBorder', 'Romantic']]
    .map(([l, i, v]) => enumChip(l, i, 'mood', v, null));
  const DUB_CHIP = chip('Hebrew Dubbed', 'recordVoiceOver', f => f.dubbedOnly, f => copy(f, { dubbedOnly: !f.dubbedOnly }));
  const FILTER_TABS = [
    ['Genre', 'list', GENRE_CHIPS], ['Type', 'videoLibrary', TYPE_CHIPS], ['Sort', 'sort', SORT_CHIPS], ['Rating', 'star', RATING_CHIPS],
    ['Era', 'dateRange', ERA_CHIPS], ['Runtime', 'accessTime', RUNTIME_CHIPS], ['Quality', 'hd', QUALITY_CHIPS], ['Mood', 'face', MOOD_CHIPS],
    ['Language', 'language', LANG_CHIPS], ['Platform', 'tv', NETWORK_CHIPS],
  ].map(([title, icon, chips]) => ({ title, icon, chips }));
  const activeCount = (tab, f) => tab.chips.filter(c => c.sel(f)).length;
  const QUICK_PRESETS = [
    ['Action Movies', 'whatshot', () => F({ genre: 'Action', typeFilter: 'MOVIE' })],
    ['Comedy Series', 'tagFaces', () => F({ genre: 'Comedy', typeFilter: 'TV_SHOW' })],
    ['Award Winners', 'star', () => F({ ratingTier: 'MASTERPIECE', minRating: 9, sortBy: 'RATING' })],
    ['New Releases', 'newReleases', () => F({ minYear: 2024, maxYear: 2026, sortBy: 'NEWEST' })],
    ['Anime', 'brush', () => F({ typeFilter: 'ANIME' })],
    ['Horror Night', 'visibilityOff', () => F({ genre: 'Horror', mood: 'Dark' })],
  ];
  const SECTION_HINTS = {
    Genre: 'Pick a genre to explore', Type: 'Movies, series, or anime', Sort: 'How to order results', Rating: 'Filter by quality score',
    Era: 'Pick a time period', Runtime: 'How long do you have?', Quality: 'Stream resolution', Mood: 'What are you in the mood for?',
    Language: 'Original language', Platform: 'Streaming service',
  };
  const cap = t => t.charAt(0).toUpperCase() + t.slice(1);
  function filterSummary(f) {
    const p = [];
    if (f.genre) p.push(f.genre);
    if (f.typeFilter !== 'ANY') p.push({ MOVIE: 'Movies', TV_SHOW: 'Series', ANIME: 'Anime' }[f.typeFilter]);
    if (f.ratingTier !== 'ANY') p.push({ MASTERPIECE: '9+★', GREAT: '7+★', GOOD: '5+★' }[f.ratingTier]);
    if (f.minYear > 1920 || f.maxYear < 2026) p.push(`${f.minYear}-${f.maxYear}`);
    if (f.quality !== 'ANY') p.push({ UHD: '4K', FHD: '1080p', HD: '720p' }[f.quality]);
    if (f.mood) p.push(f.mood);
    if (f.language !== 'ANY') p.push(cap(f.language.toLowerCase()));
    if (f.network !== 'ANY') p.push(cap(f.network.toLowerCase().replace(/_/g, ' ')));
    if (f.sortBy !== 'POPULARITY') p.push(`Sort: ${cap(f.sortBy.toLowerCase())}`);
    if (f.dubbedOnly) p.push('Dubbed');
    return p.join(' · ');
  }

  /* ── TMDB (MediaRepositoryImpl: searchMulti, getDiscoverySearch, discoverFiltered) ── */
  const HEB = /[֐-׿]/;
  /** TmdbMediaDto.toSearchResult — keeps the Movie fields the details screen reads. */
  function toResult(j, type) {
    const date = String((type === 'tv' ? j.first_air_date : j.release_date) || '');
    const releaseYear = date.slice(0, 4);
    const ok = p => (p && p !== 'null' ? p : '');
    return {
      key: `${type}_${j.id}`, id: j.id, type,
      title: j.title || j.name || 'Unknown',
      poster: ok(j.poster_path), backdrop: ok(j.backdrop_path),
      overview: j.overview || '',
      rating: Number(j.vote_average) || 0,
      releaseYear, year: Number(releaseYear) || 0,
      genre: (j.genre_ids || []).map(id => GENRE_NAME[id] || '').filter(Boolean).join(', '),
      genreIds: j.genre_ids || [],
      qualityTag: '',
    };
  }
  const distinct = list => {
    const seen = new Set();
    return list.filter(r => (seen.has(r.key) ? false : seen.add(r.key)));
  };
  const tagged = (data, type) => (data.results || []).map(j => [j, type]);

  async function searchMulti(query, page, isHebrew) {
    try {
      const d = await TV.tmdb('/search/multi', { query, language: isHebrew ? 'he-IL' : 'en-US', page, include_adult: false });
      return (d.results || []).filter(j => (j.media_type === 'movie' || j.media_type === 'tv') && j.poster_path != null)
        .map(j => toResult(j, j.media_type));
    } catch { return []; }
  }
  async function discoverySearch(page) {
    try {
      const q = { language: 'en-US', page, sort_by: 'popularity.desc' };
      const [m, t] = await Promise.all([TV.tmdb('/discover/movie', q), TV.tmdb('/discover/tv', q)]);
      return tagged(m, 'movie').concat(tagged(t, 'tv')).filter(([j]) => j.poster_path != null)
        .map(([j, type]) => toResult(j, type)).sort((a, b) => b.rating - a.rating);
    } catch { return []; }
  }
  async function discoverFiltered(o, page) {
    const language = TV.rtl() ? 'he' : 'en-US';
    const isMovie = o.type === 'movie';
    const isBoth = o.type === 'both';
    try {
      const calls = [];
      if (isBoth || isMovie) {
        calls.push(TV.tmdb('/discover/movie', {
          with_genres: o.genreId, 'primary_release_date.gte': o.dateGte, 'primary_release_date.lte': o.dateLte,
          'vote_average.gte': o.voteGte, with_original_language: o.lang, 'with_runtime.gte': o.rtGte, 'with_runtime.lte': o.rtLte,
          sort_by: o.sort, language, page,
        }).then(d => tagged(d, 'movie')));
      }
      if (isBoth || !isMovie) {
        calls.push(TV.tmdb('/discover/tv', {
          with_genres: o.tvGenreId != null ? o.tvGenreId : o.genreId, 'first_air_date.gte': o.dateGte, 'first_air_date.lte': o.dateLte,
          'vote_average.gte': o.voteGte, with_original_language: o.lang, with_networks: o.networkId,
          'with_runtime.gte': o.rtGte, 'with_runtime.lte': o.rtLte, sort_by: o.sort, language, page,
        }).then(d => tagged(d, 'tv')));
      }
      const res = [].concat(...await Promise.all(calls));
      return distinct(res.filter(([j]) => j.poster_path != null).map(([j, type]) => toResult(j, type)));
    } catch { return []; }
  }

  /** Compose on Android TV (FEATURE_LEANBACK): the focused item's leading edge goes to 30% of the list. */
  function pivot(start, size, view, total) {
    let target = 0.3 * view;
    if (size <= view && view - target < size) target = view - size;
    return Math.max(0, Math.min(start - target, Math.max(0, total - view)));
  }
  const xLeft = (x, w, full) => (TV.rtl() ? full - x - w : x);
  const centerX = e => { const r = e.getBoundingClientRect(); return (r.left + r.right) / 2; };
  /** Geometric fallback (Compose 2D focus search, simplified): the candidate closest in x. */
  function nearestX(fromEl, cands, elOf) {
    if (!fromEl || !cands.length) return cands[0] || null;
    const cx = centerX(fromEl);
    let best = null;
    let bd = Infinity;
    cands.forEach(c => {
      const e = elOf ? elOf(c) : c;
      const r = e.getBoundingClientRect();
      if (r.right <= 0 || r.left >= window.innerWidth) return;
      const d = Math.abs((r.left + r.right) / 2 - cx);
      if (d < bd) { bd = d; best = c; }
    });
    return best || cands[0];
  }

  /* ════════════════════════════════════════════════════════════════════ */
  function search() {
    const root = el(`
      <div class="screen sx">
        <div class="sx-bd"><img alt=""><i></i></div><div class="sx-bd"><img alt=""><i></i></div>
        <div class="sx-col">
          <div class="sx-nav"><div class="sx-navin"></div></div>
          <div class="sx-chipwrap hidden"><div class="sx-chips"></div></div>
          <div class="sx-content"></div>
        </div>
        <div class="sx-ov"></div>
      </div>`);
    const bdLayers = [...root.querySelectorAll('.sx-bd')];
    const navInner = TV.$(root, '.sx-navin');
    const chipsWrap = TV.$(root, '.sx-chipwrap');
    const chipsTrack = TV.$(root, '.sx-chips');
    const contentEl = TV.$(root, '.sx-content');
    const ovEl = TV.$(root, '.sx-ov');

    // The text field (BasicTextField) lives across nav rebuilds.
    const field = el(`<div class="sx-field"><input class="sx-input" type="text" dir="auto" autocomplete="off" spellcheck="false" placeholder="${esc(tr('Search movies, series...', 'חיפוש סרטים, סדרות...'))}"><i class="sx-caret"></i><span class="sx-meas"></span></div>`);
    const input = TV.$(field, '.sx-input');
    const caret = TV.$(field, '.sx-caret');
    const meas = TV.$(field, '.sx-meas');

    const St = window.Storage;
    const loadHistory = () => { try { return ((St && St.getSearchHistory()) || []).filter(t => t && String(t).trim()).slice(0, 8); } catch { return []; } };

    const s = {
      query: '', source: 'all', filters: F({}), showFilters: false,
      tmdbResults: [], tmdbLoading: false,
      discovery: [], discoveryLoading: false,
      history: loadHistory(),
      searchOpen: false,
      zone: 'content', navId: 'tab0', chipIdx: 0,
      pendingFirst: true,
    };
    let alive = true;
    let kbVisible = false;
    let C = null;            // content view
    let contentSig = null;
    let lastVH = 0;
    let navSig = null;
    let navEls = {};
    let navOrder = [];
    let chipsSig = null;
    let chipEls = [];
    let focusedEl = null;
    const timers = new Set();
    const later = (fn, ms) => { const t = setTimeout(() => { timers.delete(t); if (alive) fn(); }, ms); timers.add(t); return t; };

    /* ── Geometry of the column (nav 28+38/42+16, chips 36+16) ── */
    const chipsVisible = () => s.history.length > 0 && !s.query.trim();
    const navH = () => 28 + (s.searchOpen ? 42 : 38) + 16;
    const viewH = () => H - navH() - (chipsVisible() ? 52 : 0);

    /* ── ViewModel ─────────────────────────────────────────────────── */
    const isFuzer = () => s.source === 'fuzer';
    function isLoading() {
      if (isFuzer()) return false;
      return s.query.trim() ? s.tmdbLoading : s.discoveryLoading;
    }
    function applyFilters(list, skipServerFiltered) {
      const f = s.filters;
      let r = list;
      if (f.typeFilter === 'MOVIE') r = r.filter(x => x.type === 'movie');
      else if (f.typeFilter === 'TV_SHOW') r = r.filter(x => x.type === 'tv');
      else if (f.typeFilter === 'ANIME') r = r.filter(x => x.genre.toLowerCase().includes('animation'));
      if (f.genre != null && !skipServerFiltered) r = r.filter(x => x.genre.toLowerCase().includes(f.genre.toLowerCase()));
      if (f.minRating > 0 && !skipServerFiltered) r = r.filter(x => x.rating >= f.minRating);
      if ((f.minYear > 1920 || f.maxYear < 2026) && !skipServerFiltered) {
        r = r.filter(x => {
          if (!/^[+-]?\d+$/.test(x.releaseYear)) return true;
          const y = Number(x.releaseYear);
          return y >= f.minYear && y <= f.maxYear;
        });
      }
      const q = { HD: 'hd', FHD: 'fhd', UHD: '4k' }[f.quality];
      if (q) r = r.filter(x => (x.qualityTag || '').toLowerCase() === q);
      if (f.dubbedOnly && isFuzer()) r = r.filter(x => x.title.includes('מדובב'));
      const yr = x => (/^[+-]?\d+$/.test(x.releaseYear) ? Number(x.releaseYear) : 0);
      if (f.sortBy === 'RATING') r = r.slice().sort((a, b) => b.rating - a.rating);
      else if (f.sortBy === 'NEWEST') r = r.slice().sort((a, b) => (yr(b) - yr(a)) || (b.rating - a.rating));
      else if (f.sortBy === 'TITLE') r = r.slice().sort((a, b) => (a.title < b.title ? -1 : a.title > b.title ? 1 : 0));
      return r;
    }
    function activeResults() {
      const blank = !s.query.trim();
      let base;
      if (isFuzer()) base = [];
      else if (blank) base = s.discovery;
      else if (s.source === 'movies') base = s.tmdbResults.filter(r => r.type === 'movie');
      else if (s.source === 'series') base = s.tmdbResults.filter(r => r.type === 'tv');
      else base = s.tmdbResults;
      return isActive(s.filters) ? applyFilters(base, blank && !isFuzer()) : base;
    }

    let searchTimer = 0;
    let searchToken = 0;
    function updateQuery(q) {
      s.query = q;
      if (input.value !== q) input.value = q;
      clearTimeout(searchTimer);
      timers.delete(searchTimer);
      searchToken++;
      // A cancelled search lands in runTmdbSearch's catch: empty results, not loading.
      if (s.tmdbLoading) { s.tmdbResults = []; s.tmdbLoading = false; }
      if (!q.trim()) { s.tmdbResults = []; return; }
      const t = searchToken;
      searchTimer = later(() => runSearch(q, t), 300);
    }
    async function runSearch(q, t) {
      try { if (St && (!TV.prefs || TV.prefs.saveSearchHistory())) St.addSearchHistory(q); } catch {}
      s.history = loadHistory();
      s.tmdbLoading = true;
      render();
      const he = HEB.test(q);
      // 3 pages in the query's language, plus 2 English pages for a Hebrew query (bilingual).
      const pages = [1, 2, 3].map(p => searchMulti(q, p, he)).concat(he ? [1, 2].map(p => searchMulti(q, p, false)) : []);
      const all = [].concat(...await Promise.all(pages));
      if (!alive || t !== searchToken) return;
      s.tmdbResults = distinct(all).filter(r => r.poster);
      s.tmdbLoading = false;
      render();
    }

    let discToken = 0;
    async function loadDiscovery() {
      const t = ++discToken;
      s.discoveryLoading = true;
      const pages = await Promise.all([1, 2, 3].map(discoverySearch));
      if (!alive || t !== discToken) return;
      s.discovery = distinct([].concat(...pages)).filter(r => r.poster);
      s.discoveryLoading = false;
      render();
    }
    async function loadFilteredDiscovery(f) {
      if (!isActive(f)) { loadDiscovery(); return; }
      const t = ++discToken;
      s.discoveryLoading = true;
      const type = f.typeFilter === 'MOVIE' ? 'movie' : (f.typeFilter === 'TV_SHOW' || f.typeFilter === 'ANIME') ? 'tv' : 'both';
      const movieGenreId = f.genre ? MOVIE_GENRE_ID[f.genre.toLowerCase()] : null;
      const tvGenreId = f.genre ? TV_GENRE_ID[f.genre.toLowerCase()] : null;
      const range = f.minYear > 1920 || f.maxYear < 2026;
      const [rtGte, rtLte] = { SHORT: [null, 90], MEDIUM: [90, 120], LONG: [120, 180], EPIC: [180, null] }[f.runtime] || [null, null];
      const o = {
        type,
        genreId: type === 'tv' ? tvGenreId : movieGenreId,
        tvGenreId,
        dateGte: range ? `${f.minYear}-01-01` : null,
        dateLte: range ? `${f.maxYear}-12-31` : null,
        voteGte: f.ratingTier === 'MASTERPIECE' ? 8 : f.ratingTier === 'GREAT' ? 7 : f.ratingTier === 'GOOD' ? 6 : f.minRating > 0 ? f.minRating : null,
        lang: { ENGLISH: 'en', HEBREW: 'he', KOREAN: 'ko', JAPANESE: 'ja', SPANISH: 'es', FRENCH: 'fr' }[f.language] || null,
        networkId: { NETFLIX: '213', HBO: '49', APPLE_TV: '2552', DISNEY_PLUS: '2739', AMAZON: '1024', HULU: '453' }[f.network] || null,
        rtGte, rtLte,
        sort: { RATING: 'vote_average.desc', NEWEST: 'primary_release_date.desc', TITLE: 'original_title.asc', POPULARITY: 'popularity.desc' }[f.sortBy],
      };
      const maxPages = f.genre != null && type === 'both' ? 8 : f.genre != null ? 12 : 5;
      const all = [];
      for (let batch = 1; batch <= maxPages; batch += 5) {
        const pages = [];
        for (let p = batch; p < Math.min(batch + 5, maxPages + 1); p++) pages.push(p);
        const res = await Promise.all(pages.map(p => discoverFiltered(o, p)));
        if (!alive || t !== discToken) return;
        res.forEach(r => all.push(...r));
        // Progressive: show the first batch, still "loading" below 40 results.
        if (batch === 1 && all.length) {
          s.discovery = distinct(all).filter(r => r.poster);
          s.discoveryLoading = all.length < 40;
          render();
        }
      }
      s.discovery = distinct(all).filter(r => r.poster);
      s.discoveryLoading = false;
      render();
    }

    // LaunchedEffect(state.filters.genre): 200 ms later, focus the first result (if it is there).
    function genreChanged() {
      later(() => {
        if (s.showFilters || !C || !C.focusable) return;
        goContentFirst();
        paint();
      }, 200);
    }
    function updateFilters(nf) {
      const g0 = s.filters.genre;
      s.filters = nf;
      loadFilteredDiscovery(nf);
      if (nf.genre !== g0) genreChanged();
    }
    function clearFilters() {
      const g0 = s.filters.genre;
      s.filters = F({});
      loadDiscovery();
      if (g0 !== null) genreChanged();
    }

    /* ── Backdrop (AnimatedContent: fade in 600 / out 400, image alpha 0.3 + gradient) ── */
    let bdCur = null;
    let bdFront = 0;
    let bdTimer = 0;
    function setBackdrop(path) {
      path = path || '';
      if (path === bdCur) return;
      bdCur = path;
      clearTimeout(bdTimer);
      timers.delete(bdTimer);
      if (!path) { bdLayers.forEach(l => l.classList.remove('on')); return; }
      const url = TV.img(path, 'original'); // Android: backdropUrl = original
      bdTimer = later(() => TV.decode(url).then(ok => {
        if (!ok || !alive || bdCur !== path) return;
        const next = bdLayers[1 - bdFront];
        next.firstChild.src = url;
        next.classList.add('on');
        bdLayers[bdFront].classList.remove('on');
        bdFront = 1 - bdFront;
      }), 120);
    }

    /* ── Focus helpers ─────────────────────────────────────────────── */
    function leaveInput() { if (document.activeElement === input) input.blur(); }
    function focusNav(id, byPointer) {
      s.pendingFirst = false;
      if (id !== 'input' || byPointer) leaveInput();
      s.zone = 'nav';
      s.navId = id;
      if (id === 'input' && !byPointer && document.activeElement !== input) input.focus();
      paint();
    }
    function focusChip(i, byPointer) {
      s.pendingFirst = false;
      leaveInput();
      s.zone = 'chips';
      s.chipIdx = i;
      if (!byPointer) scrollChips(i);
      paint();
    }
    function goContentFirst() {
      if (!C || !C.focusable) return false;
      leaveInput();
      s.zone = 'content';
      C.focusFirst();
      return true;
    }
    function applyPending() {
      if (s.pendingFirst && C && C.focusable) {
        s.pendingFirst = false;
        goContentFirst();
      }
    }
    function pointerContent(fn) {
      s.pendingFirst = false;
      leaveInput();
      s.zone = 'content';
      fn();
      paint();
    }

    /* ── Nav bar (collapsed: Categories pill, tabs, search, filter / expanded: field + close) ── */
    const TABS = [['all', tr('All', 'הכל')], ['movies', tr('Movies', 'סרטים')], ['series', tr('Shows', 'סדרות')], ['fuzer', 'Fuzer']];
    function renderNav() {
      const active = isActive(s.filters);
      const pill = !s.searchOpen && (active || !!s.query.trim());
      const sig = s.searchOpen ? 'open' : `c|${pill}|${s.source}|${active}`;
      if (sig === navSig) return;
      const switched = navSig != null && (navSig === 'open') !== (sig === 'open');
      navSig = sig;
      if (s.searchOpen) {
        navInner.className = 'sx-navin open';
        navInner.innerHTML = `
          <div class="sx-inbox" data-n="input">${ic('search', 20, 'sx-inic')}</div>
          <div class="sx-rb" data-n="close">${ic('close', 16)}</div>`;
        TV.$(navInner, '.sx-inbox').appendChild(field);
      } else {
        navInner.className = 'sx-navin';
        navInner.innerHTML = `
          ${pill ? `<div class="sx-pill" data-n="pill">${ic('back', 18, 'mir')}<span>${tr('Categories', 'קטגוריות')}</span></div>` : ''}
          <div class="sx-tabs">${TABS.map(([src, label], i) => `<div class="sx-tab${s.source === src ? ' sel' : ''}" data-n="tab${i}">${src === 'fuzer' ? '<span class="sx-gem">💎</span>' : ''}<span>${esc(label)}</span></div>`).join('')}</div>
          <div class="sx-grow"></div>
          <div class="sx-rb sx-search" data-n="search">${ic('search', 18)}</div>
          <div class="sx-rb${active ? ' act' : ''}" data-n="filter">${ic('tune', 18)}</div>`;
      }
      // AnimatedContent: the new bar fades in (200 ms).
      if (switched) { void navInner.offsetWidth; navInner.classList.add('sx-fade'); }
      navEls = {};
      navOrder = [];
      navInner.querySelectorAll('[data-n]').forEach(e => {
        const id = e.getAttribute('data-n');
        navEls[id] = e;
        navOrder.push(id);
        if (id === 'input') {
          TV.pointer(e, () => focusNav('input', true), () => { focusNav('input', true); input.focus(); });
        } else {
          TV.pointer(e, () => focusNav(id, true), () => { focusNav(id, true); navActivate(id); });
        }
      });
      if (s.zone === 'nav' && !navEls[s.navId]) s.navId = navEls.tab0 ? 'tab0' : navOrder[0];
    }
    function navActivate(id) {
      if (id === 'pill') {
        if (s.query.trim()) updateQuery('');
        if (isActive(s.filters)) clearFilters();
        s.searchOpen = false;
        s.pendingFirst = true;
        render();
        return;
      }
      if (/^tab\d$/.test(id)) { s.source = TABS[Number(id.slice(3))][0]; render(); return; }
      if (id === 'search') {
        s.searchOpen = true;
        render();
        s.zone = 'nav';
        s.navId = 'input';
        paint();
        later(() => { if (s.searchOpen && s.zone === 'nav' && s.navId === 'input' && !s.showFilters) input.focus(); }, 100);
        return;
      }
      if (id === 'filter') { openFilters(); return; }
      if (id === 'close') {
        leaveInput();
        s.searchOpen = false;
        updateQuery('');
        s.navId = 'search';
        render();
        return;
      }
      if (id === 'input') {
        if (document.activeElement === input) {
          // The LG keyboard's Enter: done typing → the results, as soon as they are there.
          input.blur();
          s.pendingFirst = true;
          applyPending();
          paint();
        } else input.focus();
      }
    }

    /* ── History chips (LazyRow, padding 48, spacing 10) ── */
    let chipScroll = 0;
    function renderChips() {
      const vis = chipsVisible();
      const sig = vis ? s.history.join('\u0001') : '';
      if (sig === chipsSig) return;
      chipsSig = sig;
      chipsWrap.classList.toggle('hidden', !vis);
      chipsTrack.innerHTML = vis ? s.history.map(t => `<div class="sx-chip">${ic('history', 14)}<span dir="auto">${esc(t)}</span></div>`).join('') : '';
      chipEls = [...chipsTrack.children];
      chipScroll = 0;
      chipsTrack.style.transform = '';
      chipEls.forEach((c, i) => TV.pointer(c, () => focusChip(i, true), () => { focusChip(i, true); chipActivate(i); }));
      s.chipIdx = Math.min(s.chipIdx, Math.max(0, chipEls.length - 1));
      if (s.zone === 'chips' && !chipEls.length) s.zone = 'content';
    }
    const chipStart = c => (TV.rtl() ? chipsTrack.offsetWidth - c.offsetLeft - c.offsetWidth : c.offsetLeft);
    function scrollChips(i) {
      const c = chipEls[i];
      const last = chipEls[chipEls.length - 1];
      if (!c || !last) return;
      const total = chipStart(last) + last.offsetWidth + PAD;
      const x = pivot(chipStart(c), c.offsetWidth, W, total);
      if (x === chipScroll) return;
      chipScroll = x;
      chipsTrack.style.transform = `translateX(${TV.rtl() ? x : -x}px)`;
    }
    function chipActivate(i) {
      const term = s.history[i];
      if (term == null) return;
      updateQuery(term);
      s.zone = 'content';
      s.pendingFirst = true;
      render();
    }

    /* ── Content ───────────────────────────────────────────────────── */
    function contentMode() {
      if (s.query.trim() || isActive(s.filters) || isFuzer()) {
        if (isFuzer()) return 'fuzer'; // Fuzer's sign-in needs a reCAPTCHA the TV can't do.
        if (isLoading()) return 'shimmer';
        const res = activeResults();
        if (!res.length) return 'empty';
        if (isActive(s.filters) && !s.query.trim()) return 'year';
        return 'grid';
      }
      return 'categories';
    }
    /** CategoryGrid: a unique live backdrop per category (Android's exact matching order). */
    function genreBackdrops(list) {
      const map = new Map();
      const used = new Set();
      const withBd = list.filter(r => r.backdrop);
      const bd = r => `b:${r.backdrop}`;
      if (withBd.length) { map.set(null, bd(withBd[0])); used.add(bd(withBd[0])); }
      CATEGORIES.forEach(cat => {
        if (cat.genre == null || map.has(cat.genre)) return;
        const g = cat.genre.toLowerCase();
        const m = withBd.find(r => r.genre.toLowerCase().includes(g) && !used.has(bd(r)))
          || withBd.find(r => r.genre.toLowerCase().includes(g.slice(0, 4)) && !used.has(bd(r)));
        if (m) { map.set(cat.genre, bd(m)); used.add(bd(m)); }
      });
      CATEGORIES.forEach(cat => {
        if (map.has(cat.genre)) return;
        const unused = withBd.find(r => !used.has(bd(r)));
        if (unused) { map.set(cat.genre, bd(unused)); used.add(bd(unused)); } else if (withBd.length) {
          const pf = list.find(r => r.poster && !used.has(`p:${r.poster}`));
          if (pf) { map.set(cat.genre, `p:${pf.poster}`); used.add(`p:${pf.poster}`); }
        }
      });
      return map;
    }
    function renderContent() {
      const mode = contentMode();
      let data = null;
      let sig = mode;
      if (mode === 'grid' || mode === 'year') {
        data = activeResults();
        sig += `|${data.map(r => r.key).join(',')}`;
      } else if (mode === 'categories') {
        data = genreBackdrops(s.discovery);
        sig += `|${CATEGORIES.map(c => data.get(c.genre) || '').join(',')}`;
      }
      const vh = viewH();
      if (sig !== contentSig) {
        contentSig = sig;
        lastVH = vh;
        const prevKey = C && C.curKey ? C.curKey() : null;
        contentEl.innerHTML = '';
        C = buildContent(mode, data);
        contentEl.appendChild(C.el);
        if (C.focusable && s.zone === 'content' && !s.pendingFirst) C.restore(prevKey);
      } else if (vh !== lastVH) {
        lastVH = vh;
        if (C.relayout) C.relayout();
      }
      applyPending();
    }
    function buildContent(mode, data) {
      if (mode === 'categories') return categoriesView(data);
      if (mode === 'grid') return gridView(data);
      if (mode === 'year') return yearView(data);
      if (mode === 'shimmer') return staticView(shimmerHtml());
      if (mode === 'fuzer') {
        return staticView(`
          <div class="sx-center"><div class="sx-fe">
            <div class="sx-fe-ic">${ic('cloudOff', 32)}</div>
            <div class="sx-fe-t">Fuzer Unavailable</div>
            <div class="sx-fe-m" dir="auto">${esc(tr(
              'Fuzer search needs a sign-in with a security check (reCAPTCHA) that the TV can\'t complete. Search Fuzer from the Windows or iPhone app.',
              'החיפוש ב-Fuzer דורש התחברות עם בדיקת אבטחה (reCAPTCHA) שהטלוויזיה לא יכולה להשלים. חפשו ב-Fuzer באפליקציית Windows או iPhone.'))}</div>
          </div></div>`);
      }
      return staticView(`<div class="sx-center"><div class="sx-empty">${ic('searchOff', 64)}<div class="sx-empty-t">${esc(tr('No results found', 'לא נמצאו תוצאות'))}</div></div></div>`);
    }
    function staticView(html) {
      return { el: el(`<div class="sx-static">${html}</div>`), focusable: false, has: false, focusEl: () => null, curKey: () => null };
    }
    /** SearchResultsGrid geometry: GridCells.Adaptive(150 dp), 48 dp sides, 18 dp spacing. */
    function adaptive(minSize, gap) {
      const avail = W - 2 * PAD;
      const cols = Math.max(1, Math.floor((avail + gap) / (minSize + gap)));
      const cw = (avail - gap * (cols - 1)) / cols;
      return { cols, cw, ch: cw * 1.5 };
    }
    function shimmerHtml() {
      const { cols, cw, ch } = adaptive(150, 18);
      let html = '';
      for (let i = 0; i < 18; i++) {
        const x = PAD + (i % cols) * (cw + 18);
        const y = 16 + Math.floor(i / cols) * (ch + 18);
        html += `<i class="sx-sh" style="left:${xLeft(x, cw, W)}px;top:${y}px;width:${cw}px;height:${ch}px"></i>`;
      }
      return html;
    }

    /* MediaSearchCard */
    function resultCard(r) {
      const q = /4K|2160p/i.test(r.title) ? '4K' : /1080p/i.test(r.title) ? 'FHD' : /720p/i.test(r.title) ? 'HD' : null;
      const qColor = q === '4K' ? '#FF3D00' : q === 'FHD' ? '#00D4FF' : '#00E676';
      const c = document.createElement('div');
      c.className = 'sx-card';
      c.innerHTML = `${r.poster ? `<img src="${TV.img(r.poster, 'w500')}" alt="" decoding="async">` : ''}<i class="sx-cg"></i>`
        + `${r.releaseYear ? `<div class="sx-tb"><span class="sx-badge" style="background:rgba(0,0,0,0.667)">${esc(r.releaseYear)}</span></div>` : ''}`
        + `${q ? `<div class="sx-te"><span class="sx-badge" style="background:${qColor}">${q}</span></div>` : ''}`
        + `<div class="sx-ci"><div class="sx-ct" dir="auto">${esc(r.title)}</div><div class="sx-cm">`
        + `${r.rating > 0 ? `<span class="sx-cr">★ ${r.rating.toFixed(1)}</span>` : ''}<span class="sx-cy">${r.type === 'tv' ? 'TV Show' : 'Movie'}</span></div></div>`;
      return c;
    }
    const openResult = r => { leaveInput(); TV.open('details', { id: r.id, type: r.type, movie: r }); };
    const focusResult = r => setBackdrop(r.backdrop);

    /** A uniform lazy grid (CategoryGrid / SearchResultsGrid), windowed. */
    function uniformGrid(o) {
      const { items, cols, cw, ch, gap, top, bottom, make, onFocusItem, onOpen, mode } = o;
      const v = { mode, items, focusable: items.length > 0, has: false, fi: 0, sy: 0, cards: new Map() };
      const rows = Math.ceil(items.length / cols);
      const total = top + rows * ch + Math.max(0, rows - 1) * gap + bottom;
      const xOf = i => PAD + (i % cols) * (cw + gap);
      const yOf = i => top + Math.floor(i / cols) * (ch + gap);
      v.el = el('<div class="sx-sc"></div>');
      v.el.style.height = `${total}px`;
      v.window = () => {
        const lo = v.sy - 400;
        const hi = v.sy + viewH() + 400;
        const r0 = Math.max(0, Math.floor((lo - top) / (ch + gap)));
        const r1 = Math.min(rows - 1, Math.floor((hi - top) / (ch + gap)));
        const keep = new Set();
        for (let r = r0; r <= r1; r++) {
          for (let c = 0; c < cols; c++) {
            const i = r * cols + c;
            if (i >= items.length) break;
            keep.add(i);
            if (v.cards.has(i)) continue;
            const e = make(items[i], i);
            e.style.cssText = `left:${xLeft(xOf(i), cw, W)}px;top:${yOf(i)}px;width:${cw}px;height:${ch}px`;
            TV.pointer(e, () => pointerContent(() => v.set(i, false)), () => { pointerContent(() => v.set(i, false)); onOpen(items[i]); });
            if (v.has && v.fi === i && s.zone === 'content') e.classList.add('f');
            v.el.appendChild(e);
            v.cards.set(i, e);
          }
        }
        v.cards.forEach((e, i) => { if (!keep.has(i)) { e.remove(); v.cards.delete(i); } });
      };
      v.scrollTo = y => {
        if (y !== v.sy) { v.sy = y; v.el.style.transform = `translateY(${-y}px)`; }
        v.window();
      };
      v.set = (i, scroll = true) => {
        v.fi = Math.max(0, Math.min(items.length - 1, i));
        v.has = true;
        if (scroll) v.scrollTo(pivot(yOf(v.fi), ch, viewH(), total)); else v.window();
        if (onFocusItem) onFocusItem(items[v.fi]);
      };
      v.focusFirst = () => v.set(0);
      v.restore = k => { const i = k == null ? -1 : items.findIndex(m => (m.key || m.en) === k); v.set(i < 0 ? 0 : i); };
      v.curKey = () => (v.has && items[v.fi] ? (items[v.fi].key || items[v.fi].en) : null);
      v.focusEl = () => (v.has ? v.cards.get(v.fi) || null : null);
      v.relayout = () => v.window();
      v.key = k => {
        const i = v.fi;
        const n = items.length;
        if (TV.fwd(k)) { if ((i + 1) % cols && i + 1 < n) v.set(i + 1); return true; }
        if (TV.bwd(k)) { if (i % cols) v.set(i - 1); return true; }
        if (k === 'down') { if (Math.floor(i / cols) < rows - 1) v.set(Math.min(i + cols, n - 1)); return true; }
        if (k === 'up') { if (i >= cols) { v.set(i - cols); return true; } return 'up'; }
        if (k === 'ok') { onOpen(items[i]); return true; }
        return true;
      };
      /** Cards of the first row in view (geometric entry from above). */
      v.topCands = () => {
        const r = Math.max(0, Math.min(rows - 1, Math.ceil((v.sy - top) / (ch + gap) - 0.001)));
        const out = [];
        for (let c = 0; c < cols; c++) {
          const i = r * cols + c;
          if (i < items.length && v.cards.get(i)) out.push({ el: v.cards.get(i), go: () => v.set(i) });
        }
        return out;
      };
      v.window();
      return v;
    }

    /* CategoryGrid: Fixed(5), spacing 14, padding 48/48/bottom 48, 16:9 PremiumCategoryCard. */
    function categoriesView(backdrops) {
      const cols = 5;
      const gap = 14;
      const cw = (W - 2 * PAD - gap * (cols - 1)) / cols;
      return uniformGrid({
        mode: 'categories', items: CATEGORIES, cols, cw, ch: cw * 9 / 16, gap, top: 0, bottom: 48,
        make: cat => {
          const ref = backdrops.get(cat.genre) || '';
          const url = ref.startsWith('b:') ? TV.img(ref.slice(2), 'w780') : ref.startsWith('p:') ? TV.img(ref.slice(2), 'w500') : '';
          const c = document.createElement('div');
          c.className = 'sx-cat';
          c.innerHTML = `<div class="sx-cat-in">${url ? `<img src="${url}" alt="" decoding="async">` : ''}<i class="sx-cat-g"></i></div>`
            + `<div class="sx-cat-t">${esc(TV.rtl() ? cat.he : cat.en)}</div>`;
          return c;
        },
        onOpen: openCategory,
      });
    }
    function openCategory(cat) {
      const typeFilter = s.source === 'movies' ? 'MOVIE' : s.source === 'series' ? 'TV_SHOW' : 'ANY';
      const pop = cat.genre === '_popular_';
      updateFilters(F({ genre: pop ? null : cat.genre, typeFilter, sortBy: 'POPULARITY', ratingTier: pop ? 'GOOD' : 'ANY' }));
      render();
    }

    function gridView(items) {
      const { cols, cw, ch } = adaptive(150, 18);
      return uniformGrid({
        mode: 'grid', items, cols, cw, ch, gap: 18, top: 16, bottom: 48,
        make: resultCard, onFocusItem: focusResult, onOpen: openResult,
      });
    }

    /* YearGroupedResults: big headers + LazyRow of 150 dp posters per decade. */
    function yearView(results) {
      const CW = 150;
      const CH = 225;
      const GAP = 14;
      const STEP = CW + GAP;
      const groups = [];
      YEAR_BUCKETS.forEach(b => {
        const items = b.special
          ? results.slice().sort((a, c) => c.rating - a.rating).slice(0, 30)
          : results.filter(r => {
            if (!/^[+-]?\d+$/.test(r.releaseYear)) return false;
            const y = Number(r.releaseYear);
            return y >= b.from && y <= b.to;
          }).sort((a, c) => c.rating - a.rating);
        if (items.length) groups.push({ b, items, sx: 0, cards: new Map(), headEl: null, rowEl: null, track: null });
      });
      let y = 0;
      groups.forEach((g, gi) => {
        g.hTop = y + (gi === 0 ? 8 : 24);
        y = g.hTop + 38;     // 32 header row + 4 + 2 line
        g.rTop = y + 12;     // Spacer(12)
        y = g.rTop + CH;
      });
      const total = y + 48;
      const rowW = n => 2 * PAD + n * CW + (n - 1) * GAP;
      const v = { mode: 'year', focusable: groups.length > 0, has: false, fr: 0, fc: 0, sy: 0 };
      v.el = el('<div class="sx-sc"></div>');
      v.el.style.height = `${total}px`;

      function headHtml(g) {
        const sp = !!g.b.special;
        return `<div class="sx-yh" style="top:${g.hTop}px">
          <div class="sx-yhr"><i class="sx-ybar" style="background:${sp ? GOLD : RED}"></i><span class="sx-yemo">${g.b.emoji}</span>`
          + `<span class="sx-ytt" dir="auto" style="color:${sp ? GOLD : '#fff'}">${esc(g.b.label)}</span>`
          + `<span class="sx-ycnt${sp ? ' gold' : ''}">${g.items.length}</span></div>`
          + `<i class="sx-yline" style="background:linear-gradient(to right, ${sp ? 'rgba(255,204,0,0.5)' : 'rgba(229,9,20,0.4)'}, transparent)"></i></div>`;
      }
      function rowWindow(g, gi) {
        const n = g.items.length;
        const i0 = Math.max(0, Math.floor((g.sx - 400 - PAD) / STEP));
        const i1 = Math.min(n - 1, Math.floor((g.sx + W + 400 - PAD) / STEP));
        const keep = new Set();
        for (let i = i0; i <= i1; i++) {
          keep.add(i);
          if (g.cards.has(i)) continue;
          const e = resultCard(g.items[i]);
          e.style.cssText = `left:${xLeft(PAD + i * STEP, CW, W)}px;top:0;width:${CW}px;height:${CH}px`;
          TV.pointer(e, () => pointerContent(() => v.set(gi, i, false)), () => { pointerContent(() => v.set(gi, i, false)); openResult(g.items[i]); });
          if (v.has && v.fr === gi && v.fc === i && s.zone === 'content') e.classList.add('f');
          g.track.appendChild(e);
          g.cards.set(i, e);
        }
        g.cards.forEach((e, i) => { if (!keep.has(i)) { e.remove(); g.cards.delete(i); } });
      }
      v.window = () => {
        const lo = v.sy - 400;
        const hi = v.sy + viewH() + 400;
        groups.forEach((g, gi) => {
          const vis = g.rTop + CH >= lo && g.hTop <= hi;
          if (!vis) {
            if (g.headEl) { g.headEl.remove(); g.rowEl.remove(); g.headEl = g.rowEl = g.track = null; g.cards.clear(); }
            return;
          }
          if (!g.headEl) {
            g.headEl = el(headHtml(g));
            g.rowEl = el(`<div class="sx-yr" style="top:${g.rTop}px;height:${CH}px"><div class="sx-yt"></div></div>`);
            g.track = g.rowEl.firstChild;
            if (g.sx) g.track.style.transform = `translateX(${TV.rtl() ? g.sx : -g.sx}px)`;
            v.el.appendChild(g.headEl);
            v.el.appendChild(g.rowEl);
          }
          rowWindow(g, gi);
        });
      };
      v.set = (r, c, scroll = true) => {
        const g = groups[r];
        v.fr = r;
        v.fc = Math.max(0, Math.min(g.items.length - 1, c));
        v.has = true;
        if (scroll) {
          const x = pivot(PAD + v.fc * STEP, CW, W, rowW(g.items.length));
          if (x !== g.sx) { g.sx = x; if (g.track) g.track.style.transform = `translateX(${TV.rtl() ? x : -x}px)`; }
          const yy = pivot(g.rTop, CH, viewH(), total);
          if (yy !== v.sy) { v.sy = yy; v.el.style.transform = `translateY(${-yy}px)`; }
        }
        v.window();
        focusResult(g.items[v.fc]);
      };
      v.focusFirst = () => v.set(0, 0);
      v.curKey = () => (v.has ? `${groups[v.fr].b.label}|${groups[v.fr].items[v.fc].key}` : null);
      v.restore = k => {
        if (k) {
          const [label, key] = [k.slice(0, k.indexOf('|')), k.slice(k.indexOf('|') + 1)];
          for (const pass of [true, false]) {
            for (let r = 0; r < groups.length; r++) {
              if (pass && groups[r].b.label !== label) continue;
              const c = groups[r].items.findIndex(m => m.key === key);
              if (c >= 0) { v.set(r, c); return; }
            }
          }
        }
        v.set(0, 0);
      };
      v.focusEl = () => (v.has && groups[v.fr] ? groups[v.fr].cards.get(v.fc) || null : null);
      v.relayout = () => v.window();
      // Geometric move to the card nearest in x in another row (rows scroll independently).
      const nearestIn = r => {
        const cur = groups[v.fr];
        const cx = PAD + v.fc * STEP + CW / 2 - cur.sx;
        const g = groups[r];
        const j = Math.round((cx + g.sx - PAD - CW / 2) / STEP);
        return Math.max(0, Math.min(g.items.length - 1, j));
      };
      v.key = k => {
        const g = groups[v.fr];
        if (TV.fwd(k)) { if (v.fc < g.items.length - 1) v.set(v.fr, v.fc + 1); return true; }
        if (TV.bwd(k)) { if (v.fc > 0) v.set(v.fr, v.fc - 1); return true; }
        if (k === 'down') { if (v.fr < groups.length - 1) v.set(v.fr + 1, nearestIn(v.fr + 1)); return true; }
        if (k === 'up') { if (v.fr > 0) { v.set(v.fr - 1, nearestIn(v.fr - 1)); return true; } return 'up'; }
        if (k === 'ok') { openResult(g.items[v.fc]); return true; }
        return true;
      };
      v.topCands = () => {
        const r = groups.findIndex(g => g.rTop >= v.sy - 1);
        const g = groups[r < 0 ? 0 : r];
        const out = [];
        g.cards.forEach((e, i) => out.push({ el: e, go: () => v.set(groups.indexOf(g), i) }));
        return out;
      };
      v.window();
      return v;
    }

    /* ── Paint (focus is a class, never DOM focus) ── */
    function paint() {
      let t = null;
      if (s.zone === 'nav') t = navEls[s.navId] || null;
      else if (s.zone === 'chips') t = chipEls[s.chipIdx] || null;
      else if (C) t = C.focusEl();
      if (s.showFilters) t = null;
      if (focusedEl && focusedEl !== t) focusedEl.classList.remove('f');
      if (t) t.classList.add('f');
      focusedEl = t;
      updateCaret();
    }
    /** The field's cursor while it is focused without the keyboard (Android always shows one). */
    function updateCaret() {
      const show = s.searchOpen && s.zone === 'nav' && s.navId === 'input' && document.activeElement !== input && !s.showFilters;
      caret.style.display = show ? 'block' : 'none';
      if (!show) return;
      meas.textContent = input.value;
      const w = meas.offsetWidth;
      const fw = field.offsetWidth;
      const m = /[A-Za-z֐-׿]/.exec(input.value);
      const textRtl = m ? HEB.test(m[0]) : TV.rtl();
      let x = TV.rtl() ? (textRtl ? fw - w : fw) : (textRtl ? 0 : w);
      x = Math.max(0, Math.min(fw - 2, x));
      caret.style.left = `${x}px`;
    }

    function render() {
      if (!alive) return;
      renderNav();
      renderChips();
      renderContent();
      if (s.showFilters) renderOverlay();
      paint();
    }

    /* ── Keys ──────────────────────────────────────────────────────── */
    function exitUpFromContent() {
      const mode = C ? C.mode : null;
      const from = C && C.focusEl ? C.focusEl() : null;
      const chipsOn = chipsVisible() && chipEls.length > 0;
      // CategoryGrid: up = first chip if there are chips, else the first tab (focusProperties).
      if (mode === 'categories' || !from) {
        if (chipsOn) { focusChip(0); return; }
        if (!s.searchOpen) { focusNav('tab0'); return; }
      }
      // YearGroupedResults: row 0 up = first tab.
      if (mode === 'year' && !s.searchOpen) { focusNav('tab0'); return; }
      // Otherwise geometry: the chips row if shown, else the nav item nearest in x.
      if (chipsOn) {
        const i = nearestX(from, chipEls.map((e, k) => k), k => chipEls[k]);
        focusChip(i == null ? 0 : i);
        return;
      }
      const id = nearestX(from, navOrder.slice(), k => navEls[k]);
      focusNav(id || navOrder[0]);
    }
    function closeDown() {
      const from = navEls.close;
      if (chipsVisible() && chipEls.length) {
        const i = nearestX(from, chipEls.map((e, k) => k), k => chipEls[k]);
        focusChip(i == null ? 0 : i);
        return;
      }
      if (!C || !C.focusable || !C.topCands) return;
      const cands = C.topCands();
      const best = nearestX(from, cands, c => c.el);
      if (!best) return;
      leaveInput();
      s.zone = 'content';
      best.go();
      paint();
    }
    function navKey(key) {
      const id = s.navId;
      const typing = document.activeElement === input;
      if (key === 'ok') { s.pendingFirst = false; navActivate(id); return true; }
      s.pendingFirst = false;
      const i = navOrder.indexOf(id);
      if (TV.fwd(key)) { if (i >= 0 && i < navOrder.length - 1) focusNav(navOrder[i + 1]); return true; }
      if (TV.bwd(key)) { if (i > 0) focusNav(navOrder[i - 1]); return true; }
      if (key === 'down') {
        if (id === 'close') { closeDown(); return true; }
        // Tabs (onPreviewKeyEvent), pill, search, filter, field: down = firstResultFR.
        if (C && C.focusable) { if (typing) input.blur(); goContentFirst(); paint(); }
        return true;
      }
      return true;
    }
    function chipsKey(key) {
      s.pendingFirst = false;
      if (TV.fwd(key)) { if (s.chipIdx < chipEls.length - 1) focusChip(s.chipIdx + 1); return true; }
      if (TV.bwd(key)) { if (s.chipIdx > 0) focusChip(s.chipIdx - 1); return true; }
      if (key === 'up') {
        if (!s.searchOpen) focusNav('tab0');
        else focusNav(nearestX(chipEls[s.chipIdx], navOrder.slice(), k => navEls[k]) || navOrder[0]);
        return true;
      }
      if (key === 'down') { if (goContentFirst()) paint(); return true; }
      if (key === 'ok') { chipActivate(s.chipIdx); return true; }
      return true;
    }
    function contentKey(key) {
      if (!C || !C.focusable || !C.has) {
        // Nothing focused (the focused card went away): up leaves, other directions enter the list.
        if (key === 'up') exitUpFromContent();
        else if (C && C.focusable && key !== 'ok') { goContentFirst(); paint(); }
        return true;
      }
      s.pendingFirst = false;
      const r = C.key(key);
      if (r === 'up') exitUpFromContent();
      else paint();
      return true;
    }
    /** BackHandler order: filters overlay → clear filters → clear query → leave. */
    function backKey() {
      if (s.zone === 'nav' && s.navId === 'input' && s.searchOpen) {
        if (document.activeElement === input && kbVisible) { input.blur(); paint(); return true; }
        // onPreviewKeyEvent(Back) in the field: close the search, clear it, focus the first result.
        leaveInput();
        s.searchOpen = false;
        updateQuery('');
        s.pendingFirst = true;
        render();
        return true;
      }
      if (isActive(s.filters)) { clearFilters(); render(); return true; }
      if (s.query.trim()) { updateQuery(''); render(); return true; }
      return false;
    }

    // Typing: the field keeps left/right (core.js); at the field's edge they leave toward Close.
    input.addEventListener('keydown', e => {
      const k = TV.keyOf(e);
      if ((k !== 'left' && k !== 'right') || !TV.fwd(k)) return;
      const v = input.value;
      if (input.selectionStart !== input.selectionEnd) return;
      const m = /[A-Za-z֐-׿]/.exec(v);
      const textRtl = m ? HEB.test(m[0]) : TV.rtl();
      const edge = k === 'left' ? (textRtl ? v.length : 0) : (textRtl ? 0 : v.length);
      if (input.selectionStart !== edge) return;
      e.preventDefault();
      focusNav('close');
    });
    input.addEventListener('input', () => {
      s.pendingFirst = false;
      updateQuery(input.value);
      render();
    });
    input.addEventListener('focus', () => { kbVisible = true; if (s.zone !== 'nav' || s.navId !== 'input') { s.zone = 'nav'; s.navId = 'input'; } paint(); });
    input.addEventListener('blur', () => { kbVisible = false; paint(); });
    const onKbState = e => { kbVisible = !!(e.detail && e.detail.visibility); };
    document.addEventListener('keyboardStateChange', onKbState);

    /* ── Filter overlay (SearchFilterSidebar: rail + chips) ────────── */
    const ov = { tab: 0, key: 'tab:0' };
    function chipHtml(c, f, k) {
      const sel = c.sel(f);
      return `<div class="sx-fc${sel ? ' sel' : ''}" data-k="${k}">${ic(c.icon, 16, 'sx-fci')}<span>${esc(c.label)}</span>${sel ? ic('check', 14, 'sx-fck') : ''}</div>`;
    }
    function renderOverlay() {
      const f = s.filters;
      const total = FILTER_TABS.reduce((n, t) => n + activeCount(t, f), 0) + (f.dubbedOnly ? 1 : 0);
      const cur = FILTER_TABS[ov.tab] || FILTER_TABS[0];
      const cnt = activeCount(cur, f);
      ovEl.innerHTML = `
        <div class="sx-rail">
          <div class="sx-rh">Filters</div>
          ${total > 0 ? `<div class="sx-ra">${total} active</div>` : ''}
          <div class="sx-rtabs">${FILTER_TABS.map((t, i) => {
            const a = activeCount(t, f);
            return `<div class="sx-rtab${i === ov.tab ? ' sel' : ''}" data-k="tab:${i}"><i class="sx-acc"></i>${ic(t.icon, 16)}<span class="sx-rtt">${t.title}</span>${a > 0 ? `<span class="sx-rbadge">${a}</span>` : ''}</div>`;
          }).join('')}</div>
          ${isActive(f) ? `<div class="sx-rbtn clr" data-k="clear">${ic('delete', 14)}<span>Clear All</span></div>` : ''}
          <div class="sx-rbtn cls" data-k="close">${ic('close', 14)}<span>Close</span></div>
        </div>
        <div class="sx-panel">
          <div class="sx-ph">${ic(cur.icon, 22, 'sx-red')}<div class="sx-pht"><div class="sx-ptt">${cur.title}</div><div class="sx-phint">${SECTION_HINTS[cur.title]}</div></div>${cnt > 0 ? `<span class="sx-pcnt">${cnt} selected</span>` : ''}</div>
          <i class="sx-pdiv"></i>
          ${ov.tab === 0 ? `<div class="sx-plbl">Quick Presets</div><div class="sx-pres">${QUICK_PRESETS.map((p, i) => `<div class="sx-pre" data-k="pre:${i}">${ic(p[1], 13)}<span>${p[0]}</span></div>`).join('')}</div>` : ''}
          <div class="sx-flow">${cur.chips.map((c, i) => chipHtml(c, f, `chip:${i}`)).join('')}</div>
          ${isFuzer() && ov.tab === 0 ? `<i class="sx-fzl"></i><div class="sx-fzt">Fuzer</div><div class="sx-fzc">${chipHtml(DUB_CHIP, f, 'dub')}</div>` : ''}
          ${total > 0 ? `<div class="sx-sum">${ic('filterList', 16)}<span dir="auto">${esc(filterSummary(f))}</span></div>` : ''}
        </div>`;
      ovEl.querySelectorAll('[data-k]').forEach(e => {
        const k = e.getAttribute('data-k');
        TV.pointer(e, () => { ov.key = k; ovPaint(); }, () => { ov.key = k; ovPaint(); ovActivate(k); });
      });
      ovPaint();
    }
    function ovPaint() {
      let found = false;
      ovEl.querySelectorAll('[data-k]').forEach(e => {
        const on = e.getAttribute('data-k') === ov.key;
        e.classList.toggle('f', on);
        found = found || on;
      });
      if (!found) { ov.key = `tab:${ov.tab}`; ovPaint(); }
    }
    function openFilters() {
      leaveInput();
      s.showFilters = true;
      ov.tab = 0;          // the sidebar's selectedTab is remembered only while it is shown
      ov.key = 'tab:0';    // filterFR
      renderOverlay();
      ovEl.classList.add('on');
      paint();
    }
    function closeFilters() {
      s.showFilters = false;
      ovEl.classList.remove('on');
      // Focus comes back to the filter button (the genre effect may move it to the first result).
      if (!s.searchOpen && navEls.filter) { s.zone = 'nav'; s.navId = 'filter'; }
      paint();
    }
    function ovActivate(k) {
      const [kind, n] = k.split(':');
      if (kind === 'tab') { ov.tab = Number(n); renderOverlay(); return; }
      if (kind === 'pre') { updateFilters(QUICK_PRESETS[Number(n)][2]()); render(); return; }
      if (kind === 'chip') { updateFilters(FILTER_TABS[ov.tab].chips[Number(n)].toggle(s.filters)); render(); return; }
      if (kind === 'dub') { updateFilters(DUB_CHIP.toggle(s.filters)); render(); return; }
      if (kind === 'clear') { clearFilters(); closeFilters(); render(); return; }
      if (kind === 'close') closeFilters();
    }
    /** Compose 2D focus search inside the overlay (focus can't leave it). */
    function ovMove(key) {
      const from = ovEl.querySelector(`[data-k="${ov.key}"]`);
      if (!from) return;
      const a = from.getBoundingClientRect();
      const acx = (a.left + a.right) / 2;
      const acy = (a.top + a.bottom) / 2;
      let best = null;
      let bestScore = Infinity;
      ovEl.querySelectorAll('[data-k]').forEach(e => {
        if (e === from) return;
        const b = e.getBoundingClientRect();
        const bcx = (b.left + b.right) / 2;
        const bcy = (b.top + b.bottom) / 2;
        let major;
        let minor;
        let beam;
        if (key === 'right') {
          if (bcx <= acx + 1 || b.left < a.left + 1) return;
          major = Math.max(0, b.left - a.right); minor = Math.abs(bcy - acy); beam = b.top < a.bottom && b.bottom > a.top;
        } else if (key === 'left') {
          if (bcx >= acx - 1 || b.right > a.right - 1) return;
          major = Math.max(0, a.left - b.right); minor = Math.abs(bcy - acy); beam = b.top < a.bottom && b.bottom > a.top;
        } else if (key === 'down') {
          if (bcy <= acy + 1 || b.top < a.top + 1) return;
          major = Math.max(0, b.top - a.bottom); minor = Math.abs(bcx - acx); beam = b.left < a.right && b.right > a.left;
        } else {
          if (bcy >= acy - 1 || b.bottom > a.bottom - 1) return;
          major = Math.max(0, a.top - b.bottom); minor = Math.abs(bcx - acx); beam = b.left < a.right && b.right > a.left;
        }
        const score = (beam ? 0 : 1e12) + 13 * major * major + minor * minor;
        if (score < bestScore) { bestScore = score; best = e; }
      });
      if (best) { ov.key = best.getAttribute('data-k'); ovPaint(); }
    }
    function ovKey(key) {
      if (key === 'back') { closeFilters(); return true; }
      if (key === 'ok') { ovActivate(ov.key); return true; }
      if (key === 'up' || key === 'down' || key === 'left' || key === 'right') { ovMove(key); return true; }
      return true;
    }

    /* ── Start (Android: discovery + history load, focus the first category) ── */
    input.value = '';
    loadDiscovery();
    render();

    return {
      el: root,
      onKey(key) {
        if (s.showFilters) return ovKey(key);
        if (key === 'back') return backKey();
        if (s.zone === 'nav') return navKey(key);
        if (s.zone === 'chips') return chipsKey(key);
        return contentKey(key);
      },
      onShow(first) {
        if (first) return;
        // Back from a title: Android recomposes the screen — the search bar closes, the
        // backdrop clears and the first result takes focus (LaunchedEffect(Unit)).
        leaveInput();
        s.searchOpen = false;
        setBackdrop('');
        s.history = loadHistory();
        s.zone = 'content';
        s.pendingFirst = true;
        render();
      },
      destroy() {
        alive = false;
        timers.forEach(t => clearTimeout(t));
        timers.clear();
        clearTimeout(searchTimer);
        document.removeEventListener('keyboardStateChange', onKbState);
        leaveInput();
      },
    };
  }

  TV.register('search', search);
})();
