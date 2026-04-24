@echo off
setlocal EnableExtensions
chcp 65001 >nul
title cpolar tunnel - stop

echo ============================================================
echo Stopping cpolar processes...
echo ============================================================
echo.

taskkill /f /im cpolar.exe >nul 2>&1
if errorlevel 1 (
  echo No running cpolar.exe process found.
) else (
  echo All cpolar.exe processes stopped.
)

echo.
echo Done.
exit /b 0
