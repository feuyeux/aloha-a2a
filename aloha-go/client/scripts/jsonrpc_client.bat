@echo off
cd /d %~dp0..
go run . --transport jsonrpc --port 12001 --message "Roll a 6-sided dice"
