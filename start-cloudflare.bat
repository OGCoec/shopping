@echo off
setlocal EnableExtensions

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-cloudflare.ps1"
set "EXIT_CODE=%ERRORLEVEL%"

exit /b %EXIT_CODE%
