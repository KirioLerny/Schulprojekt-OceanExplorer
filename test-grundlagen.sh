#!/bin/bash
echo "===================================="
echo "Ocean Explorer - Grundlagen Test"
echo "===================================="
echo ""
echo "OceanServer muss laufen auf:"
echo "  - Ship Port: 8150"
echo "  - Submarine Port: 8151"
echo ""
echo "Warte 5 Sekunden..."
sleep 5
echo ""
echo "Starte ShipApp..."
echo ""

cd "$(dirname "$0")"
mvn exec:java -Dexec.mainClass="ocean.Main"

