@echo off
setlocal EnableExtensions
chcp 65001 >nul
title cpolar tunnel - start

set "CPOLAR_BIN=cpolar"
set "TARGET_URL=https://localhost:6655"
set "PROTO=https"

where cpolar >nul 2>&1
if errorlevel 1 (
  if exist "%ProgramFiles%\cpolar\cpolar.exe" (
    set "CPOLAR_BIN=%ProgramFiles%\cpolar\cpolar.exe"
  ) else if exist "%ProgramFiles(x86)%\cpolar\cpolar.exe" (
    set "CPOLAR_BIN=%ProgramFiles(x86)%\cpolar\cpolar.exe"
  ) else (
    echo ============================================================
    echo ERROR: cpolar not found.
    echo Install cpolar or add cpolar.exe to PATH.
    echo ============================================================
    pause
    exit /b 1
  )
)

echo ============================================================
echo Starting cpolar tunnel...
echo Command:
echo "%CPOLAR_BIN%" http -proto %PROTO% %TARGET_URL%
echo Press Ctrl+C to stop.
echo ============================================================
echo.

if /I "%CPOLAR_DRY_RUN%"=="1" (
  echo Dry run mode enabled. Exit without starting tunnel.
  exit /b 0
)
if /I "%~1"=="--dry-run" (
  echo Dry run mode enabled. Exit without starting tunnel.
  exit /b 0
)

"%CPOLAR_BIN%" http -proto %PROTO% %TARGET_URL%
set "EXIT_CODE=%ERRORLEVEL%"

echo.
echo cpolar exited with code %EXIT_CODE%.
pause
exit /b %EXIT_CODE%
