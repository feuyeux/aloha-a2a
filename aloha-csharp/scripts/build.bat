@echo off
cd /d %~dp0..

echo Building C# project...
dotnet restore
dotnet build

echo C# build completed successfully!
