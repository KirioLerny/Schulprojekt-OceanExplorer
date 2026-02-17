# 🚀 QUICK-START: Phase 3 mit Docker + MySQL testen

## ✅ VORAUSSETZUNGEN

- Java 21 installiert
- Maven installiert
- **Docker Desktop** installiert und laufend
- OceanServer bereit (`external/oceanserver.jar`)

---

## 📋 ABLAUF (3 Schritte):

### **1. MySQL mit Docker starten**

```powershell
cd C:\Users\DOPAMINKISTE-v2\IdeaProjects\Schulprojekt-OceanExplorer
docker-compose up -d
```

**Warte 10-15 Sekunden** bis MySQL initialisiert ist.

**Prüfe ob MySQL läuft:**
```powershell
docker ps
```
Sollte zeigen: `oceanexplorer-mysql` und `oceanexplorer-phpmyadmin`

---

### **2. OceanServer starten**

```powershell
cd C:\Users\DOPAMINKISTE-v2\IdeaProjects\Schulprojekt-OceanExplorer\external
java -jar oceanserver.jar
```

→ GUI öffnet sich → **"Start"** klicken

---

### **3. Phase 3 Test ausführen**

**Öffne ein neues PowerShell-Fenster:**

```powershell
cd C:\Users\DOPAMINKISTE-v2\IdeaProjects\Schulprojekt-OceanExplorer
.\test-phase3.bat
```

**ODER direkt mit Maven:**

```powershell
cd C:\Users\DOPAMINKISTE-v2\IdeaProjects\Schulprojekt-OceanExplorer
mvn exec:java -Dexec.mainClass="ocean.Main"
```

---

## 📊 ERWARTETE AUSGABE

```
[INFO] === Ocean Explorer - ShipApp gestartet ===
[INFO] Verbinde zu OceanServer:
[INFO]   - Ship Port: 8150
[INFO]   - Submarine Port: 8151

[INFO] === Phase 3: Initialisiere Datenbank ===
[INFO] Verbinde zu MySQL-Datenbank:
[INFO]   Host: localhost:3306
[INFO]   Datenbank: oceanexplorer
[INFO]   Benutzer: oceanapp
[INFO] ✅ Datenbank verbunden (Connection Pool aktiv)
[INFO] ✅ Datenbank-Verbindung erfolgreich getestet

[INFO] Schiff gestartet: Ship[Explorer-1 at (50,50) dir=(0,1)]
[INFO] ✅ Schiff gespeichert: Explorer-1 (ID: 1)

[INFO] Radar-Scan ergab 8 Sektoren:
[INFO]   - RadarEcho[sector=(49,51), ground=WATER, height=0]
[INFO]   - RadarEcho[sector=(50,51), ground=WATER, height=0]
[INFO]   - ...

[INFO] Tiefen-Scan: ScanResult[depth=-2500.0, stdDev=123.45]
[INFO] === Phase 1 Test erfolgreich! ===

[INFO] === Phase 2: Starte autonome Navigation ===
[INFO] === Starte autonome Navigation ===
[INFO] Ziel: 10 Sektoren erkunden

[INFO] Scanne Sektor (50,50)...
[INFO]   → Tiefe: -2500.0 m, StdDev: 123.45
[INFO] Fortschritt: 1/10 Sektoren gescannt

[INFO] Neue Position: (50,51) (Richtung: (0,1))
[INFO] Scanne Sektor (50,51)...
[INFO]   → Tiefe: -2498.0 m, StdDev: 118.32
[INFO] Fortschritt: 2/10 Sektoren gescannt

...

[INFO] Fortschritt: 10/10 Sektoren gescannt
[INFO] === Navigation abgeschlossen ===
[INFO] Insgesamt 10 Sektoren erforscht

[INFO] Finale Position: Ship[Explorer-1 at (50,60) dir=(0,1)]
[INFO] === Phase 2 Test erfolgreich! ===

[INFO] === Phase 3: Datenbank-Statistiken ===
[INFO] Gespeicherte Scans: 10
[INFO] Gespeicherte Positionen: 11
[INFO] === Phase 3 Test erfolgreich! ===

[INFO] Datenbank-Verbindung geschlossen (Connection Pool beendet)
[INFO] === ShipApp beendet ===
```

---

## 🔍 DATENBANK INSPIZIEREN

### Option 1: phpMyAdmin (GUI) - **EMPFOHLEN**

Öffne im Browser: **http://localhost:8080**

- **Server:** mysql
- **Benutzer:** oceanapp
- **Passwort:** oceanpass123

