#!/bin/bash
# ============================================================
#  Lanzador del Nodo 1 — Sistema Distribuido Steam (macOS/Linux)
#  nodeId=1  clientPort=5000  peerPort=6000
# ============================================================
echo "[Nodo 1] Compilando proyecto..."

# Directorio donde reside este script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

# En macOS/Linux el separador de classpath es ":" en lugar de ";"
CLASSPATH="bin:lib/jackson-annotations-2.14.0.jar:lib/jackson-core-2.14.0.jar:lib/jackson-databind-2.14.0.jar:lib/mysql-connector-j-8.3.0.jar"

mkdir -p bin

javac -d bin -cp "$CLASSPATH" --module-path lib -sourcepath src src/module-info.java src/common/*.java src/server/*.java src/node/*.java src/load/*.java src/client/*.java 2>/dev/null

echo "[Nodo 1] Iniciando nodo 1 (puertoCliente=5000, puertoPeer=6000)..."
java -cp "$CLASSPATH" node.NodoPeer 1 5000 6000 nodes.txt
