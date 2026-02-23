# ============================================
# PHASE 4 DB-CHECK: Submarine-Tabellen pruefen
# Fuehre dies NACH .\test-phase3.ps1 aus
# ============================================
function QueryCount($sql) {
    $raw = docker exec -i oceanexplorer-mysql mysql -u root -poceanexplorer_root -N -e $sql 2>&1
    $lines = @($raw | Where-Object { $_ -notmatch "Warning" -and "$_".Trim() -ne "" })
    if ($lines.Count -eq 0) { return 0 }
    $n = 0
    if ([int]::TryParse("$($lines[0])".Trim(), [ref]$n)) { return $n }
    return 0
}
function QueryRows($sql) {
    $raw = docker exec -i oceanexplorer-mysql mysql -u root -poceanexplorer_root -N -e $sql 2>&1
    return @($raw | Where-Object { $_ -notmatch "Warning" -and "$_".Trim() -ne "" })
}
Write-Host ""
Write-Host "============================================"
Write-Host "PHASE 4 CHECK: Submarine-Datenbank"
Write-Host "============================================"
Write-Host ""
$mysqlRunning = docker ps --filter "name=oceanexplorer-mysql" --filter "status=running" --format "{{.Names}}" 2>&1
if (-not ($mysqlRunning -match "oceanexplorer-mysql")) {
    Write-Host "FEHLER: MySQL Container laeuft nicht!" -ForegroundColor Red
    Write-Host "Starte mit: docker compose up -d"
    exit 1
}
# [1/5] submarine
Write-Host "[1/5] Pruefe submarine Tabelle..." -ForegroundColor Cyan
$subCount = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine;"
if ($subCount -gt 0) {
    Write-Host "  Submarines: $subCount" -ForegroundColor Green
    QueryRows "SELECT id, name, ship_id, active FROM oceanexplorer.submarine;" | ForEach-Object { Write-Host "    $_" }
} else {
    Write-Host "  Submarines: 0" -ForegroundColor Yellow
}
# [2/5] submarine_dive
Write-Host ""
Write-Host "[2/5] Pruefe submarine_dive Tabelle..." -ForegroundColor Cyan
$diveCount = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_dive;"
if ($diveCount -gt 0) {
    Write-Host "  Tauchgaenge: $diveCount" -ForegroundColor Green
    QueryRows "SELECT sd.id, s.name, sd.status, sd.start_time, sd.end_time FROM oceanexplorer.submarine_dive sd JOIN oceanexplorer.submarine s ON s.id = sd.submarine_id LIMIT 5;" | ForEach-Object { Write-Host "    $_" }
} else {
    Write-Host "  Tauchgaenge: 0" -ForegroundColor Yellow
}
# [3/5] submarine_measurement_point
Write-Host ""
Write-Host "[3/5] Pruefe submarine_measurement_point Tabelle..." -ForegroundColor Cyan
$mpCount = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_measurement_point;"
if ($mpCount -gt 0) {
    Write-Host "  Messpunkte: $mpCount" -ForegroundColor Green
    QueryRows "SELECT id, dive_id, x, y, z FROM oceanexplorer.submarine_measurement_point LIMIT 5;" | ForEach-Object { Write-Host "    $_" }
} else {
    Write-Host "  Messpunkte: 0" -ForegroundColor Yellow
}
# [4/5] submarine_photo
Write-Host ""
Write-Host "[4/5] Pruefe submarine_photo Tabelle..." -ForegroundColor Cyan
$photoCount = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_photo;"
if ($photoCount -gt 0) {
    Write-Host "  Fotos: $photoCount" -ForegroundColor Green
    QueryRows "SELECT id, dive_id, photo_format, LENGTH(photo_data) AS bytes FROM oceanexplorer.submarine_photo;" | ForEach-Object { Write-Host "    $_" }
} else {
    Write-Host "  Fotos: 0" -ForegroundColor Yellow
}
# [5/5] accident
Write-Host ""
Write-Host "[5/5] Pruefe accident Tabelle..." -ForegroundColor Cyan
$accCount = QueryCount "SELECT COUNT(*) FROM oceanexplorer.accident;"
if ($accCount -eq 0) {
    Write-Host "  Unfaelle: 0 (gut!)" -ForegroundColor Green
} else {
    Write-Host "  Unfaelle: $accCount (Submarine hatte Unfall!)" -ForegroundColor Red
    QueryRows "SELECT id, submarine_id, x, y, description FROM oceanexplorer.accident;" | ForEach-Object { Write-Host "    $_" }
}
# Ergebnis
Write-Host ""
Write-Host "============================================"
Write-Host "ERGEBNIS"
Write-Host "============================================"
$ok = $true
if ($subCount -eq 0)   { Write-Host "  [FEHLER] Keine Submarines gespeichert"                       -ForegroundColor Red;    $ok = $false }
else                    { Write-Host "  [OK]     Submarines gespeichert ($subCount)"                 -ForegroundColor Green }
if ($diveCount -eq 0)  { Write-Host "  [FEHLER] Keine Tauchgaenge gespeichert"                      -ForegroundColor Red;    $ok = $false }
else                    { Write-Host "  [OK]     Tauchgaenge gespeichert ($diveCount)"               -ForegroundColor Green }
if ($mpCount -eq 0)    { Write-Host "  [WARN]   Keine Messpunkte (submarine sendet evtl. keine)"     -ForegroundColor Yellow }
else                    { Write-Host "  [OK]     Messpunkte gespeichert ($mpCount)"                   -ForegroundColor Green }
if ($photoCount -eq 0) { Write-Host "  [WARN]   Kein Foto (submarine sendet evtl. keines)"           -ForegroundColor Yellow }
else                    { Write-Host "  [OK]     Foto gespeichert ($photoCount)"                      -ForegroundColor Green }
Write-Host ""
if ($ok) {
    Write-Host "Phase 4 erfolgreich! (Schiff + Submarine)" -ForegroundColor Green
} else {
    Write-Host "Phase 4 unvollstaendig - pruefe die Logs oben" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Moegliche Ursachen:"
    Write-Host "  - submarine.jar vorhanden?  ->  Test-Path external\submarine.jar"
    Write-Host "  - OceanServer Port 8151?    ->  GUI: Stop -> Start"
    Write-Host "  - Wartezeit zu kurz?        ->  60s in test-phase3.ps1 erhoehen"
}
Write-Host ""
