#!/bin/bash
# ============================================================
#  Lanzador del Nodo 3 — Sistema Distribuido Steam (macOS/Linux)
#  nodeId=3  clientPort=5002  peerPort=6002
# ============================================================
# Directorio donde reside este script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

CLASSPATH="bin:lib/jackson-annotations-2.14.0.jar:lib/jackson-core-2.14.0.jar:lib/jackson-databind-2.14.0.jar:lib/mysql-connector-j-8.3.0.jar"

echo "[Nodo 3] Iniciando nodo 3 (puertoCliente=5002, puertoPeer=6002)..."
java -cp "$CLASSPATH" node.NodoPeer 3 5002 6002 nodes.txt
