@echo off
cd /d %~dp0..

echo Building JavaScript server...
cd server
call npm install --silent
call npm run build

echo Building JavaScript client...
cd ..\client
call npm install --silent
call npm run build

echo JavaScript build completed successfully!
