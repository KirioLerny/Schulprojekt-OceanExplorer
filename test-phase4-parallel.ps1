# ============================================================
# PHASE 4 TEST: 3-4 Submarines parallel
# Prüft ob mehrere Submarines gleichzeitig tauchen und Daten
# korrekt in der Datenbank gespeichert werden.
# ============================================================

function QueryCount($sql) {
    $raw = docker exec -i oceanexplorer-mysql mysql -u root -poceanexplorer_root -N -e $sql 2>&1
    $lines = @($raw | Where-Object { $_ -notmatch "Warning" -and "$_".Trim() -ne "" })
    if ($lines.Count -eq 0) { return 0 }
    $n = 0
    if ([int]::TryParse("$($lines[0])".Trim(), [ref]$n)) { return $n }
    return 0
}
function QueryRows($sql) {
    $raw = docker exec -i oceanexplorer-mysql mysql -u root -poceanexplorer_root -e $sql 2>&1
    return @($raw | Where-Object { $_ -notmatch "Warning" -and "$_".Trim() -ne "" })
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " PHASE 4 TEST: Parallele Submarines" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Voraussetzungen prüfen
$mysqlRunning = docker ps --filter "name=oceanexplorer-mysql" --filter "status=running" --format "{{.Names}}" 2>&1
if (-not ($mysqlRunning -match "oceanexplorer-mysql")) {
    Write-Host "FEHLER: MySQL Container läuft nicht!" -ForegroundColor Red
    Write-Host "Starte mit: docker compose up -d"
    exit 1
}
Write-Host "✅ MySQL läuft" -ForegroundColor Green

# DB vor dem Test leeren (nur Submarine-Tabellen)
Write-Host ""
Write-Host "Leere Submarine-Tabellen für sauberen Test..." -ForegroundColor Yellow
docker exec -i oceanexplorer-mysql mysql -u root -poceanexplorer_root -e `
    "DELETE FROM oceanexplorer.accident; DELETE FROM oceanexplorer.submarine_photo; DELETE FROM oceanexplorer.submarine_measurement_point; DELETE FROM oceanexplorer.submarine_dive; DELETE FROM oceanexplorer.submarine;" 2>&1 | Out-Null
Write-Host "✅ Tabellen geleert" -ForegroundColor Green

# Zähler VOR dem Test
$subsBefore    = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine;"
$divesBefore   = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_dive;"
$pointsBefore  = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_measurement_point;"
$photosBefore  = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_photo;"

Write-Host ""
Write-Host "--- Zustand VOR dem Test ---" -ForegroundColor Yellow
Write-Host "  Submarines: $subsBefore"
Write-Host "  Tauchgänge: $divesBefore"
Write-Host "  Messpunkte: $pointsBefore"
Write-Host "  Fotos:      $photosBefore"

# Erst kompilieren (einmalig, ohne exec)
Write-Host ""
Write-Host "Kompiliere Projekt..." -ForegroundColor Yellow
mvn -q compile
if ($LASTEXITCODE -ne 0) {
    Write-Host "FEHLER: Kompilierung fehlgeschlagen!" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Kompilierung erfolgreich" -ForegroundColor Green

# ShipApp ausführen (nur exec, kein clean/compile mehr)
Write-Host ""
Write-Host "Starte ShipApp (Phase 4 mit 3 parallelen Submarines)..." -ForegroundColor Cyan
Write-Host "(Dies dauert ca. 60-90 Sekunden)"
Write-Host ""
mvn -q exec:java "-Dexec.mainClass=ocean.Main" 2>&1
$mvnExit = $LASTEXITCODE
Write-Host ""

# Zähler NACH dem Test
$subsAfter     = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine;"
$divesAfter    = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_dive;"
$pointsAfter   = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_measurement_point;"
$photosAfter   = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_photo;"
$accidentsAfter= QueryCount "SELECT COUNT(*) FROM oceanexplorer.accident;"

Write-Host "--- Zustand NACH dem Test ---" -ForegroundColor Yellow
Write-Host "  Submarines: $subsAfter"
Write-Host "  Tauchgänge: $divesAfter"
Write-Host "  Messpunkte: $pointsAfter"
Write-Host "  Fotos:      $photosAfter"
Write-Host "  Unfälle:    $accidentsAfter"

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " TESTERGEBNISSE" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

$passed = 0
$failed = 0

function Check($beschreibung, $wert, $minErwartet) {
    if ($wert -ge $minErwartet) {
        Write-Host "  ✅ $beschreibung`: $wert (erwartet >= $minErwartet)" -ForegroundColor Green
        $script:passed++
    } else {
        Write-Host "  ❌ $beschreibung`: $wert (erwartet >= $minErwartet)" -ForegroundColor Red
        $script:failed++
    }
}

