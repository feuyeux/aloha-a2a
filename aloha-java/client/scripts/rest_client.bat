@echo off
cd /d %~dp0..
set "EXEC_ARGS=--transport rest --port 11002 --message ""Roll a 6-sided dice"""
call mvn -q exec:exec -Dexec.mainClass="com.aloha.a2a.client.AlohaClient" "-Dexec.args=%EXEC_ARGS%"
