# LuminaStreams — iOS & Web parity tracker

**Reference:** the Android TV app (`app/`). iOS (`ios-native/`) and Web (`web/public/app/`) must match it
feature-for-feature and share the same Firebase accounts.

Legend: ✅ equivalent · ⚠️ partial · ❌ missing · `[ ]` open task · `[x]` done
Paths: **A** = `app/src/main/java/com/luminastreams/tv/`, **I** = `ios-native/LuminaStreams/`, **W** = `web/public/app/js/`

Audit date: 2026-10-30.

---

## 0. Blockers — nothing "works" until these are fixed

### Shared (data contract)
- [ ] **Web sync wipes Android's watchlist.** Web pushes `favorites` as numeric ids (`W/core/sync.js:63`); Android keeps only
      JSON strings and calls `replaceWatchlist(empty)` (`A/data/sync/SyncManager.kt:298-301`). Web must write Android's schema.
- [ ] **One canonical Firestore schema** (Android's), used by all three apps — `users/{uid}`:
      - `favorites`: array of JSON **strings** `{id:"movie_123"|"tv_123", title, backdropUrl, posterUrl, overview, rating, year, genre, mediaType}` (overwritten whole)
      - `progress`: map key → JSON **string** `{p, d, t[, tmdb]}` in **milliseconds**, `""` = deleted (written per field)
      - `_exists: true`
- [ ] **One canonical progress key:** `movie_<imdb>` / `ep_<imdb>_s<S>e<E>`. iOS uses `_s<S>_e<E>` and seconds (`I/Core/WatchProgressManager.swift:62`); web uses seconds and other field names.
- [ ] **firestore.rules:** `device_links` is `allow read, write: if true` and the web landing page writes the **plaintext password** there (`web/public/app.js:269-273`). Lock down rules; stop storing passwords.
- [ ] **AllDebrid v4.1:** `magnet/status` no longer returns files → must call `/v4/magnet/files` (per AllDebrid changelog 16/10/2024; verify with a real token). Affects all three apps.

### Web
- [ ] **No torrent source can play:** `W/screens/details.js:315-317` calls `AllDebrid.isConfigured()` / `unlockMagnet()`, which don't exist.
- [ ] Back button throws: `Router.canGoBack()` not exported (`W/screens/details.js:476`).
- [ ] hls.js never loaded in the IPTV screen (checks `window.Hls` before `loadHls()`); unpinned `hls.js@latest` in player and IPTV.
- [ ] Wizdom Hebrew `.srt` files are windows-1255 → decode fallback needed (`W/screens/player.js:146`).

### iOS
- [ ] **Most debrid files won't play:** plain AVPlayer rejects MKV/AVI/WebM, DTS, TrueHD, embedded subs. Add **KSPlayer** (or MPVKit) via SPM in `project.yml`.
- [ ] `NSAppTransportSecurity` missing from `project.yml` `info.properties` → XcodeGen drops it → http IPTV/streams blocked.
- [ ] `AVAudioSession` never set to `.playback` → silent with ring switch on, no background audio / PiP.
- [ ] No Firebase at all → no login, no sync.

---

## 1. Accounts, sync & settings

| Android | iOS | Web |
|---|---|---|
| Firebase email/password login + register (`A/data/repository/FirebaseAuthRepository.kt`) | ❌ | ✅ (+ password reset) |
| Logout | ❌ | ✅ |
| Initial pull + realtime snapshot listener, 500 ms debounce (`SyncManager.kt:265-319`) | ❌ | ⚠️ pushes **before** pulling → overwrites cloud on first login |
| Push on watchlist toggle, progress tick, mark watched, tombstones | ❌ | ⚠️ progress only; no watchlist push; no tombstones |
| Cloud-sync card with status + 15 s timeout | ❌ | ⚠️ button, no status |
| Navigation: Search, Home, Movies, Series, Fuzer, IPTV, Debrid cloud, Watchlist, Settings, Profile | ⚠️ no Profile | ⚠️ no Debrid screen; mobile nav lacks Fuzer/IPTV/Account |
| RTL from `app_lang` | ✅ (`tr` arg order reversed vs Android/Web) | ✅ (nav titles hard-coded Hebrew) |
| Screensaver after 5 min idle | ❌ | ❌ |

**Settings** (`A/data/repository/SettingsRepository.kt:24-65`) — none sync to Firestore on any platform.

| Key (default) | iOS | Web |
|---|---|---|
| `app_lang` (he) | ✅ | ✅ |
| `ad_api_token` + PIN login, account status | ⚠️ no status | ⚠️ |
| `preferred_audio_lang` (original) | ⚠️ no UI, unused | ⚠️ default he, unused |
| `def_subs` (Hebrew/English/None) | ⚠️ no UI | ⚠️ "none" ignored |
| `subtitle_font_scale` (small…xlarge) | ⚠️ no xlarge | ⚠️ not applied in player |
| `yellow_subs` (false) | ✅ | ⚠️ not applied |
| `max_resolution`, `max_file_size` | ⚠️ no UI, unused | ❌ |
| `save_history`, clear search history | ❌ | ⚠️ |
| clear image cache / size | ❌ | ⚠️ wipes all `lumina_*` keys |
| server status, about/version | ❌ | ⚠️ version only |

Web bugs: `fullSync` runs twice on login; "clear progress" is local only; `reduce_motion` not applied at start.
iOS bugs: progress saved only on player dismiss; `try?` decode silently drops watchlist on model change.

**iOS Firebase plan:** Firebase iOS SDK via SPM (`FirebaseAuth`, `FirebaseFirestore`). Needs an iOS app registered in
project `luminastreams-1a474` for bundle `com.luminastreams.ios` → `GoogleService-Info.plist`.

---

## 2. Browsing (Home, Details, Search, Watchlist)

| Android | iOS | Web |
|---|---|---|
| Home rows: Continue Watching, Trending Movies, HBO, Netflix, Popular Shows, Amazon, Apple, Disney, Paramount, Hulu, Sony, New in Theaters; studio rows merge movie+TV by rating; de-duped (`A/presentation/home/HomeViewModel.kt:474-555`) | ❌ different rows | ❌ different rows |
| TMDB language: rows `en-US`, details/hero by app language | ⚠️ | ⚠️ `he-IL` hard-coded |
| Movies/Series tabs: studio ribbon + fixed rows | ⚠️ | ⚠️ genre rows |
| Studio filter, US region, 7 categories (ids at `HomeViewModel.kt:140-149`) | ❌ **broken**: lowercase ids vs `"HBO"`/`"APPLE_TV"` switch (`I/Views/HomeView.swift:281` vs `I/Services/TmdbService.swift:97`) | ⚠️ 3 identical rows, IL region, no Sony |
| Row pagination (6 from end, 50 pages) | ❌ | ⚠️ Movies/Series only, 5 pages |
| Continue Watching: one per show, newest first, max 20, refresh on resume | ⚠️ one per episode (duplicate ids), refresh only at launch | ❌ |
| Hero: logo, runtime, cast, genres; muted YouTube trailer after 3.5 s | ❌ static carousel | ⚠️ no trailer |
| Details: logo, OMDb IMDb rating, year, runtime, age, genres | ✅ | ⚠️ no OMDb |
| Watch button "S{x}E{y}" / "CONTINUE" from last episode | ❌ always S{n}E1 | ⚠️ never matches (id format) |
| Trailer via Cinemeta / `include_video_language=he,en,null` | ⚠️ TMDB he-IL only | ⚠️ TMDB he-IL only |
| Episode titles fallback he→en→Cinemeta; progress bars; watched badge | ⚠️ | ⚠️ |
| Mark watched/unwatched (episode, season, movie) | ❌ | ❌ |
| Unaired "AIRS Mar 14" pill; NextEpisodeStatusCard (5 states) | ⚠️ raw date | ❌ |
| Cast + actor popup | ✅ | ⚠️ no popup |
| Search: `search/multi` 3 pages, bilingual for Hebrew queries, + Fuzer | ⚠️ 1 page | ⚠️ no Fuzer |
| Empty search → discovery grid | ❌ | ⚠️ |
| Filter sidebar (type, genre, sort, rating, era, runtime, language, platform, presets) | ❌ | ⚠️ partial, mostly ignored with a query |
| Search history (8) + suggestions | ❌ | ⚠️ history only |
| Watchlist screen: newest first, remove | ⚠️ no remove | ⚠️ remove broken (string vs number id) |
| Splash (logo + progress, 3.5 s) | ❌ | ⚠️ spinner |

iOS bugs: Details doesn't reload when navigating to a recommendation (`.task` with no id, `I/Views/DetailsView.swift:81`); Hebrew strings hard-coded.
Web bugs: studio rows ignore sort; `yearMin` used as exact year; missing `assets/img/*-placeholder.png`; page-wide arrow-key handler on Home.

---

## 3. Stream sources (Torrentio, AllDebrid, Debrid Cloud, Fuzer)

| Android | iOS | Web |
|---|---|---|
| Torrentio `/alldebrid=<token>/stream/...` | ✅ | ✅ (no timeout) |
| tmdb→imdb via `/external_ids` before scraping | ❌ | ⚠️ |
| Parser: size, seeders, leechers, tracker, `AD+` = cached | ✅ | ⚠️ no tracker/leechers; every debrid stream "cached" |
| Audio-language detection (15 langs, word boundaries) | ⚠️ 6 langs, false positives | ⚠️ 3 langs |
| Default sort: size → seeders → leechers | ⚠️ different | ⚠️ different (browser-audio bias, keep for web) |
| Pre-filters: audio language, `max_resolution`, `max_file_size` | ❌ | ⚠️ |
| 10-min stream cache + Refresh | ❌ | ❌ |
| DMCA probe (top 5, ≤20 MB = placeholder) | ❌ | ❌ |
| Filters: tracker, language, cached, DV, quality (multi-select) | ⚠️ | ❌ |
| Source row: tracker badge, HDR/DV, ATMOS/7.1/5.1, size, ▲▼ | ⚠️ | ⚠️ |
| Play direct URL: Range probe, expired/DMCA detection, remove dead stream | ⚠️ | ❌ |
| Play infoHash: upload → poll → unlock, 25 s timeout, conflict retry, cleanup | ⚠️ | ⚠️ |
| AD API v4.1 status, POST form for upload/unlock, `magnet/delete` | ⚠️ v4, GET | ✅ (no delete) |
| PIN auth 120×5 s, tolerant of errors | ⚠️ 60×4 s, stops on error | ⚠️ |
| Torrent-file upload (multipart) with progress | ⚠️ | ❌ |
| Delete session torrents on exit | ❌ | ❌ |
| Debrid Cloud screen (list, TMDB posters, %, files, play, delete) | ⚠️ | ❌ |
| Fuzer: vBulletin MD5 login, windows-1255 search, browse, parse, re-login | ⚠️ UTF-8 search, no re-login | ❌ fake login, play returns null |

**Web CORS (tested):** Torrentio ✅ and AllDebrid ✅ work from the browser. **Fuzer needs a server proxy.**

---

## 4. Player & subtitles

| Android | iOS | Web |
|---|---|---|
| Subtitle sources: Wizdom, Ktuvit-proxy, OpenSubtitles v3, OS-Community, SubDL, Ktuvit-Direct | ⚠️ OS v3 + SubDL only | ⚠️ no SubDL/Ktuvit-Direct |
| Picker listing every file + source | ❌ | ✅ |
| Auto-select by preference (Hebrew/English/None) | ⚠️ | ⚠️ |
| Embedded subtitle track picker + Off | ❌ | ❌ |
| Audio track picker (language/channels/codec/Atmos) + preferred language | ❌ | ⚠️ hls.js only |
| Subtitle offset ±100/250/500/1000 ms, ±15 s, saved per title | ⚠️ not saved | ❌ |
| Subtitle style: yellow/white, 4 sizes, shadow | ⚠️ | ❌ |
| Resume dialog (>30 s) | ⚠️ silent, seeks before ready | ⚠️ toast |
| Save progress every 5 s, done at ≥95 %, push to cloud | ❌ dismiss only | ✅ |
| Next-episode card at T-45 s, 10 s countdown, autoplay | ❌ | ❌ |
| Speed 0.75–2x | ❌ | ✅ |
| Aspect modes | ❌ | ❌ |
| ±10 s seek, +85 s skip intro | ⚠️ | ⚠️ |
| Error overlay, auto-reconnect, stall watchdog | ❌ black screen | ⚠️ |
| Loading overlay, resolution/DV/Atmos badges | ❌ | ❌ |

**Formats:** Android = Media3 + FFmpeg audio (MKV, AC3/EAC3/DTS/TrueHD, embedded SRT/ASS/PGS).
iOS → add KSPlayer/MPVKit. Web → prefer browser-safe streams (`isWebAudio`), pin hls.js; full coverage would need server-side remux.

Bugs: iOS loads a random language when Hebrew is missing; SubDL returns 403 without `api_key` (Android too);
web series resume picks another episode's position; web progress key changes mid-session; web false "AC3 unsupported" banner on every stream.
CORS: OpenSubtitles, Wizdom, Ktuvit-proxy ✅. `opensubtitles.stremio.homes` is dead (404). Ktuvit-Direct impossible in the browser.

---

## 5. IPTV

| Android | iOS | Web |
|---|---|---|
| M3U parser: any quoting, BOM, `#EXTGRP`, adult filter, stable ids (tests in `app/src/test/.../iptv/`) | ⚠️ unstable `hashValue` ids | ⚠️ index ids |
| Playlists: add/edit/delete, name + M3U + EPG | ⚠️ URL only, reloads every visit | ⚠️ last URL only |
| Groups + Favorites | ⚠️ no favorites | ✅ |
| Recently watched (10) | ❌ | ✅ |
| XMLTV EPG (gzip, encodings, 7-day lookback, weekly refresh) | ❌ | ❌ |
| Now/next hero, guide grid, in-player 7-day guide + catch-up | ❌ | ❌ |
| Player: HLS vs TS detection, behind-live-window recovery, retry | ⚠️ | ⚠️ |
| Channel zap, OSD (logo, number, EPG line, clock) | ❌ | ⚠️ |
| Aspect / audio / subtitle options | ❌ | ❌ |
| QR → phone form (name, M3U, EPG) | → native form instead | → web form instead |

Web: http playlists/EPG/streams are blocked on an https page (mixed content + no CORS); raw MPEG-TS needs mpegts.js → **needs a streaming proxy**.
iOS: AVPlayer plays HLS only (raw `.ts` needs KSPlayer or `.m3u8` rewrite); live seek slider broken (`duration` stays 1).

---

## 6. Security notes (outside parity, but real)
- `app/keystore.jks` and `app/google-services.json` are committed.
- AllDebrid token and Fuzer username/password are hard-coded in iOS `Constants.swift` and web JS (public).
- Android ships `setAppVerificationDisabledForTesting(true)` (`FirebaseAuthRepository.kt:15`).

---

## Build order

1. **Shared data contract** — Firestore schema, progress keys, rules, AllDebrid v4.1 files (+ the Android sync guard).
2. **Web: make it play** — Phase 0 web bugs, sync rewrite, progress keys, subtitle encoding.
3. **iOS: make it play** — KSPlayer, ATS, audio session, AllDebrid fixes, progress every 5 s, error handling.
4. **iOS: accounts** — Firebase SDK, login/logout, sync identical to Android.
5. **Feature parity** — Browse → Streams → Player → IPTV, per sections 2–5, both platforms.
6. **Web proxy** — Fuzer, IPTV (http/TS), Ktuvit-Direct.
7. **Verify** — CI IPA build on each push; browser playback tests on Chrome/Safari/mobile.
