@echo off
chcp 65001 >nul
title LuminaStreams iOS - Sideloading Helper
color 0B

echo ===============================================================
echo   LuminaStreams iOS - 1:1 Pixel-Perfect Native IPA
echo ===============================================================
echo.
echo הוראות התקנה לאייפון:
echo 1. ברגע שהבנייה ב-GitHub Actions מסתיימת:
echo    https://github.com/alioop1/LuminaStreams/actions
echo.
echo 2. הורד את הקובץ: LuminaStreams-Native-iOS-IPA
echo 3. חלץ את LuminaStreams.ipa אל: F:\Sideloadly\LuminaStreams.ipa
echo    (או אל C:\Users\TechPC\Documents\LuminaStreams\LuminaStreams.ipa)
echo.
echo 4. Sideloadly ייפתח כעת אוטומטית להתקנה קלה!
echo ===============================================================
echo.

if exist "F:\Sideloadly\sideloadly.exe" (
    start "" "F:\Sideloadly\sideloadly.exe"
) else (
    echo Sideloadly לא נמצא ב-F:\Sideloadly
)

pause
