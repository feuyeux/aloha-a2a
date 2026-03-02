@echo off
cd /d %~dp0..\..
where uv >nul 2>&1
if %errorlevel% equ 0 (
    uv run --project client python -m client --transport rest --port 13002 --message "Roll a 6-sided dice"
) else if exist client\.venv\Scripts\python.exe (
    client\.venv\Scripts\python.exe -m client --transport rest --port 13002 --message "Roll a 6-sided dice"
) else (
    python -m client --transport rest --port 13002 --message "Roll a 6-sided dice"
)
