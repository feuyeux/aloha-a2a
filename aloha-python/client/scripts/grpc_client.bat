@echo off
cd /d %~dp0..\..
where uv >nul 2>&1
if %errorlevel% equ 0 (
    uv run python -m client --transport grpc --port 13000 --message "Roll a 6-sided dice" 2>nul || python -m client --transport grpc --port 13000 --message "Roll a 6-sided dice"
) else (
    python -m client --transport grpc --port 13000 --message "Roll a 6-sided dice"
)
