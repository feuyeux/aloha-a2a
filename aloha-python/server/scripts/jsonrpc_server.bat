@echo off
cd /d %~dp0..\..
set TRANSPORT_MODE=jsonrpc
if "%JSONRPC_PORT%"=="" set "JSONRPC_PORT=13001"
call :kill_port %JSONRPC_PORT%
where uv >nul 2>&1
if %errorlevel% equ 0 (
    uv run --project server python -m server
) else if exist server\.venv\Scripts\python.exe (
    server\.venv\Scripts\python.exe -m server
) else (
    python -m server
)
goto :eof

:kill_port
set "PORT=%~1"
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /R /C:":%PORT% .*LISTENING"') do (
    if not "%%P"=="0" (
        echo [startup] Killing PID %%P on port %PORT%
        taskkill /PID %%P /F >nul 2>&1
    )
)
exit /b 0
