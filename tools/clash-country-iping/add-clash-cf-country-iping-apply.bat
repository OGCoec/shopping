@echo off
setlocal

chcp 65001 >nul

"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0add-clash-cf-country-iping.ps1" -Apply
set "exit_code=%ERRORLEVEL%"

echo.
if not "%exit_code%"=="0" (
    echo Failed with exit code %exit_code%.
) else (
    echo Done.
)

pause
exit /b %exit_code%
