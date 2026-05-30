#!/bin/bash
# ============================================================
#  Lanzador del Nodo 2 — Sistema Distribuido Steam (macOS/Linux)
#  nodeId=2  clientPort=5001  peerPort=6001
# ============================================================
# Directorio donde reside este script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

CLASSPATH="bin:lib/jackson-annotations-2.14.0.jar:lib/jackson-core-2.14.0.jar:lib/jackson-databind-2.14.0.jar:lib/mysql-connector-j-8.3.0.jar"

echo "[Nodo 2] Iniciando nodo 2 (puertoCliente=5001, puertoPeer=6001)..."
java -cp "$CLASSPATH" node.NodoPeer 2 5001 6001 nodes.txt
