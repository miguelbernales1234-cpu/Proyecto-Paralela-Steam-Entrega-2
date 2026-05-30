@echo off
REM ============================================================
REM  Lanzador del Nodo 3 — Sistema Distribuido Steam
REM  nodeId=3  clientPort=5002  peerPort=6002
REM ============================================================
cd /d "%~dp0"

set CLASSPATH=bin;lib\jackson-annotations-2.14.0.jar;lib\jackson-core-2.14.0.jar;lib\jackson-databind-2.14.0.jar;lib\mysql-connector-j-8.3.0.jar

echo [Nodo 3] Iniciando nodo 3 (clientPort=5002, peerPort=6002)...
java -cp "%CLASSPATH%" node.NodoPeer 3 5002 6002 nodes.txt
pause
