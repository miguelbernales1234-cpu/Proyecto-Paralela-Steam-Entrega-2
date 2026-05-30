@echo off
REM ============================================================
REM  Lanzador del Nodo 1 — Sistema Distribuido Steam
REM  nodeId=1  clientPort=5000  peerPort=6000
REM ============================================================
echo [Nodo 1] Compilando proyecto...
cd /d "%~dp0"

set CLASSPATH=bin;lib\jackson-annotations-2.14.0.jar;lib\jackson-core-2.14.0.jar;lib\jackson-databind-2.14.0.jar;lib\mysql-connector-j-8.3.0.jar

javac -d bin -cp "%CLASSPATH%" --module-path lib -sourcepath src src\common\*.java src\server\*.java src\node\*.java src\load\*.java src\client\*.java src\module-info.java 2>nul

echo [Nodo 1] Iniciando nodo 1 (clientPort=5000, peerPort=6000)...
java -cp "%CLASSPATH%" node.NodoPeer 1 5000 6000 nodes.txt
pause
