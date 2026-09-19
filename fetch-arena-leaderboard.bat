@echo off
REM ============================================================
REM  Fetch the Code Arena leaderboard pages and pack them into
REM  one zip file, ready to be imported from the web page.
REM
REM  Run this on a machine that can open https://arena.ai
REM  in a browser (the cloud server is blocked with HTTP 403).
REM ============================================================
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0fetch-arena-leaderboard.ps1"
echo.
pause
