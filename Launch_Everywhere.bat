@echo off
title LuminaStreams - הפעלה מכל מקום (Cloudflare Tunnel)
echo.
echo =========================================================
echo    🌟 LuminaStreams - הפעלה גלובלית (גישה מכל מקום ב-4G/Wi-Fi)
echo =========================================================
echo.
cd /d "%~dp0web"
start "Lumina Local Server" /b node server.js
echo.
echo מתחבר לענן Cloudflare ליצירת כתובת אינטרנט ציבורית ומאובטחת (HTTPS)...
echo.
"C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel --url http://localhost:5000
pause
