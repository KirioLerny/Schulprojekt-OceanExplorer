#!/usr/bin/env bash
# ============================================
# PHASE 3 TEST: Docker + MySQL Datenbank
# macOS / Linux Version
# ============================================

echo ""
echo "============================================"
echo "PHASE 3 TEST: Docker + MySQL Datenbank"
echo "============================================"
echo ""

# ---- [1/4] Docker prüfen ----------------------------------------
echo "[1/4] Pruefe Docker..."
if ! docker ps > /dev/null 2>&1; then
    echo ""
    echo "FEHLER: Docker laeuft nicht!"
    echo ""
    echo "Bitte starte Docker Desktop:"
    echo "  - Docker Desktop oeffnen"
    echo "  - Warte bis Docker bereit ist"
    echo ""
    exit 1
fi
echo "✅ Docker laeuft"

# ---- [2/4] MySQL Container --------------------------------------
echo ""
echo "[2/4] Starte MySQL Container..."
MYSQL_STATUS=$(docker ps --filter "name=oceanexplorer-mysql" --filter "status=running" --format "{{.Names}}" 2>&1)

if echo "$MYSQL_STATUS" | grep -q "oceanexplorer-mysql"; then
    echo "✅ MySQL Container laeuft bereits (healthy)"
else
    echo "MySQL Container nicht gefunden, starte..."
    docker compose up -d > /dev/null 2>&1
    echo "Warte 20 Sekunden bis MySQL bereit ist..."
    sleep 20

    MYSQL_STATUS2=$(docker ps --filter "name=oceanexplorer-mysql" --filter "status=running" --format "{{.Names}}" 2>&1)
    if echo "$MYSQL_STATUS2" | grep -q "oceanexplorer-mysql"; then
        echo "✅ MySQL Container gestartet"
    else
        echo "FEHLER: MySQL Container konnte nicht gestartet werden!"
        echo "Ausgabe: $MYSQL_STATUS2"
        exit 1
    fi
fi

# ---- [3/4] OceanServer prüfen -----------------------------------
echo ""
echo "[3/4] Pruefe OceanServer (Port 8150)..."

test_ocean_server_ready() {
    # Sendet einen Test-Launch und prüft ob der Server antwortet
    python3 - <<'PYEOF' 2>/dev/null
import socket, sys
try:
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(3)
    s.connect(("localhost", 8150))
    s.sendall(b'{"name":"__test__","typ":"ship","cmd":"launch","dir":{"vec2":[1,0]},"sector":{"vec2":[99,99]}}\n')
    data = s.recv(4096)
    s.sendall(b'{"cmd":"exit"}\n')
    s.close()
    sys.exit(0 if data else 1)
except Exception:
    sys.exit(1)
PYEOF
}

if ! test_ocean_server_ready; then
    echo ""
    echo "FEHLER: OceanServer akzeptiert keine Kommandos!"
    echo ""
    echo "Bitte in der OceanServer-GUI:"
    echo "  1. Falls noch nicht gestartet: 'Start' klicken"
    echo "  2. Falls schon gestartet aber haengend: 'Stop' dann 'Start' klicken"
    echo "  3. Der Server muss 'Running' anzeigen"
    echo ""
    read -rp "Druecke Enter wenn OceanServer bereit ist (gruenes 'Running' in GUI)..."

    if ! test_ocean_server_ready; then
        echo "OceanServer antwortet immer noch nicht!"
        exit 1
    fi
fi
echo "✅ OceanServer antwortet auf Kommandos"

# ---- [4/4] ShipApp starten --------------------------------------
echo ""
echo "[4/4] Starte ShipApp mit Datenbank-Integration..."
echo ""
echo "Warte 3 Sekunden vor dem Start..."
sleep 3
echo ""

cd "$(dirname "$0")"
mvn exec:java -Dexec.mainClass="ocean.Main"

echo ""
echo "============================================"
echo "Test beendet"
echo "============================================"
echo ""
echo "Datenbank pruefen:"
echo "  - DataGrip / IntelliJ DB Plugin verwenden"
echo "  - Host: localhost:3306"
echo "  - Login: root / oceanexplorer_root  (oder oceanapp / oceanpass123)"
echo "  - Datenbank: oceanexplorer"
echo ""
echo "Docker Container stoppen:"
echo "  docker compose down"
echo ""
