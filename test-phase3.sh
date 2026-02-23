#!/bin/bash
# ============================================
# PHASE 3 TEST: Docker + MySQL Datenbank
# ============================================

echo ""
echo "============================================"
echo "PHASE 3 TEST: Docker + MySQL Datenbank"
echo "============================================"
echo ""

# Prüfe ob Docker läuft
echo "[1/4] Prüfe Docker..."
if ! docker ps > /dev/null 2>&1; then
    echo ""
    echo "❌ FEHLER: Docker läuft nicht!"
    echo ""
    echo "Bitte starte Docker Desktop:"
    echo "  - Docker Desktop öffnen"
    echo "  - Warte bis Docker bereit ist"
    echo ""
    exit 1
fi
echo "✅ Docker läuft"

echo ""
echo "[2/4] Starte MySQL Container..."
if docker-compose ps | grep -q "oceanexplorer-mysql.*Up"; then
    echo "✅ MySQL Container läuft bereits"
else
    echo "MySQL Container nicht gefunden, starte..."
    docker-compose up -d
    echo "Warte 15 Sekunden bis MySQL bereit ist..."
    sleep 15
fi

echo ""
echo "[3/4] Prüfe OceanServer (Port 8150)..."
if ! nc -z localhost 8150 2>/dev/null; then
    echo ""
    echo "❌ FEHLER: OceanServer läuft nicht!"
    echo ""
    echo "Bitte starte zuerst den OceanServer:"
    echo "  cd external"
    echo "  java -jar oceanserver.jar"
    echo "  (GUI öffnet sich) -> Start klicken"
    echo ""
    echo "Drücke Enter, wenn OceanServer bereit ist..."
    read

    # Nochmal prüfen
    if ! nc -z localhost 8150 2>/dev/null; then
        echo "❌ Port 8150 immer noch nicht erreichbar!"
        exit 1
    fi
fi
echo "✅ OceanServer erreichbar"

echo ""
echo "[4/4] Starte ShipApp mit Datenbank-Integration..."
echo ""
echo "Warte 5 Sekunden vor dem Start..."
sleep 5
echo ""

cd "$(dirname "$0")"
mvn exec:java -Dexec.mainClass="ocean.Main"

echo ""
echo "============================================"
echo "Test beendet"
echo "============================================"
echo ""
echo "📊 Datenbank prüfen:"
echo "  - PHPMyAdmin: http://localhost:8080"
echo "  - Login: root / oceanexplorer"
echo "  - Datenbank: oceanexplorer"
echo ""
echo "🐳 Docker Container stoppen:"
echo "  docker-compose down"
echo ""

