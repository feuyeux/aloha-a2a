@echo off
cd /d %~dp0..
call mvn -q exec:exec -Dexec.mainClass="com.aloha.a2a.client.AlohaClient" "-Dexec.args=--transport jsonrpc --port 11001 --message Roll a 6-sided dice"
