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
echo If the TV is not listed above, or its IP address changed, run:
echo     ares-setup-device -m LuminaTV -i "host=TV_IP_ADDRESS"
echo     ares-novacom --device LuminaTV --getkey
echo (the passphrase is shown in the TV's Developer Mode app)
echo.

node webos\build.js --launch
if errorlevel 1 (
    echo.
    echo Install failed - check that the TV is on and Developer Mode is running.
    pause
    exit /b 1
)

echo.
echo Done. LuminaStreams is in the TV's app list.
echo Developer Mode expires after 50 hours unless you press "Extend" in the
echo Developer Mode app (installed apps are removed when it expires).
pause
