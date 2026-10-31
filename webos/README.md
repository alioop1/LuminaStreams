# LuminaStreams for LG webOS

The LG TV app (webOS 23 / 24 / 25, LG OLED 2023+). It is the shared web app
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

`node webos/build.js` alone only builds `webos/dist/com.luminastreams.tv_<version>_all.ipk`.

Developer Mode lasts 50 hours; press **Extend** in the Developer Mode app before it
runs out, or the TV removes developer apps and you reinstall with the script.

## Testing on a PC

Open the web app with `?tv=1` (e.g. `http://localhost:5000/app/?tv=1`) to get TV mode
in a browser: arrow keys = D-pad, Enter = OK, Escape = Back. A desktop browser can't
decode most release formats; real playback needs the TV.
