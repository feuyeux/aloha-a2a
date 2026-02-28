@echo off
cd /d %~dp0..
node dist/index.js --transport grpc --host localhost --port 14000 --message "Roll a 6-sided dice"
