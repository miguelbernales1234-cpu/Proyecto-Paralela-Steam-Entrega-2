@echo off
REM ============================================================
REM  Generador de Carga — Sistema Distribuido Steam
REM  Ejecutar DESPUES de tener al menos 1 nodo activo
REM  Uso: run_carga.bat [host] [port] [durationSec] [threads]
REM ============================================================
cd /d "%~dp0"

set CLASSPATH=bin;lib\jackson-annotations-2.14.0.jar;lib\jackson-core-2.14.0.jar;lib\jackson-databind-2.14.0.jar;lib\mysql-connector-j-8.3.0.jar

set HOST=%1
set PORT=%2
set DURATION=%3
set THREADS=%4

if "%HOST%"=="" set HOST=localhost
if "%PORT%"=="" set PORT=5000
if "%DURATION%"=="" set DURATION=60
if "%THREADS%"=="" set THREADS=50

echo [LoadGen] Iniciando prueba de carga contra %HOST%:%PORT%
echo [LoadGen] Hilos: %THREADS% ^| Duracion: %DURATION% segundos
echo.

java -cp "%CLASSPATH%" load.EjecutarGeneradorCarga %HOST% %PORT% %DURATION% %THREADS%
pause
