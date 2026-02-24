# ============================================================
# PHOTO API TEST: REST-Endpunkte + Galerie pruefen
# Voraussetzung: ShipApp laeuft (PhotoApiServer auf Port 8080)
# Aufruf:
#   .\test-photo-api.ps1
#   .\test-photo-api.ps1 -Port 8080
#   .\test-photo-api.ps1 -SkipDbCheck
# ============================================================
param([int]$Port=8080,[switch]$SkipDbCheck)
$BASE   = "http://localhost:$Port"
$passed = 0
$failed = 0
$warned = 0
function Print-Header($t) { Write-Host ""; Write-Host "============================================" -ForegroundColor DarkCyan; Write-Host "  $t" -ForegroundColor Cyan; Write-Host "============================================" -ForegroundColor DarkCyan }
function Print-Step($n,$tot,$t) { Write-Host ""; Write-Host "[$n/$tot] $t" -ForegroundColor Cyan }
function Pass($m) { Write-Host "  [PASS] $m" -ForegroundColor Green;  $script:passed++ }
function Fail($m) { Write-Host "  [FAIL] $m" -ForegroundColor Red;    $script:failed++ }
function Warn($m) { Write-Host "  [WARN] $m" -ForegroundColor Yellow; $script:warned++ }
function Info($m) { Write-Host "         $m" -ForegroundColor Gray }
function QueryCount($sql) {
    $raw = docker exec -i oceanexplorer-mysql mysql -u root -poceanexplorer_root -N -e $sql 2>&1
    $lines2 = @($raw | Where-Object { $_ -notmatch "Warning" -and "$_".Trim() -ne "" })
    if ($lines2.Count -eq 0) { return 0 }
    $n = 0; [int]::TryParse("$($lines2[0])".Trim(), [ref]$n) | Out-Null; return $n
}
function QueryRows($sql) {
    $raw = docker exec -i oceanexplorer-mysql mysql -u root -poceanexplorer_root -N -e $sql 2>&1
    return @($raw | Where-Object { $_ -notmatch "Warning" -and "$_".Trim() -ne "" })
}
function Invoke-Api($method,$path,[switch]$Raw) {
    try {
        if ($Raw) { return Invoke-WebRequest  -Uri "$BASE$path" -Method $method -UseBasicParsing -ErrorAction Stop }
        else       { return Invoke-RestMethod -Uri "$BASE$path" -Method $method -UseBasicParsing -ErrorAction Stop }
    } catch { return $null }
}
Print-Header "PHOTO API TEST - Ocean Explorer"
Write-Host "  Ziel: $BASE" -ForegroundColor Gray
# ============================================================
# BLOCK 1: Voraussetzungen
# ============================================================
Print-Header "BLOCK 1: Voraussetzungen"
Print-Step 1 5 "PhotoApiServer auf Port $Port erreichbar?"
$ping = Invoke-Api GET "/" -Raw
if ($ping -and $ping.StatusCode -eq 200) {
    Pass "Server antwortet (HTTP $($ping.StatusCode))"
} else {
    Fail "Server nicht erreichbar auf Port $Port"
    Write-Host "  Starten mit: mvn exec:java -Dexec.mainClass=ocean.Main" -ForegroundColor Yellow
    exit 1
}
Print-Step 2 5 "MySQL Container pruefen..."
if ($SkipDbCheck) {
    Warn "DB-Check uebersprungen (-SkipDbCheck)"
} else {
    $mysqlRunning = docker ps --filter "name=oceanexplorer-mysql" --filter "status=running" --format "{{.Names}}" 2>&1
    if ($mysqlRunning -match "oceanexplorer-mysql") { Pass "MySQL Container laeuft" }
    else { Warn "MySQL nicht gefunden - DB-Checks werden uebersprungen"; $SkipDbCheck = $true }
}
Print-Step 3 5 "UNIQUE(x,y,z) Constraint auf submarine_measurement_point..."
if (-not $SkipDbCheck) {
    $cc = QueryCount "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='oceanexplorer' AND TABLE_NAME='submarine_measurement_point' AND CONSTRAINT_TYPE='UNIQUE';"
    if ($cc -gt 0) { Pass "UNIQUE(x,y,z) Constraint vorhanden" }
    else { Fail "UNIQUE(x,y,z) fehlt - docker/init.sql neu einspielen!"; Warn "Doppelte Messpunkte koennen gespeichert werden" }
} else { Warn "DB-Check uebersprungen" }
Print-Step 4 5 "Fotos in Datenbank vorhanden?"
$dbPhotoCount = 0
if (-not $SkipDbCheck) {
    $dbPhotoCount = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_photo;"
    if ($dbPhotoCount -gt 0) {
        Pass "$dbPhotoCount Foto(s) in submarine_photo"
        QueryRows "SELECT p.id, s.name, p.x, p.y, p.z, LENGTH(p.photo_data) AS bytes FROM oceanexplorer.submarine_photo p JOIN oceanexplorer.submarine_dive d ON d.id=p.dive_id JOIN oceanexplorer.submarine s ON s.id=d.submarine_id LIMIT 5;" | ForEach-Object { Info $_ }
    } else { Warn "Keine Fotos in DB - starte erst eine ShipApp mit Submarines" }
} else { Warn "DB-Check uebersprungen" }
Print-Step 5 5 "Content-Type der HTML-Galerie pruefen..."
$galleryResp = Invoke-Api GET "/" -Raw
if ($galleryResp -and $galleryResp.Headers."Content-Type" -match "text/html") { Pass "Content-Type ist text/html" }
else { Fail "Unerwarteter Content-Type: $($galleryResp.Headers.'Content-Type')" }
# ============================================================
# BLOCK 2: REST API Endpunkte
# ============================================================
Print-Header "BLOCK 2: REST API Endpunkte"
Print-Step 1 5 "GET /api/photos (JSON-Liste)"
$allPhotos = Invoke-Api GET "/api/photos"
if ($null -eq $allPhotos) {
    Fail "/api/photos nicht erreichbar oder kein gueltiges JSON"
} elseif ($allPhotos -is [array]) {
    Pass "/api/photos liefert Array ($($allPhotos.Count) Eintraege)"
    if ($allPhotos.Count -gt 0) {
        $first = $allPhotos[0]
        Info "Erstes Foto: id=$($first.id)  submarine=$($first.submarineName)  pos=($($first.x),$($first.y),$($first.z))"
        $req  = @("id","diveId","submarineName","x","y","z","dirX","dirY","dirZ","timestamp")
        $miss = $req | Where-Object { $first.PSObject.Properties.Name -notcontains $_ }
        if ($miss.Count -eq 0) { Pass "Alle Pflichtfelder vorhanden" }
        else { Fail "Fehlende Felder: $($miss -join ',')" }
    } else { Warn "Array leer - noch keine Fotos gespeichert" }
} else { Fail "/api/photos hat unerwartetes Format: $($allPhotos.GetType().Name)" }
Print-Step 2 5 "GET /api/photos/{id} (PNG-Download)"
if ($allPhotos -and $allPhotos.Count -gt 0) {
    $tid      = $allPhotos[0].id
    $photoR   = Invoke-Api GET "/api/photos/$tid" -Raw
    $photoCt  = if ($photoR) { $photoR.Headers."Content-Type" } else { "" }
    if ($photoR -and $photoR.StatusCode -eq 200 -and $photoCt -match "image/png") {
        $photoB = $photoR.Content
        Pass "Foto $tid als image/png ($($photoB.Length) Bytes)"
        if ($photoB.Length -ge 4 -and $photoB[0] -eq 0x89 -and $photoB[1] -eq 0x50 -and $photoB[2] -eq 0x4E -and $photoB[3] -eq 0x47) { Pass "PNG Magic Bytes korrekt (89 50 4E 47)" }
        else { Warn "PNG Magic Bytes nicht erkannt" }
    } else { Fail "HTTP $($photoR.StatusCode) fuer /api/photos/$tid" }
} else { Warn "Kein Foto zum Testen vorhanden - uebersprungen" }
Print-Step 3 5 "GET /api/photos/99999 (404 erwartet)"
try { Invoke-WebRequest -Uri "$BASE/api/photos/99999" -Method GET -UseBasicParsing -ErrorAction Stop | Out-Null; Fail "Erwartet 404, bekam 200" }
catch { $sc = $_.Exception.Response.StatusCode.value__; if ($sc -eq 404) { Pass "HTTP 404 korrekt" } else { Fail "Erwartet 404, bekam $sc" } }
Print-Step 4 5 "GET /api/photos/abc (400 erwartet)"
try { Invoke-WebRequest -Uri "$BASE/api/photos/abc" -Method GET -UseBasicParsing -ErrorAction Stop | Out-Null; Fail "Erwartet 400, bekam 200" }
catch { $sc = $_.Exception.Response.StatusCode.value__; if ($sc -eq 400) { Pass "HTTP 400 korrekt" } else { Fail "Erwartet 400, bekam $sc" } }
Print-Step 5 5 "GET /api/submarines/{id}/photos"
if (-not $SkipDbCheck) {
    $sid = (QueryRows "SELECT id FROM oceanexplorer.submarine LIMIT 1;" | Select-Object -First 1)
    $sidClean = "$sid".Trim()
    if ($sidClean -match "^\d+$") {
        $subP = Invoke-Api GET "/api/submarines/$sidClean/photos"
        if ($null -ne $subP) { Pass "/api/submarines/$sidClean/photos liefert Array ($($subP.Count) Eintraege)" }
        else { Fail "/api/submarines/$sidClean/photos nicht erreichbar" }
    } else { Warn "Kein Submarine in DB - Endpunkt nicht testbar" }
} else { Warn "DB-Check uebersprungen" }
# ============================================================
# BLOCK 3: Galerie HTML-Inhalt
# ============================================================
Print-Header "BLOCK 3: HTML-Galerie Inhalt"
$galleryHtml = if ($galleryResp) { $galleryResp.Content } else { "" }
Print-Step 1 4 "HTML-Struktur der Galerie..."
if ($galleryHtml -match "<!DOCTYPE html>") { Pass "DOCTYPE vorhanden" } else { Fail "DOCTYPE fehlt" }
if ($galleryHtml -match "Submarine Galerie") { Pass "Seitentitel vorhanden" } else { Fail "Seitentitel fehlt" }
Print-Step 2 4 "Galerie-Karten fuer vorhandene Fotos..."
if ($dbPhotoCount -gt 0) {
    $cardCount = ([regex]::Matches($galleryHtml, "class=""card""")).Count
    if ($cardCount -gt 0) { Pass "$cardCount Foto-Karte(n) im HTML gerendert" }
    else { Fail "Keine Foto-Karten im HTML obwohl DB Fotos enthaelt" }
} elseif (-not $SkipDbCheck) {
    if ($galleryHtml -match "class=""empty""") { Pass "Leerzustand korrekt angezeigt" }
    else { Warn "Leerer Zustand nicht dargestellt" }
} else { Warn "Uebersprungen (kein DB-Zugriff)" }
Print-Step 3 4 "Lightbox JavaScript vorhanden..."
if ($galleryHtml -match "function openLightbox") { Pass "openLightbox() Funktion vorhanden" } else { Fail "openLightbox() fehlt" }
if ($galleryHtml -match "ArrowRight") { Pass "Tastaturnavigation (ArrowRight/ArrowLeft) vorhanden" } else { Fail "Tastaturnavigation fehlt" }
Print-Step 4 4 "Bild-URLs in der Galerie..."
if ($dbPhotoCount -gt 0) {
    $imgCount = ([regex]::Matches($galleryHtml, "src=""/api/photos/\d+""")).Count
    if ($imgCount -gt 0) { Pass "$imgCount Bild-URL(s) mit /api/photos/{id} Schema" }
    else { Fail "Keine /api/photos/{id} URLs in img-Tags" }
} else { Warn "Keine Fotos - URL-Pruefung uebersprungen" }
# ============================================================
# BLOCK 4: Datenkonsistenz
# ============================================================
if (-not $SkipDbCheck) {
    Print-Header "BLOCK 4: Datenkonsistenz"
    Print-Step 1 3 "API-Anzahl == DB-Anzahl..."
    $apiCount = if ($allPhotos) { $allPhotos.Count } else { 0 }
    if ($apiCount -eq $dbPhotoCount) { Pass "Anzahl stimmt: API=$apiCount, DB=$dbPhotoCount" }
    else { Fail "Mismatch: API=$apiCount, DB=$dbPhotoCount" }
    Print-Step 2 3 "Duplikat-Messpunkt wird ignoriert (UNIQUE x,y,z)..."
    $cntBefore = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_measurement_point;"
    docker exec -i oceanexplorer-mysql mysql -u root -poceanexplorer_root -N -e "INSERT IGNORE INTO oceanexplorer.submarine_measurement_point (dive_id, x, y, z) SELECT dive_id, x, y, z FROM oceanexplorer.submarine_measurement_point LIMIT 1;" 2>&1 | Out-Null
    $cntAfter  = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_measurement_point;"
    if ($cntBefore -eq 0) { Warn "Keine Messpunkte - UNIQUE-Test nicht moeglich" }
    elseif ($cntBefore -eq $cntAfter) { Pass "Duplikat korrekt ignoriert (UNIQUE greift)" }
    else { Fail "Duplikat wurde eingefuegt! UNIQUE(x,y,z) funktioniert nicht ($cntBefore -> $cntAfter)" }
    Print-Step 3 3 "Tauchgang-Status (alle beendet)..."
    $activeDives = QueryCount "SELECT COUNT(*) FROM oceanexplorer.submarine_dive WHERE status='DIVING';"
    if ($activeDives -eq 0) { Pass "Keine offenen Tauchgaenge" }
    else { Warn "$activeDives Tauchgang/Tauchgaenge noch auf DIVING" }
}
# ============================================================
# ERGEBNIS
# ============================================================
Print-Header "TESTERGEBNIS"
Write-Host "  Gesamt:         $($passed+$failed+$warned)" -ForegroundColor White
Write-Host "  Bestanden:      $passed" -ForegroundColor Green
if ($warned -gt 0) { Write-Host "  Warnungen:      $warned" -ForegroundColor Yellow }
if ($failed -gt 0) { Write-Host "  Fehlgeschlagen: $failed" -ForegroundColor Red }
Write-Host ""
if     ($failed -eq 0 -and $warned -eq 0) { Write-Host "  ALLES GRUEN" -ForegroundColor Green }
elseif ($failed -eq 0)                    { Write-Host "  Keine Fehler ($warned Warnungen)" -ForegroundColor Yellow }
else {
    Write-Host "  $failed Fehler aufgetreten" -ForegroundColor Red
    Write-Host "  ShipApp starten: mvn exec:java -Dexec.mainClass=ocean.Main" -ForegroundColor Yellow
    Write-Host "  Docker starten:  docker compose up -d" -ForegroundColor Yellow
}
Write-Host ""
Write-Host "  Galerie:  $BASE/"           -ForegroundColor Cyan
Write-Host "  JSON-API: $BASE/api/photos" -ForegroundColor Cyan
Write-Host ""
exit $failed
