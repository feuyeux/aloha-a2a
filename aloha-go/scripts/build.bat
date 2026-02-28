@echo off
cd /d %~dp0..

echo Building Go project...
go mod tidy
go build ./...

echo Go build completed successfully!
