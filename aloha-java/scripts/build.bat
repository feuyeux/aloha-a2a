@echo off
cd /d %~dp0..

echo Building Java project...
call mvn clean install -q

echo Java build completed successfully!
