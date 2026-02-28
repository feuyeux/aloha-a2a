@echo off
cd /d %~dp0..
set TRANSPORT_MODE=grpc
npm start
