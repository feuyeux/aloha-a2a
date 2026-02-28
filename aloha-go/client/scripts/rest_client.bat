@echo off
cd /d %~dp0..
go run . --transport rest --port 12002 --message "Roll a 6-sided dice"
