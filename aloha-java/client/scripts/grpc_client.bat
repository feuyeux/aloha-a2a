@echo off
cd /d %~dp0..
set "EXEC_ARGS=--transport grpc --port 11000 --agent-card-port 11001 --message 'Roll a 6-sided dice'"
call mvn -q exec:java -Dexec.mainClass="com.aloha.a2a.client.AlohaClient" -Dexec.args="%EXEC_ARGS%"
