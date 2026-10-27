@echo off
title LuminaStreams Web Portal
echo.
echo ===================================================
echo    🌟 LuminaStreams - הפעלת שער הכניסה ב-WEB
echo ===================================================
echo.
echo מתחיל שרת מקומי ופותח את הדפדפן...
echo.

cd /d "%~dp0web"
node server.js
pause
