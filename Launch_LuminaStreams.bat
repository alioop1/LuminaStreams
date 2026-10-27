@echo off
title LuminaStreams
echo.
echo   ========================================
echo     LuminaStreams - Starting...
echo   ========================================
echo.

set SDK=%LOCALAPPDATA%\Android\Sdk
set ADB=%SDK%\platform-tools\adb.exe
set EMULATOR=%SDK%\emulator\emulator.exe
set AVD=Television_4K
set APK=%~dp0app\release\app-release.apk

if not exist "%APK%" (
    set APK=%~dp0app\build\outputs\apk\release\app-release.apk
)
if not exist "%APK%" (
    set APK=%~dp0app\build\outputs\apk\debug\app-debug.apk
)

if not exist "%EMULATOR%" (
    echo [ERROR] Android Emulator not found!
    echo Install Android Studio from https://developer.android.com/studio
    pause
    exit /b 1
)

if not exist "%APK%" (
    echo [ERROR] APK file not found!
    echo Build the project first: .\gradlew.bat assembleRelease
    pause
    exit /b 1
)

:: Check if emulator is already running
"%ADB%" devices 2>nul | findstr /r "emulator-.*device" >nul 2>&1
if %ERRORLEVEL%==0 (
    echo   Emulator already running!
    goto :install
)

echo   Starting Android TV emulator...
start "" "%EMULATOR%" -avd %AVD% -no-boot-anim -no-snapshot-save -gpu auto

echo   Waiting for emulator to boot...
:wait_boot
timeout /t 3 /nobreak >nul
"%ADB%" shell getprop sys.boot_completed 2>nul | findstr "1" >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo   Still booting...
    goto :wait_boot
)

echo   Emulator ready!
timeout /t 3 /nobreak >nul

:install
echo   Installing LuminaStreams...
"%ADB%" install -r -t "%APK%" >nul 2>&1

echo   Launching LuminaStreams...
"%ADB%" shell am start -n com.luminastreams.tv/.MainActivity >nul 2>&1

echo.
echo   ========================================
echo     LuminaStreams is running!
echo   ========================================
echo.
echo   Press any key to exit this window...
echo   (The app will keep running)
pause >nul
