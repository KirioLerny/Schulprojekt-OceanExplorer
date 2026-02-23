# ============================================
# PHASE 3 TEST: Docker + MySQL Datenbank
# ============================================

Write-Host ""
Write-Host "============================================"
Write-Host "PHASE 3 TEST: Docker + MySQL Datenbank"
Write-Host "============================================"
Write-Host ""

# Prüfe ob Docker läuft
Write-Host "[1/4] Pruefe Docker..."
$dockerCheck = docker ps 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "FEHLER: Docker laeuft nicht!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Bitte starte Docker Desktop:"
    Write-Host "  - Docker Desktop oeffnen"
    Write-Host "  - Warte bis Docker bereit ist"
    Write-Host ""
    exit 1
}
Write-Host "Docker laeuft" -ForegroundColor Green

Write-Host ""
Write-Host "[2/4] Starte MySQL Container..."
# Korrekter Check: docker ps direkt nach Container-Name filtern
$mysqlStatus = docker ps --filter "name=oceanexplorer-mysql" --filter "status=running" --format "{{.Names}}" 2>&1
if ($mysqlStatus -match "oceanexplorer-mysql") {
    Write-Host "MySQL Container laeuft bereits (healthy)" -ForegroundColor Green
} else {
    Write-Host "MySQL Container nicht gefunden, starte..."
    docker compose up -d 2>&1 | Out-Null
    Write-Host "Warte 20 Sekunden bis MySQL bereit ist..."
    Start-Sleep -Seconds 20
    # Nochmal pruefen
    $mysqlStatus2 = docker ps --filter "name=oceanexplorer-mysql" --filter "status=running" --format "{{.Names}}" 2>&1
    if ($mysqlStatus2 -match "oceanexplorer-mysql") {
        Write-Host "MySQL Container gestartet" -ForegroundColor Green
    } else {
        Write-Host "FEHLER: MySQL Container konnte nicht gestartet werden!" -ForegroundColor Red
        Write-Host "Ausgabe: $mysqlStatus2"
        exit 1
    }
}

Write-Host ""
Write-Host "[3/4] Pruefe OceanServer (Port 8150)..."

function Test-OceanServerReady {
    # Testet ob der OceanServer wirklich auf Kommandos antwortet (nicht nur Port offen)
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $tcp.Connect("localhost", 8150)
        $stream = $tcp.GetStream()
        $stream.ReadTimeout = 3000
        $writer = New-Object System.IO.StreamWriter($stream)
        $reader = New-Object System.IO.StreamReader($stream)
        $writer.AutoFlush = $true

        # Test-Launch mit Dummy-Namen auf freiem Sektor
        $testCmd = '{"name":"__test__","typ":"ship","cmd":"launch","dir":{"vec2":[1,0]},"sector":{"vec2":[99,99]}}'
        $writer.WriteLine($testCmd)

        try {
            $response = $reader.ReadLine()
            # Exit senden damit der Test-Slot wieder frei wird
            $writer.WriteLine('{"cmd":"exit"}')
            $tcp.Close()
            return $true  # Hat geantwortet = Server ist bereit
        } catch {
            $tcp.Close()
            return $false  # Timeout = Server akzeptiert keine Kommandos
        }
    } catch {
        return $false  # Verbindung fehlgeschlagen
    }
}

if (-not (Test-OceanServerReady)) {
    Write-Host ""
    Write-Host "FEHLER: OceanServer akzeptiert keine Kommandos!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Bitte in der OceanServer-GUI:" -ForegroundColor Yellow
    Write-Host "  1. Falls noch nicht gestartet: 'Start' klicken"
    Write-Host "  2. Falls schon gestartet aber haengend: 'Stop' dann 'Start' klicken"
    Write-Host "  3. Der Server muss 'Running' anzeigen"
    Write-Host ""
    Read-Host "Druecke Enter wenn OceanServer bereit ist (gruenes 'Running' in GUI)"

    if (-not (Test-OceanServerReady)) {
        Write-Host "OceanServer antwortet immer noch nicht!" -ForegroundColor Red
        exit 1
    }
}
Write-Host "OceanServer antwortet auf Kommandos" -ForegroundColor Green

Write-Host ""
Write-Host "[4/4] Starte ShipApp mit Datenbank-Integration..."
Write-Host ""
Write-Host "Warte 3 Sekunden vor dem Start..."
Start-Sleep -Seconds 3
Write-Host ""

Set-Location -Path $PSScriptRoot
mvn exec:java "-Dexec.mainClass=ocean.Main"

Write-Host ""
Write-Host "============================================"
Write-Host "Test beendet"
Write-Host "============================================"
Write-Host ""
Write-Host "Datenbank pruefen:"
Write-Host "  - DataGrip / IntelliJ DB Plugin verwenden"
Write-Host "  - Host: localhost:3306"
Write-Host "  - Login: root / oceanexplorer_root  (oder oceanapp / oceanpass123)"
Write-Host "  - Datenbank: oceanexplorer"
Write-Host ""
Write-Host "Docker Container stoppen:"
Write-Host "  docker compose down"
Write-Host ""
