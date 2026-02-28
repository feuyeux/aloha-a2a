@echo off
cd /d %~dp0..
call mvn -q exec:exec -Dexec.mainClass="com.aloha.a2a.client.AlohaClient" "-Dexec.args=--transport grpc --port 11000 --message Roll a 6-sided dice"
