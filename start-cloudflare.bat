@echo off
setlocal EnableExtensions
chcp 65001 >nul
title Cloudflare 隧道控制 - 启动

set "TUNNEL_ID=%CF_TUNNEL_ID%"
set "TUNNEL_ID_SOURCE=环境变量 CF_TUNNEL_ID"
if "%TUNNEL_ID%"=="" (
  echo ============================================================
  echo 错误：未设置环境变量 CF_TUNNEL_ID。
  echo 请先设置该环境变量后再运行本脚本。
  echo ============================================================
  pause
  exit /b 1
)

set "CF_BIN="
set "CONFIG_FILE=%USERPROFILE%\.cloudflared\config.yml"

for /f "delims=" %%I in ('where cloudflared 2^>nul') do (
  set "CF_BIN=%%~fI"
  goto CF_BIN_FOUND
)

if "%CF_BIN%"=="" (
  if exist "%ProgramFiles%\cloudflared\cloudflared.exe" (
    set "CF_BIN=%ProgramFiles%\cloudflared\cloudflared.exe"
  ) else if exist "%ProgramFiles(x86)%\cloudflared\cloudflared.exe" (
    set "CF_BIN=%ProgramFiles(x86)%\cloudflared\cloudflared.exe"
  ) else (
    echo ============================================================
    echo 错误：未找到 cloudflared。
    echo 请先安装 cloudflared，或将 cloudflared.exe 加入 PATH。
    echo ============================================================
    pause
    exit /b 1
  )
)
:CF_BIN_FOUND

if not exist "%CONFIG_FILE%" (
  echo ============================================================
  echo 错误：未找到 Cloudflare 配置文件：
  echo %CONFIG_FILE%
  echo ============================================================
  pause
  exit /b 1
)

:INPUT_PROTOCOL
set "USER_PROTOCOL="
set /p USER_PROTOCOL=请输入协议（http/https）:
if /I "%USER_PROTOCOL%"=="http" (
  set "USER_PROTOCOL=http"
) else if /I "%USER_PROTOCOL%"=="https" (
  set "USER_PROTOCOL=https"
) else (
  echo 协议无效，请输入 http 或 https。
  goto INPUT_PROTOCOL
)

:INPUT_PORT
set "USER_PORT="
set /p USER_PORT=请输入端口（1-65535）:
if "%USER_PORT%"=="" (
  echo 端口不能为空，请重新输入。
  goto INPUT_PORT
)
echo(%USER_PORT%| findstr /r "^[0-9][0-9]*$" >nul
if errorlevel 1 (
  echo 端口必须是数字，请重新输入。
  goto INPUT_PORT
)
set /a PORT_NUM=%USER_PORT%
if %PORT_NUM% LSS 1 (
  echo 端口范围必须在 1-65535，请重新输入。
  goto INPUT_PORT
)
if %PORT_NUM% GTR 65535 (
  echo 端口范围必须在 1-65535，请重新输入。
  goto INPUT_PORT
)

set "LOCAL_SERVICE=%USER_PROTOCOL%://localhost:%PORT_NUM%"
echo.
echo 正在更新 Cloudflare 配置：%LOCAL_SERVICE%
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0update-cloudflare-config.ps1" -ConfigPath "%CONFIG_FILE%" -Protocol "%USER_PROTOCOL%" -Port %PORT_NUM%
set "UPDATE_EXIT_CODE=%ERRORLEVEL%"
if not "%UPDATE_EXIT_CODE%"=="0" (
  echo 配置更新失败，返回码：%UPDATE_EXIT_CODE%
  pause
  exit /b %UPDATE_EXIT_CODE%
)

echo ============================================================
echo 正在启动 Cloudflare 隧道...
echo 隧道 ID：%TUNNEL_ID%
echo 来源：%TUNNEL_ID_SOURCE%
echo 回源地址：%LOCAL_SERVICE%
echo 访问域名：niko000o.site
echo 按 Ctrl + C 可以停止隧道。
echo ============================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0cloudflare-ip-guard.ps1" -CloudflaredPath "%CF_BIN%" -TunnelId "%TUNNEL_ID%"
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if "%EXIT_CODE%"=="0" (
  echo 隧道已停止。
) else if "%EXIT_CODE%"=="100" (
  echo 检测到公网 IP 变化，已拒绝连接并终止隧道进程。
) else (
  echo 隧道异常退出，返回码：%EXIT_CODE%
)
pause
exit /b %EXIT_CODE%
