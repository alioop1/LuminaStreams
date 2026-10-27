@echo off
setlocal enabledelayedexpansion
title LuminaStreams - Sideload IPA to iPhone via EmLoad

echo ========================================================
echo    LuminaStreams - Install IPA to iPhone via EmLoad
echo ========================================================
echo.

set "EMLOAD_DIR=F:\EmLoad"
set "EMLOAD_BAT=%EMLOAD_DIR%\emload.bat"

if not exist "%EMLOAD_BAT%" (
    echo [ERROR] EmLoad not found at %EMLOAD_DIR%
    pause
    exit /b 1
)

set "IPA_PATH=%~1"

if "%IPA_PATH%"=="" (
    if exist "%USERPROFILE%\Downloads\LuminaStreams.ipa" (
        set "IPA_PATH=%USERPROFILE%\Downloads\LuminaStreams.ipa"
    ) else if exist "%USERPROFILE%\Downloads\LuminaStreams-Native-iOS-IPA\LuminaStreams.ipa" (
        set "IPA_PATH=%USERPROFILE%\Downloads\LuminaStreams-Native-iOS-IPA\LuminaStreams.ipa"
    ) else if exist "%~dp0LuminaStreams.ipa" (
        set "IPA_PATH=%~dp0LuminaStreams.ipa"
    ) else if exist "%EMLOAD_DIR%\LuminaStreams.ipa" (
        set "IPA_PATH=%EMLOAD_DIR%\LuminaStreams.ipa"
    )
)

if "%IPA_PATH%"=="" (
    echo Drag and drop LuminaStreams.ipa onto this file, or place it in Downloads.
    echo.
    set /p "IPA_PATH=Or paste the full path to LuminaStreams.ipa here: "
)

set IPA_PATH=%IPA_PATH:"=%

if not exist "%IPA_PATH%" (
    echo [ERROR] File not found: "%IPA_PATH%"
    pause
    exit /b 1
)

echo.
echo Found IPA: "%IPA_PATH%"
echo Starting signing and installation to connected iPhone...
echo.

call "%EMLOAD_BAT%" sideload "%IPA_PATH%"

echo.
echo Process finished.
pause
