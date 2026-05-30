@echo off
REM ============================================================
REM  Lanzador del Nodo 2 — Sistema Distribuido Steam
REM  nodeId=2  clientPort=5001  peerPort=6001
REM ============================================================
cd /d "%~dp0"

set CLASSPATH=bin;lib\jackson-annotations-2.14.0.jar;lib\jackson-core-2.14.0.jar;lib\jackson-databind-2.14.0.jar;lib\mysql-connector-j-8.3.0.jar

echo [Nodo 2] Iniciando nodo 2 (clientPort=5001, peerPort=6001)...
java -cp "%CLASSPATH%" node.NodoPeer 2 5001 6001 nodes.txt
pause
