@echo off
cd /d %~dp0..
set TRANSPORT_MODE=jsonrpc
go run .
