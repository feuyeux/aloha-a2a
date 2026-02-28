@echo off
cd /d %~dp0..
dotnet run -- --transport jsonrpc --port 15001 --message "Roll a 6-sided dice"
