@echo off
setlocal EnableExtensions
chcp 65001 >nul
title Cloudflare 隧道控制 - 停止

echo ============================================================
echo 正在关闭 cloudflared 隧道进程...
echo ============================================================
echo.

taskkill /f /im cloudflared.exe >nul 2>&1
if errorlevel 1 (
  echo 未找到正在运行的 cloudflared.exe 进程。
) else (
  echo 已关闭所有 cloudflared.exe 进程。
)

echo.
echo 2 秒后自动退出...
ping 127.0.0.1 -n 3 >nul
exit /b 0
