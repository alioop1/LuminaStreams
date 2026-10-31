@echo off
chcp 65001 >nul
title LuminaStreams - Install on LG TV (webOS)
color 0B
cd /d "%~dp0"

echo ===============================================================
echo   LuminaStreams for LG webOS - build and install
echo ===============================================================
echo.
echo Before you start (on the TV):
echo   1. Open the "Developer Mode" app, sign in with your LG account.
echo   2. Dev Mode Status = ON, Key Server = ON.
echo   3. The TV must be on and on the same network as this PC.
echo.

where ares-install >nul 2>nul
if errorlevel 1 (
    echo The LG webOS CLI is missing. Install it once with:
    echo     npm install -g @webos-tools/cli
    pause
    exit /b 1
)

call ares-setup-device -l
echo.
echo Installing on every TV listed above (LuminaTV, LuminaTV2, ...).
echo If a TV is missing, or its IP address changed, run:
echo     ares-setup-device -a NAME -i "host=TV_IP_ADDRESS" -i "port=9922" -i "username=prisoner"
echo     ares-novacom --device NAME --getkey
echo (the passphrase is shown in that TV's Developer Mode app)
echo.

node webos\build.js --launch --all-tvs
if errorlevel 1 (
    echo.
    echo Some TVs failed - check that they are on and Developer Mode is running.
    pause
    exit /b 1
)

echo.
echo Done. LuminaStreams is in each TV's app list.
echo Developer Mode expires after 50 hours unless you press "Extend" in the
echo Developer Mode app (installed apps are removed when it expires).
pause
