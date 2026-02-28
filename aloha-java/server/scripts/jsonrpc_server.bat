@echo off
cd /d %~dp0..
call mvn exec:exec -Dexec.mainClass="com.aloha.a2a.server.AlohaServer" -Dtransport.mode=jsonrpc -q
