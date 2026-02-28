@echo off
cd /d %~dp0..

echo Building Python server...
cd server
where uv >nul 2>&1
if %errorlevel% equ 0 (
    uv venv --quiet 2>nul
    uv pip install -e . --quiet
) else (
    pip install -e . --quiet
)

echo Building Python client...
cd ..\client
where uv >nul 2>&1
if %errorlevel% equ 0 (
    uv venv --quiet 2>nul
    uv pip install -e . --quiet
) else (
    pip install -e . --quiet
)

echo Python build completed successfully!
