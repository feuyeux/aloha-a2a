@echo off
cd /d %~dp0..
set "JSONRPC_PORT=%JSONRPC_PORT%"
set "REST_PORT=%REST_PORT%"
if "%JSONRPC_PORT%"=="" set "JSONRPC_PORT=15001"
if "%REST_PORT%"=="" set "REST_PORT=15002"
call :kill_port %JSONRPC_PORT%
call :kill_port %REST_PORT%
dotnet run
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
