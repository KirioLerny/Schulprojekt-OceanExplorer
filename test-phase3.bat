@echo off
REM ============================================
REM PHASE 3 TEST: Docker + MySQL Datenbank
REM ============================================

echo.
echo ============================================
REM PHASE 3 TEST: Docker + MySQL Datenbank
echo ============================================
echo.

REM Prüfe ob Docker läuft
echo [1/4] Prüfe Docker...
docker ps > nul 2>&1
if errorlevel 1 (
    echo.
    echo ❌ FEHLER: Docker läuft nicht!
    echo.
    echo Bitte starte Docker Desktop:
    echo   - Docker Desktop öffnen
    echo   - Warte bis Docker bereit ist (Tray-Icon grün)
    echo.
    pause
    exit /b 1
)
echo ✅ Docker läuft

echo.
echo [2/4] Starte MySQL Container...
docker-compose ps | findstr oceanexplorer-mysql | findstr Up > nul 2>&1
if errorlevel 1 (
    echo MySQL Container nicht gefunden, starte...
    docker-compose up -d
    echo Warte 15 Sekunden bis MySQL bereit ist...
    timeout /t 15 /nobreak > nul
) else (
    echo ✅ MySQL Container läuft bereits
)

echo.
echo [3/4] Prüfe OceanServer (Port 8150)...
powershell -Command "Test-NetConnection -ComputerName localhost -Port 8150 -InformationLevel Quiet" > nul 2>&1
if errorlevel 1 (
    echo.
    echo ❌ FEHLER: OceanServer läuft nicht!
    echo.
    echo Bitte starte zuerst den OceanServer:
    echo   cd external
    echo   java -jar oceanserver.jar
    echo   ^(GUI öffnet sich^) ^-^> Start klicken
    echo.
    pause
    exit /b 1
)
echo ✅ OceanServer läuft

echo.
echo [4/4] Kompiliere Projekt...
call mvn clean compile -q
if errorlevel 1 (
    echo ❌ Kompilierung fehlgeschlagen!
    pause
    exit /b 1
)
echo ✅ Kompilierung erfolgreich

echo.
echo [5/5] Starte ShipApp mit Phase 3...
echo.
echo ============================================
echo.

call mvn exec:java -Dexec.mainClass="ocean.Main" -q

echo.
echo ============================================
echo.
echo PHASE 3 TEST ABGESCHLOSSEN
echo.
echo 📊 Datenbank prüfen:
echo   - phpMyAdmin: http://localhost:8080
echo   - Benutzer: oceanapp
echo   - Passwort: oceanpass123
echo.
echo 🐳 Docker Befehle:
echo   - docker-compose ps           (Status)
echo   - docker-compose down         (Stoppen)
echo   - docker-compose logs mysql   (Logs)
echo.
echo ============================================
echo.

pause
