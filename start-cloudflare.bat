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

set "CF_BIN=cloudflared"

where cloudflared >nul 2>&1
if errorlevel 1 (
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

echo ============================================================
echo 正在启动 Cloudflare 隧道...
echo 隧道 ID：%TUNNEL_ID%
echo 来源：%TUNNEL_ID_SOURCE%
echo 访问域名：niko000o.site
echo 按 Ctrl + C 可以停止隧道。
echo ============================================================
echo.

"%CF_BIN%" tunnel run %TUNNEL_ID%
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if "%EXIT_CODE%"=="0" (
  echo 隧道已停止。
) else (
  echo 隧道异常退出，返回码：%EXIT_CODE%
)
pause
exit /b %EXIT_CODE%
