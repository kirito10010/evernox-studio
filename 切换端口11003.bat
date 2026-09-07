@echo off
setlocal EnableDelayedExpansion

REM ============ Config (edit if needed) ============
set "ROOT_DIR=C:\Users\Administrator\Desktop\evernox-studio"
set "NGINX_DIR=%ROOT_DIR%\nginx-1.30.4"
set "OLD_PORT=11002"
set "NEW_PORT=11003"
set "JAVA_OPTS=-Xms256m -Xmx512m"
REM =================================================

set "NEW_JAR=%ROOT_DIR%\evernox-backend-%NEW_PORT%.jar"

echo.
echo ===== Switch backend port: %OLD_PORT% to %NEW_PORT% =====
echo.

echo [1/5] Starting new instance on port %NEW_PORT% ...
start "evernox-%NEW_PORT%" /D "%ROOT_DIR%" java %JAVA_OPTS% -jar "%NEW_JAR%" --server.port=%NEW_PORT%

echo [2/5] Waiting for new instance to be ready ...
set /a try=0
:wait_loop
curl -s -o nul http://127.0.0.1:%NEW_PORT%/api/image/public
if !errorlevel!==0 goto ready
set /a try+=1
if !try! geq 20 goto warn_timeout
timeout /t 3 /nobreak >nul
goto wait_loop

:warn_timeout
echo WARN: new instance not ready after ~60s, switching anyway...
:ready
echo New instance is ready.

echo [3/5] Pointing nginx to port %NEW_PORT% ...
powershell -NoProfile -Command "$p='%NGINX_DIR%\conf\nginx.conf'; $c=[System.IO.File]::ReadAllText($p); $c=$c.Replace('127.0.0.1:%OLD_PORT%','127.0.0.1:%NEW_PORT%'); [System.IO.File]::WriteAllText($p,$c)"

echo [4/5] Reloading nginx ...
cd /d "%NGINX_DIR%"
nginx -s reload

echo [5/5] Stopping old instance on port %OLD_PORT% ...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%OLD_PORT%" ^| findstr "LISTENING"') do (
  taskkill /F /PID %%a >nul 2>&1
)

echo.
echo Done. Backend is now serving on port %NEW_PORT%.
echo.
pause