→ Wähle Datenbank `oceanexplorer` → Durchsuche Tabellen

### Option 2: MySQL CLI

```powershell
# In Docker Container einloggen
docker exec -it oceanexplorer-mysql mysql -u oceanapp -poceanpass123 oceanexplorer
```

```sql
-- Alle Schiffe
SELECT * FROM ship;

-- Scan-Historie
SELECT x, y, average_depth, timestamp 
FROM ship_scan 
ORDER BY timestamp;

-- Route (Positions-Historie)
SELECT x, y, direction_x, direction_y, timestamp 
FROM ship_position 
ORDER BY timestamp;

-- Statistiken
SELECT 
    (SELECT COUNT(*) FROM ship) as ships,
    (SELECT COUNT(*) FROM ship_scan) as scans,
    (SELECT COUNT(*) FROM ship_position) as positions,
    (SELECT COUNT(*) FROM sector) as sectors;

-- Views verwenden
SELECT * FROM v_current_ship_positions;
SELECT * FROM v_ship_scan_stats;

-- Verlasse MySQL
exit;
```

### Option 3: MySQL Workbench / DBeaver

- **Host:** localhost
- **Port:** 3306
- **Benutzer:** oceanapp
- **Passwort:** oceanpass123
- **Datenbank:** oceanexplorer

---

## 🐳 DOCKER BEFEHLE

### MySQL starten
```powershell
docker-compose up -d
```

### Status prüfen
```powershell
docker-compose ps
docker logs oceanexplorer-mysql
```

### MySQL stoppen
```powershell
docker-compose down
```

### Datenbank komplett löschen (Neustart)
```powershell
docker-compose down -v
docker-compose up -d
```

### MySQL Logs anzeigen
```powershell
docker logs -f oceanexplorer-mysql
```

---

## ✅ ERFOLG = PHASE 3 ABGESCHLOSSEN!

Wenn du siehst:
```
[INFO] === Phase 3 Test erfolgreich! ===
```

Dann ist **Phase 3 (Datenbank) komplett** und du bist bereit für:
→ **Phase 4: Submarine-Server** (TCP-Server für Submarines)

---

## 🐛 FEHLERSUCHE

### Problem: "Cannot connect to Docker daemon"
```
ERROR: Cannot connect to the Docker daemon
```
**Lösung:** 
- Docker Desktop starten
- Warte bis Docker bereit ist (Tray-Icon grün)

### Problem: "Port 3306 already in use"
```
ERROR: Port 3306 is already allocated
```
**Lösung:** 
```powershell
# Prüfe was Port 3306 nutzt
netstat -ano | findstr :3306
# Stoppe andere MySQL-Instanzen oder ändere Port in docker-compose.yml
```

### Problem: "Port 8150 nicht erreichbar"
```
❌ FEHLER: OceanServer läuft nicht!
```
**Lösung:** OceanServer starten (siehe Schritt 2)

### Problem: "Kompilierung fehlgeschlagen"
```
[ERROR] COMPILATION ERROR
```
**Lösung:** 
```powershell
mvn clean compile
```

### Problem: "Access denied for user 'oceanapp'"
```
[ERROR] Access denied for user 'oceanapp'@'localhost'
```
**Lösung:** 
```powershell
# MySQL Container neu starten
docker-compose down -v
docker-compose up -d
# Warte 15 Sekunden für Initialisierung
```

### Problem: "Connection timed out"
```
[ERROR] Communications link failure
```
**Lösung:** 
```powershell
# Prüfe ob MySQL läuft
docker ps
# Prüfe MySQL Logs
docker logs oceanexplorer-mysql
# Warte 15 Sekunden nach Container-Start
```

---

## 🎉 GESCHAFFT!

Du hast jetzt:
- ✅ Phase 1: OceanServer-Kommunikation
- ✅ Phase 2: Autonome Navigation
- ✅ Phase 3: **Docker + MySQL + HikariCP**

**60% des Projekts fertig!** 🎓

---

## 💡 VORTEILE VON DOCKER + MYSQL

✅ **Produktionsreif**: MySQL ist Enterprise-Grade  
✅ **Isolation**: Datenbank läuft in eigenem Container  
✅ **Connection Pooling**: HikariCP für Performance  
✅ **phpMyAdmin**: Webbasiertes Management-Tool  
✅ **Portabilität**: docker-compose.yml für alle Umgebungen  
✅ **Skalierbar**: Einfach auf mehrere Server verteilen  

---

**WEITER ZU PHASE 4! 🚀**
