# LuminaStreams for LG webOS

The LG TV app (webOS 6 and newer: 2021 LG TVs onwards). It is its **own app, built for
the TV** (`webos/app`), not the website: every screen is rebuilt from the Android TV
Compose code and laid out in the same units (960×540 dp, two pixels per dp on a 1080p
TV; Details at Android's 0.85 density).

- `app/js/core.js` — dp scaling, screen stack (screens stay alive underneath, like
  Android's back stack), remote keys (LG Back 461, media keys), Magic Remote pointer,
  transform-only row scrolling, TMDB queries identical to Android's.
- `app/js/home.js` — top bar with the Home / Movies / Series / Fuzer tabs, hero,
  category list + one row at a time (Android's ConsoleRowCycler), studio ribbon/filter.
- `app/js/details.js` — Details, the sources dialog (filters, DetailedSourceRow) and
  the season popup.
- `app/js/screens.js` — Search (LG on-screen keyboard), My List, Settings, Sign-in.
- `app/js/playback.js` — the shared Android player (`web/.../js/player/androidPlayer.js`,
  same as Windows) over the TV's own `<video>` (`tvBackend.js`): the TV decodes
  Dolby Vision / HDR / Atmos itself and shows its own native pop-ups.
- Shared with the website and Windows (copied in at build time): TMDB/Torrentio/
  AllDebrid/subtitles clients, Firebase sign-in + sync, watch progress, the player.

Built for the TV's engine (Chromium 79 on 2021 models): animations on transform and
opacity only, no backdrop blur, nothing animating off-screen, the rows beside the
current one pre-drawn, the 4K backdrop decoded before it is shown.

## Install (Developer Mode)

On the TV:

1. LG Content Store → install **Developer Mode**, open it and sign in with your LG
   developer account (free at webostv.developer.lge.com).
2. Turn **Dev Mode Status** on (the TV restarts), then turn **Key Server** on.
   Note the IP address and passphrase it shows.

On the PC (once):

```bash
npm install -g @webos-tools/cli
ares-setup-device -a LuminaTV -i "host=<TV IP>" -i "port=9922" -i "username=prisoner"
ares-novacom --device LuminaTV --getkey
```

Then double-click **`Install_on_LG_TV.bat`** in the repo root, or:

```bash
node webos/build.js --launch
```

The script installs on **every** TV registered with `ares-setup-device` (`--all-tvs`);
for one TV: `node webos/build.js --launch --device LuminaTV2`.
`node webos/build.js` alone only builds `webos/dist/com.luminastreams.tv_<version>_all.ipk`.

The LG CLI calls Node helpers that Node 23+ removed; `build.js` preloads
`node-compat.js` into it. When running `ares-*` commands yourself on Node 23+, set
`NODE_OPTIONS=--require <repo>/webos/node-compat.js` first.

Developer Mode lasts 50 hours; press **Extend** in the Developer Mode app before it
runs out, or the TV removes developer apps and you reinstall with the script.

## Testing on a PC

`node webos/build.js --no-package` stages the app in `webos/build`; serve that folder
(e.g. `python -m http.server 5100 --directory webos/build`) and open it at 1280×720 or
1920×1080: arrow keys = D-pad, Enter = OK, Escape = Back. A desktop browser can't
decode most release formats; real playback needs the TV.
