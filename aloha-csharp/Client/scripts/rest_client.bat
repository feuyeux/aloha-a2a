@echo off
cd /d %~dp0..
dotnet run -- --transport rest --port 15002 --message "Roll a 6-sided dice"
