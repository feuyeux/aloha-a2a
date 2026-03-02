@echo off
cd /d %~dp0..\..
set TRANSPORT_MODE=grpc
if "%GRPC_PORT%"=="" set "GRPC_PORT=13000"
if "%REST_PORT%"=="" set "REST_PORT=13002"
call :kill_port %GRPC_PORT%
call :kill_port %REST_PORT%
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
