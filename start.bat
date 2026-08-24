@echo off
chcp 65001 >nul
echo ========================================
echo    EverNox 永夜照相馆 - 启动脚本
echo ========================================
echo.

:: 设置端口
set FRONTEND_PORT=5211
set BACKEND_PORT=11002
:: 本机局域网 IP（供同网段其他设备访问；IP 变了改这里，并同步
:: evernox-backend\config\application.yml 里的 evernox.security.allowed-origins）
set LAN_HOST=172.16.0.168

echo [1/4] 检查并关闭占用端口的进程...

call :kill_port %FRONTEND_PORT% 前端
call :kill_port %BACKEND_PORT% 后端

echo 端口清理完成!
echo.
goto :ports_done

:: ------------------------------------------------------------
::  关闭占用指定端口的进程
::  %1 = 端口号  %2 = 用途标签
::
::  注意 echo 里不要出现裸的 ")"：它会被当成 for 代码块的结束符，
::  导致后面的 taskkill 跑到循环外、拿不到 %%a（曾因此清不掉端口）。
:: ------------------------------------------------------------
:kill_port
set "PORT=%~1"
set "LABEL=%~2"
set "PORT_FOUND="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr /C:":%PORT% " ^| findstr "LISTENING"') do (
    set "PORT_FOUND=1"
    echo   正在关闭 %LABEL% 端口 %PORT% 上的进程 PID=%%a
    taskkill /F /PID %%a >nul 2>&1
    if errorlevel 1 echo   [警告] PID=%%a 结束失败，请尝试以管理员身份运行本脚本
)
if not defined PORT_FOUND (
    echo   %LABEL% 端口 %PORT% 当前空闲
    goto :eof
)
:: 端口释放有极短延迟，确认一下再往下走（用 ping 代替 timeout：
:: timeout 在标准输入被重定向时会直接报错退出）
ping -n 2 127.0.0.1 >nul
netstat -ano | findstr /C:":%PORT% " | findstr "LISTENING" >nul
if not errorlevel 1 echo   [警告] %LABEL% 端口 %PORT% 仍被占用，新进程可能启动失败
goto :eof

:ports_done

:: ============================================================
::  本地开发配置
::
::  数据库口令与图片编解码密钥统一放在
::      evernox-backend\config\application.yml
::  这里不要再用 set 设 EVERNOX_* 环境变量：环境变量优先级高于该外置配置
::  （EVERNOX_CODEC_SALT 会按松散绑定覆盖 evernox.codec.salt），
::  一旦值不对，ImageCodec 初始化就会失败，后端窗口瞬间关闭。
:: ============================================================

if not exist "%~dp0evernox-backend\config\application.yml" (
    echo ========================================
    echo  [错误] 缺少本地配置文件
    echo ========================================
    echo 未找到: evernox-backend\config\application.yml
    echo 该文件保存数据库口令与图片编解码密钥，缺失时后端无法启动。
    echo.
    pause
    exit /b 1
)

echo [2/4] 启动后端服务 (端口: %BACKEND_PORT%)...
cd /d "%~dp0evernox-backend"
:: 用 /k 而不是 /c：启动失败时窗口保留，能看到报错而不是一闪而过
start "EverNox Backend" cmd /k "mvn spring-boot:run"
echo 后端服务启动中...
echo.

:: 等待后端启动
timeout /t 5 /nobreak >nul

echo [3/4] 启动前端服务 (端口: %FRONTEND_PORT%)...
cd /d "%~dp0evernox-frontend"
start "EverNox Frontend" cmd /k "npm run dev"
echo 前端服务启动中...
echo.

echo [4/4] 启动完成!
echo ========================================
echo    本机访问:   http://localhost:%FRONTEND_PORT%
echo    局域网访问: http://%LAN_HOST%:%FRONTEND_PORT%
echo    后端地址:   http://localhost:%BACKEND_PORT%/api
echo ========================================
echo 提示: 局域网设备无法访问时，检查 Windows 防火墙是否放行入站 TCP %FRONTEND_PORT%
echo.
echo 按任意键打开浏览器访问前端页面...
pause >nul
start http://localhost:%FRONTEND_PORT%