# [1] Mehrere Submarines registriert (mindestens 2 von 3 gestartet)
Check "Submarines in DB" $subsAfter 2

# [2] Mehrere Tauchgänge gestartet
Check "Tauchgänge gesamt" $divesAfter 2

# [3] Mindestens ein Tauchgang beendet (SURFACED oder CRASHED)
$finishedDives = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_dive WHERE status IN ('SURFACED','CRASHED','ABORTED');"
Check "Beendete Tauchgänge" $finishedDives 1

# [4] 3D-Messpunkte vorhanden
Check "3D-Messpunkte" $pointsAfter 1

# [4b] Fotos vorhanden
Check "Fotos in DB" $photosAfter 1

# [4c] Fotos haben Positionsdaten
$photosWithPos = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_photo WHERE x IS NOT NULL;"
Check "Fotos mit Position" $photosWithPos 1

# [5] Kein Tauchgang hängt ewig auf DIVING (alle sollten abgeschlossen sein)
$divingStuck = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_dive WHERE status = 'DIVING' AND start_time < DATE_SUB(NOW(), INTERVAL 5 MINUTE);"
if ($divingStuck -eq 0) {
    Write-Host "  ✅ Keine veralteten DIVING-Einträge" -ForegroundColor Green
    $passed++
} else {
    Write-Host "  ⚠️  $divingStuck veraltete DIVING-Einträge (möglicher Fehler)" -ForegroundColor Yellow
}

# [6] Submarines sind deaktiviert nach dem Tauchgang
$activeAfter = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine WHERE active = 1;"
if ($activeAfter -eq 0) {
    Write-Host "  ✅ Alle Submarines inaktiv nach Tauchgang" -ForegroundColor Green
    $passed++
} else {
    Write-Host "  ⚠️  $activeAfter Submarine(s) noch aktiv (ggf. noch laufend)" -ForegroundColor Yellow
}

# Detaillierte Ausgabe der Tauchgänge
Write-Host ""
Write-Host "--- Tauchgang-Details ---" -ForegroundColor Yellow
QueryRows "SELECT sd.id, s.name, sd.status, sd.start_time, sd.end_time FROM oceanexplorer.submarine_dive sd JOIN oceanexplorer.submarine s ON s.id = sd.submarine_id ORDER BY sd.start_time;" | ForEach-Object {
    Write-Host "  $_"
}

# Messpunkte pro Tauchgang
Write-Host ""
Write-Host "--- Messpunkte pro Tauchgang ---" -ForegroundColor Yellow
QueryRows "SELECT dive_id, COUNT(*) AS punkte FROM oceanexplorer.submarine_measurement_point GROUP BY dive_id;" | ForEach-Object {
    Write-Host "  $_"
}

# Foto-Details
Write-Host ""
Write-Host "--- Foto-Details ---" -ForegroundColor Yellow
QueryRows "SELECT id, dive_id, photo_format, LENGTH(photo_data) AS bytes, x, y, z, dir_x, dir_y, dir_z, timestamp FROM oceanexplorer.submarine_photo;" | ForEach-Object {
    Write-Host "  $_"
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
if ($failed -eq 0) {
    Write-Host "  ERGEBNIS: ✅ ALLE TESTS BESTANDEN ($passed/$($passed+$failed))" -ForegroundColor Green
} else {
    Write-Host "  ERGEBNIS: ❌ $failed TEST(S) FEHLGESCHLAGEN ($passed/$($passed+$failed) bestanden)" -ForegroundColor Red
}
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

exit $failed

