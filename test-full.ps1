# ============================================================
# FULL TEST: ShipApp starten -> Submarines tauchen -> API testen
# Aufruf:
#   .\test-full.ps1
#   .\test-full.ps1 -SkipClean   (DB nicht leeren)
#   .\test-full.ps1 -NoBrowser   (kein Browser am Ende)
# ============================================================
param([switch]$SkipClean,[switch]$NoBrowser,[int]$Port=8080)
$BASE   = "http://localhost:$Port"
$PROJ   = $PSScriptRoot
$passed = 0
$failed = 0
$warned = 0
function Sep($t)  { Write-Host ""; Write-Host "============================================================" -ForegroundColor DarkCyan; Write-Host "  $t" -ForegroundColor Cyan; Write-Host "============================================================" -ForegroundColor DarkCyan }
function Step($n,$tot,$t) { Write-Host ""; Write-Host "[$n/$tot] $t" -ForegroundColor White }
function Pass($m) { Write-Host "  [PASS] $m" -ForegroundColor Green;  $script:passed++ }
function Fail($m) { Write-Host "  [FAIL] $m" -ForegroundColor Red;    $script:failed++ }
function Warn($m) { Write-Host "  [WARN] $m" -ForegroundColor Yellow; $script:warned++ }
function Info($m) { Write-Host "         $m" -ForegroundColor Gray }
function QueryCount($sql) {
    $raw = docker exec -i oceanexplorer-mysql mysql -u root -poceanexplorer_root -N -e $sql 2>&1
    $lines = @($raw | Where-Object { $_ -notmatch "Warning" -and "$_".Trim() -ne "" })
    if ($lines.Count -eq 0) { return 0 }
    $n = 0; [int]::TryParse("$($lines[0])".Trim(), [ref]$n) | Out-Null; return $n
}
function QueryRows($sql) {
    $raw = docker exec -i oceanexplorer-mysql mysql -u root -poceanexplorer_root -N -e $sql 2>&1
    return @($raw | Where-Object { $_ -notmatch "Warning" -and "$_".Trim() -ne "" })
}
function Test-OceanServer {
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $tcp.Connect("localhost", 8150)
        $stream = $tcp.GetStream(); $stream.ReadTimeout = 3000
        $w = New-Object System.IO.StreamWriter($stream); $w.AutoFlush = $true
        $r = New-Object System.IO.StreamReader($stream)
        $w.WriteLine("{""name"":""__probe__"",""typ"":""ship"",""cmd"":""launch"",""dir"":{""vec2"":[1,0]},""sector"":{""vec2"":[98,98]}}")
        try { $r.ReadLine() | Out-Null; $w.WriteLine("{""cmd"":""exit""}") } catch {}
        $tcp.Close(); return $true
    } catch { return $false }
}
function Invoke-Api($method,$path,[switch]$Raw) {
    try {
        if ($Raw) { return Invoke-WebRequest  -Uri "$BASE$path" -Method $method -UseBasicParsing -ErrorAction Stop }
        else       { return Invoke-RestMethod -Uri "$BASE$path" -Method $method -UseBasicParsing -ErrorAction Stop }
    } catch { return $null }
}
# ------ SCHRITT 1: Voraussetzungen ------
Sep "SCHRITT 1: Voraussetzungen pruefen"
Step 1 4 "Docker pruefen..."
docker ps 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "Docker laeuft nicht"; exit 1 }
Pass "Docker laeuft"
Step 2 4 "MySQL Container pruefen..."
$ms = docker ps --filter "name=oceanexplorer-mysql" --filter "status=running" --format "{{.Names}}" 2>&1
if ($ms -match "oceanexplorer-mysql") {
    Pass "MySQL laeuft"
} else {
    Write-Host "  MySQL nicht gefunden - starte..." -ForegroundColor Yellow
    Set-Location $PROJ
    docker compose up -d 2>&1 | Out-Null
    Start-Sleep -Seconds 20
    $ms2 = docker ps --filter "name=oceanexplorer-mysql" --filter "status=running" --format "{{.Names}}" 2>&1
    if ($ms2 -match "oceanexplorer-mysql") { Pass "MySQL gestartet" }
    else { Fail "MySQL konnte nicht gestartet werden"; exit 1 }
}
Step 3 4 "OceanServer (Port 8150) pruefen..."
if (-not (Test-OceanServer)) {
    Write-Host "  OceanServer antwortet nicht!" -ForegroundColor Red
    Write-Host "  -> Bitte in der OceanServer-GUI auf Start klicken" -ForegroundColor Yellow
    Read-Host "  [Enter] wenn OceanServer bereit ist"
    if (-not (Test-OceanServer)) { Fail "OceanServer antwortet nicht"; exit 1 }
}
Pass "OceanServer antwortet"
Step 4 4 "Projekt kompilieren..."
Set-Location $PROJ
& mvn compile -q 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "Kompilierung fehlgeschlagen"; exit 1 }
Pass "Kompilierung erfolgreich"
# ------ SCHRITT 2: DB leeren ------
Sep "SCHRITT 2: Datenbank vorbereiten"
if ($SkipClean) {
    Warn "DB wird NICHT geleert (-SkipClean)"
} else {
    $cf = "$PROJ\clear_db.sql"
    if (Test-Path $cf) {
        Write-Host "  Leere DB via clear_db.sql..." -ForegroundColor Yellow
        Get-Content $cf | docker exec -i oceanexplorer-mysql mysql -u root -poceanexplorer_root oceanexplorer 2>&1 | Out-Null
        Pass "DB geleert (clear_db.sql)"
    } else {
        Warn "clear_db.sql nicht gefunden - ueberspringe"
    }
}
$subsBefore   = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine;"
$divesBefore  = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_dive;"
$pointsBefore = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_measurement_point;"
$photosBefore = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_photo;"
Write-Host ""
Write-Host "  VOR dem Test: Submarines=$subsBefore  Tauchgaenge=$divesBefore  Messpunkte=$pointsBefore  Fotos=$photosBefore" -ForegroundColor Gray
# ------ SCHRITT 3: ShipApp starten ------
Sep "SCHRITT 3: ShipApp + Submarines starten"
Write-Host "  Starte ShipApp (3 Submarines, ca. 60-120s)..." -ForegroundColor Yellow
Write-Host "  PhotoApiServer laeuft automatisch auf Port $Port" -ForegroundColor Gray
Write-Host ""
Set-Location $PROJ
& mvn exec:java "-Dexec.mainClass=ocean.Main"
$mvnExit = $LASTEXITCODE
Write-Host ""
if ($mvnExit -eq 0) { Pass "ShipApp beendet (Exit 0)" }
else { Warn "ShipApp beendet mit Exit-Code $mvnExit" }
# ------ SCHRITT 4: DB pruefen ------
Sep "SCHRITT 4: Datenbank-Ergebnisse"
$subsAfter   = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine;"
$divesAfter  = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_dive;"
$pointsAfter = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_measurement_point;"
$photosAfter = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_photo;"
$accAfter    = QueryCount "SELECT COUNT(*) FROM oceanexplorer.accident;"
$newSubs   = $subsAfter   - $subsBefore
$newDives  = $divesAfter  - $divesBefore
$newPoints = $pointsAfter - $pointsBefore
$newPhotos = $photosAfter - $photosBefore
Write-Host "  NEU: +$newSubs Submarines  +$newDives Tauchgaenge  +$newPoints Messpunkte  +$newPhotos Fotos" -ForegroundColor White
Write-Host ""
Step 1 6 "Submarines in DB (>= 2)..."
if ($newSubs -ge 2) { Pass "$newSubs neue Submarines" } else { Fail "Nur $newSubs Submarine(s) - erwartet >= 2" }
Step 2 6 "Tauchgaenge gestartet (>= 2)..."
if ($newDives -ge 2) { Pass "$newDives neue Tauchgaenge" } else { Fail "Nur $newDives Tauchgaenge - erwartet >= 2" }
Step 3 6 "Tauchgaenge abgeschlossen..."
$stuck = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_dive WHERE status = 'DIVING';"
if ($stuck -eq 0) { Pass "Alle Tauchgaenge sauber beendet" } else { Warn "$stuck Tauchgang/Tauchgaenge noch auf DIVING" }
Step 4 6 "3D-Messpunkte gespeichert..."
if ($newPoints -ge 1) { Pass "$newPoints neue Messpunkte" } else { Warn "Keine neuen Messpunkte" }
Step 5 6 "Fotos gespeichert..."
if ($newPhotos -ge 1) { Pass "$newPhotos neues Foto/neue Fotos" } else { Warn "Keine neuen Fotos" }
Step 6 6 "UNIQUE(x,y,z) Constraint vorhanden..."
$uq = QueryCount "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='oceanexplorer' AND TABLE_NAME='submarine_measurement_point' AND CONSTRAINT_TYPE='UNIQUE';"
if ($uq -gt 0) { Pass "UNIQUE(x,y,z) aktiv" } else { Fail "UNIQUE(x,y,z) fehlt - init.sql neu einspielen!" }
Write-Host ""
Write-Host "  -- Tauchgang-Details --" -ForegroundColor DarkGray
QueryRows "SELECT sd.id, s.name, sd.status, sd.start_time, sd.end_time FROM oceanexplorer.submarine_dive sd JOIN oceanexplorer.submarine s ON s.id = sd.submarine_id ORDER BY sd.id;" | ForEach-Object { Info $_ }
Write-Host ""
Write-Host "  -- Messpunkte pro Tauchgang --" -ForegroundColor DarkGray
QueryRows "SELECT dive_id, COUNT(*) AS punkte FROM oceanexplorer.submarine_measurement_point GROUP BY dive_id;" | ForEach-Object { Info $_ }
Write-Host ""
Write-Host "  -- Foto-Details --" -ForegroundColor DarkGray
QueryRows "SELECT id, dive_id, photo_format, LENGTH(photo_data) AS bytes, x, y, z FROM oceanexplorer.submarine_photo;" | ForEach-Object { Info $_ }
if ($accAfter -gt 0) {
    Write-Host "  -- Unfaelle --" -ForegroundColor Red
    QueryRows "SELECT id, submarine_id, x, y, description FROM oceanexplorer.accident;" | ForEach-Object { Write-Host "         $_" -ForegroundColor Red }
}
# ------ SCHRITT 5: API testen ------
Sep "SCHRITT 5: Photo REST-API testen (Port $Port)"
$apiUp = $false
$ping = Invoke-Api GET "/" -Raw
if ($ping -and $ping.StatusCode -eq 200) {
    $apiUp = $true
    Pass "PhotoApiServer laeuft auf Port $Port"
} else {
    Warn "PhotoApiServer gestoppt (laeuft nur waehrend ShipApp aktiv)"
    Write-Host "  -> Tipp: ShipApp offen lassen, dann .\test-photo-api.ps1 ausfuehren" -ForegroundColor Yellow
}
if ($apiUp) {
    Step 1 4 "GET /api/photos..."
    $allPhotos = Invoke-Api GET "/api/photos"
    if ($null -eq $allPhotos) { Fail "/api/photos nicht erreichbar" }
    elseif ($allPhotos -is [array]) {
        Pass "/api/photos liefert Array ($($allPhotos.Count) Eintraege)"
        if ($allPhotos.Count -gt 0) {
            $f = $allPhotos[0]
            Info "Erstes Foto: id=$($f.id)  sub=$($f.submarineName)  pos=($($f.x),$($f.y),$($f.z))"
            $req  = @("id","diveId","submarineName","x","y","z","dirX","dirY","dirZ","timestamp")
            $miss = $req | Where-Object { $f.PSObject.Properties.Name -notcontains $_ }
            if ($miss.Count -eq 0) { Pass "Alle JSON-Felder vorhanden" } else { Fail "Fehlende Felder: $($miss -join ',')"}
        }
    } else { Fail "/api/photos unerwartetes Format" }
    Step 2 4 "GET /api/photos/{id} - PNG-Download..."
    if ($allPhotos -and $allPhotos.Count -gt 0) {
        $pid2    = $allPhotos[0].id
        $imgResp = Invoke-Api GET "/api/photos/$pid2" -Raw
        $imgCt   = if ($imgResp) { $imgResp.Headers."Content-Type" } else { "" }
        if ($imgResp -and $imgResp.StatusCode -eq 200 -and $imgCt -match "image/png") {
            $ib = $imgResp.Content
            Pass "Foto $pid2 als image/png ($($ib.Length) Bytes)"
            if ($ib.Length -ge 4 -and $ib[0] -eq 0x89 -and $ib[1] -eq 0x50 -and $ib[2] -eq 0x4E -and $ib[3] -eq 0x47) { Pass "PNG Magic Bytes korrekt" }
            else { Warn "PNG Magic Bytes nicht erkannt" }
        } else { Fail "Foto-Download fehlgeschlagen" }
    } else { Warn "Keine Fotos - Download-Test uebersprungen" }
    Step 3 4 "GET /api/photos/99999 - 404 erwartet..."
    try { Invoke-WebRequest -Uri "$BASE/api/photos/99999" -Method GET -UseBasicParsing -ErrorAction Stop | Out-Null; Fail "Kein 404" }
    catch { if ($_.Exception.Response.StatusCode.value__ -eq 404) { Pass "HTTP 404 korrekt" } else { Fail "Erwartet 404, bekam $($_.Exception.Response.StatusCode.value__)" } }
    Step 4 4 "GET /api/photos/abc - 400 erwartet..."
    try { Invoke-WebRequest -Uri "$BASE/api/photos/abc" -Method GET -UseBasicParsing -ErrorAction Stop | Out-Null; Fail "Kein 400" }
    catch { if ($_.Exception.Response.StatusCode.value__ -eq 400) { Pass "HTTP 400 korrekt" } else { Fail "Erwartet 400, bekam $($_.Exception.Response.StatusCode.value__)" } }
}
# ------ SCHRITT 6: Browser ------
Sep "SCHRITT 6: Galerie oeffnen"
if ($NoBrowser) { Warn "Browser uebersprungen (-NoBrowser)" }
elseif ($apiUp) { Start-Process "$BASE/"; Pass "Browser geoeffnet: $BASE/" }
else {
    Write-Host "  API nicht mehr aktiv. Galerie nachtraeglich:" -ForegroundColor Yellow
    Write-Host "    1.  mvn exec:java -Dexec.mainClass=ocean.Main" -ForegroundColor White
    Write-Host "    2.  .\test-photo-api.ps1" -ForegroundColor White
    Write-Host "    3.  Browser: $BASE/" -ForegroundColor White
    Warn "Browser nicht geoeffnet"
}
# ------ ERGEBNIS ------
Sep "GESAMTERGEBNIS"
Write-Host "  Gesamt:         $($passed+$failed+$warned)" -ForegroundColor White
Write-Host "  Bestanden:      $passed" -ForegroundColor Green
if ($warned -gt 0) { Write-Host "  Warnungen:      $warned" -ForegroundColor Yellow }
if ($failed -gt 0) { Write-Host "  Fehlgeschlagen: $failed" -ForegroundColor Red }
Write-Host ""
if     ($failed -eq 0 -and $warned -eq 0) { Write-Host "  ALLES GRUEN - alles funktioniert!" -ForegroundColor Green }
elseif ($failed -eq 0)                    { Write-Host "  Keine Fehler ($warned Warnungen)" -ForegroundColor Yellow }
else {
    Write-Host "  $failed Fehler aufgetreten" -ForegroundColor Red
    Write-Host "  DB zuruecksetzen:  docker compose down -v ; docker compose up -d" -ForegroundColor Yellow
    Write-Host "  Logs pruefen:      mvn exec:java -Dexec.mainClass=ocean.Main" -ForegroundColor Yellow
}
Write-Host ""
Write-Host "  Galerie:  $BASE/"            -ForegroundColor Cyan
Write-Host "  JSON-API: $BASE/api/photos"  -ForegroundColor Cyan
Write-Host ""
exit $failed
