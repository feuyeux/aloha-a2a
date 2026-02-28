@echo off
cd /d %~dp0..\..
set TRANSPORT_MODE=grpc
where uv >nul 2>&1
if %errorlevel% equ 0 (
    uv run python -m server 2>nul || python -m server
) else (
    python -m server
)
