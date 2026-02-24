#!/usr/bin/env bash
# ============================================
# PHASE 4 DB-CHECK: Submarine-Tabellen pruefen
# macOS / Linux Version
# Fuehre dies NACH ./test-phase3.sh aus
# ============================================

# Hilfsfunktion: zählt Zeilen, filtert MySQL-Warnings
query_count() {
    docker exec -i oceanexplorer-mysql \
        mysql -u root -poceanexplorer_root -N -e "$1" 2>&1 \
        | grep -v "Warning" \
        | grep -v "^$" \
        | head -1
}

query_rows() {
    docker exec -i oceanexplorer-mysql \
        mysql -u root -poceanexplorer_root -N -e "$1" 2>&1 \
        | grep -v "Warning" \
        | grep -v "^$"
}

echo ""
echo "============================================"
echo "PHASE 4 CHECK: Submarine-Datenbank"
echo "============================================"
echo ""

# MySQL läuft?
MYSQL_RUNNING=$(docker ps --filter "name=oceanexplorer-mysql" --filter "status=running" --format "{{.Names}}" 2>&1)
if ! echo "$MYSQL_RUNNING" | grep -q "oceanexplorer-mysql"; then
    echo "FEHLER: MySQL Container laeuft nicht!"
    echo "Starte mit: docker compose up -d"
    exit 1
fi

# ---- [1/5] submarine ----------------------------------------
echo "[1/5] Pruefe submarine Tabelle..."
SUB_COUNT=$(query_count "SELECT COUNT(*) FROM oceanexplorer.submarine;")
SUB_COUNT=${SUB_COUNT:-0}
if [ "$SUB_COUNT" -gt 0 ] 2>/dev/null; then
    echo "  Submarines: $SUB_COUNT ✅"
    query_rows "SELECT id, name, ship_id, active FROM oceanexplorer.submarine;" \
        | while IFS= read -r line; do echo "    $line"; done
else
    echo "  Submarines: 0"
fi

# ---- [2/5] submarine_dive -----------------------------------
echo ""
echo "[2/5] Pruefe submarine_dive Tabelle..."
DIVE_COUNT=$(query_count "SELECT COUNT(*) FROM oceanexplorer.submarine_dive;")
DIVE_COUNT=${DIVE_COUNT:-0}
if [ "$DIVE_COUNT" -gt 0 ] 2>/dev/null; then
    echo "  Tauchgaenge: $DIVE_COUNT ✅"
    query_rows "SELECT sd.id, s.name, sd.status, sd.start_time, sd.end_time \
                FROM oceanexplorer.submarine_dive sd \
                JOIN oceanexplorer.submarine s ON s.id = sd.submarine_id LIMIT 5;" \
        | while IFS= read -r line; do echo "    $line"; done
else
    echo "  Tauchgaenge: 0"
fi

# ---- [3/5] submarine_measurement_point ----------------------
echo ""
echo "[3/5] Pruefe submarine_measurement_point Tabelle..."
MP_COUNT=$(query_count "SELECT COUNT(*) FROM oceanexplorer.submarine_measurement_point;")
MP_COUNT=${MP_COUNT:-0}
if [ "$MP_COUNT" -gt 0 ] 2>/dev/null; then
    echo "  Messpunkte: $MP_COUNT ✅"
    query_rows "SELECT id, dive_id, x, y, z FROM oceanexplorer.submarine_measurement_point LIMIT 5;" \
        | while IFS= read -r line; do echo "    $line"; done
else
    echo "  Messpunkte: 0"
fi

# ---- [4/5] submarine_photo ----------------------------------
echo ""
echo "[4/5] Pruefe submarine_photo Tabelle..."
PHOTO_COUNT=$(query_count "SELECT COUNT(*) FROM oceanexplorer.submarine_photo;")
PHOTO_COUNT=${PHOTO_COUNT:-0}
if [ "$PHOTO_COUNT" -gt 0 ] 2>/dev/null; then
    echo "  Fotos: $PHOTO_COUNT ✅"
    query_rows "SELECT id, dive_id, photo_format, LENGTH(photo_data) AS bytes FROM oceanexplorer.submarine_photo;" \
        | while IFS= read -r line; do echo "    $line"; done
else
    echo "  Fotos: 0"
fi

# ---- [5/5] accident -----------------------------------------
echo ""
echo "[5/5] Pruefe accident Tabelle..."
ACC_COUNT=$(query_count "SELECT COUNT(*) FROM oceanexplorer.accident;")
ACC_COUNT=${ACC_COUNT:-0}
if [ "$ACC_COUNT" -eq 0 ] 2>/dev/null; then
    echo "  Unfaelle: 0 (gut!) ✅"
else
    echo "  Unfaelle: $ACC_COUNT (Submarine hatte Unfall!)"
    query_rows "SELECT id, submarine_id, x, y, description FROM oceanexplorer.accident;" \
        | while IFS= read -r line; do echo "    $line"; done
fi

# ---- Ergebnis -----------------------------------------------
echo ""
echo "============================================"
echo "ERGEBNIS"
echo "============================================"

OK=true

if [ "${SUB_COUNT:-0}" -eq 0 ] 2>/dev/null; then
    echo "  [FEHLER] Keine Submarines gespeichert"
    OK=false
else
    echo "  [OK]     Submarines gespeichert ($SUB_COUNT)"
fi

if [ "${DIVE_COUNT:-0}" -eq 0 ] 2>/dev/null; then
    echo "  [FEHLER] Keine Tauchgaenge gespeichert"
    OK=false
else
    echo "  [OK]     Tauchgaenge gespeichert ($DIVE_COUNT)"
fi

if [ "${MP_COUNT:-0}" -eq 0 ] 2>/dev/null; then
    echo "  [WARN]   Keine Messpunkte (submarine sendet evtl. keine)"
else
    echo "  [OK]     Messpunkte gespeichert ($MP_COUNT)"
fi

if [ "${PHOTO_COUNT:-0}" -eq 0 ] 2>/dev/null; then
    echo "  [WARN]   Kein Foto (submarine sendet evtl. keines)"
else
    echo "  [OK]     Foto gespeichert ($PHOTO_COUNT)"
fi

echo ""
if [ "$OK" = true ]; then
    echo "✅ Phase 4 erfolgreich! (Schiff + Submarine)"
else
    echo "⚠️  Phase 4 unvollstaendig - pruefe die Logs oben"
    echo ""
    echo "Moegliche Ursachen:"
    echo "  - submarine.jar vorhanden?  ->  ls external/submarine.jar"
    echo "  - OceanServer Port 8151?    ->  GUI: Stop -> Start"
    echo "  - Wartezeit zu kurz?        ->  sleep in test-phase3.sh erhoehen"
fi
echo ""

