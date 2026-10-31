# LuminaStreams for LG webOS

The LG TV app (webOS 6 and newer: 2021 LG TVs onwards, including webOS 22–25). It is the shared web app
(`web/public/app`) packaged as a webOS app; on the TV it switches to TV mode
(`js/tv/tvMode.js`):

- **Look and controls of the Android TV app** — remote D-pad navigation with the
  Android TV focus ring, OK selects, **Back** closes a sheet → goes back a screen →
  leaves the app from Home.
- **The Android TV player** (`js/player/androidPlayer.js`, the same module the Windows
  app uses) drawn over the TV's own `<video>` (`js/player/tvBackend.js`). The stream
  goes straight into the TV's media pipeline, so the TV itself decodes Dolby Vision,
  HDR10/HDR10+, HEVC/AV1 and Dolby Atmos / DTS / TrueHD — and shows its own native
  **Dolby Vision / Dolby Atmos** pop-ups. The app draws no format badges of its own.
- Sources are ranked like the Android app (lossless/Atmos audio is not penalised, as
  it is in a desktop browser).
- Same account, favourites and watch progress as every other platform.
- Older TVs (webOS 6 / 2021, Chromium 79): `build.js` lowers the JavaScript to ES2019
  with TypeScript, expands the CSS `inset` shorthand, adds `legacy.css` fallbacks and
  allows `file:` in the page's security policy; `js/tv/flexGap.js` restores flex `gap`.

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

Open the web app with `?tv=1` (e.g. `http://localhost:5000/app/?tv=1`) to get TV mode
in a browser: arrow keys = D-pad, Enter = OK, Escape = Back. A desktop browser can't
decode most release formats; real playback needs the TV.
