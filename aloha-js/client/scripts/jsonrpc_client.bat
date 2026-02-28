@echo off
cd /d %~dp0..
node dist/index.js --transport jsonrpc --host localhost --port 14001 --message "Roll a 6-sided dice"
