@echo off
cd /d %~dp0..
call mvn -q exec:java -Dexec.mainClass="com.aloha.a2a.client.AlohaClient" -Dexec.args="--transport rest --port 11002 --message 'Roll a 6-sided dice'"
