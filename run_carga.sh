#!/bin/bash
# ============================================================
#  Generador de Carga — Sistema Distribuido Steam (macOS/Linux)
#  Ejecutar DESPUÉS de tener al menos 1 nodo activo
#  Uso: ./run_carga.sh [host] [puerto] [duracionSeg] [hilos]
# ============================================================
# Directorio donde reside este script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

CLASSPATH="bin:lib/jackson-annotations-2.14.0.jar:lib/jackson-core-2.14.0.jar:lib/jackson-databind-2.14.0.jar:lib/mysql-connector-j-8.3.0.jar"

HOST=${1:-localhost}
PORT=${2:-5000}
DURATION=${3:-60}
THREADS=${4:-50}

echo "[LoadGen] Iniciando prueba de carga contra $HOST:$PORT (macOS/Linux)"
echo "[LoadGen] Hilos: $THREADS | Duración: $DURATION segundos"
echo ""

java -cp "$CLASSPATH" load.EjecutarGeneradorCarga "$HOST" "$PORT" "$DURATION" "$THREADS"
