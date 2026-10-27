@echo off
title LuminaStreams Native iOS - Cloud IPA Builder
echo ===============================================================
echo   LuminaStreams Native iOS (SwiftUI) - Cloud IPA Builder
echo ===============================================================
echo.
cd /d "C:\Users\TechPC\Documents\LuminaStreams"
echo Pushing SwiftUI iOS project to GitHub...
git push origin master
if %ERRORLEVEL% neq 0 (
    echo.
    echo [!] Git push requires authentication or failed.
    echo Opening GitHub Actions page anyway...
)
echo.
echo Opening GitHub Actions page in browser...
start "" "https://github.com/alioop1/LuminaStreams/actions"
echo.
echo Done! Wait for "Build Native iOS IPA (SwiftUI)" to finish,
echo then download "LuminaStreams-Native-iOS-IPA".
pause
