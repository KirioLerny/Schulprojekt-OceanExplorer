# 🧪 PHASE 3 TESTING - Schritt-für-Schritt Anleitung

## 📋 Was wird in Phase 3 getestet?

Phase 3 fügt **MySQL-Datenbank-Persistierung** hinzu:
- ✅ Speichern von Schiffsdaten
- ✅ Speichern von Positionen
- ✅ Speichern von Scan-Ergebnissen
- ✅ Docker + MySQL Integration

---

## 🚀 SCHNELLSTART (3 Schritte)

### **Schritt 1: MySQL mit Docker starten**

```powershell
cd C:\Users\DOPAMINKISTE-v2\IdeaProjects\Schulprojekt-OceanExplorer
docker compose up -d
```

⏰ **Warte 20 Sekunden** bis MySQL initialisiert ist.

Prüfe ob Container laufen:
```powershell
docker ps
```

Sollte zeigen:
- `oceanexplorer-mysql` (Port 3306) mit Status `healthy`

---

### **Schritt 2: OceanServer starten**

```powershell
cd external
java -jar oceanserver.jar
```

→ GUI öffnet sich → **"Start"** klicken
→ Warte auf: "OceanServer: Waiting on Port 8150 for ship-connections"

---

### **Schritt 3: Phase 3 Test ausführen**

```powershell
.\test-phase3.ps1
```

**ODER mit Maven direkt:**

```powershell
mvn exec:java "-Dexec.mainClass=ocean.Main"
```

---

## ✅ ERWARTETE AUSGABE

```
[INFO] === Ocean Explorer - ShipApp gestartet ===
[INFO] === Phase 3: Initialisiere Datenbank ===
[INFO] ✅ Datenbank verbunden (Connection Pool aktiv)
[INFO] ✅ Datenbank-Verbindung erfolgreich getestet
[INFO] ✅ Datenbank bereit

[INFO] Schiff gestartet: Ship[Explorer-220556 at (50,50) dir=(0,1)]
[INFO] ✅ Schiff gespeichert: Explorer-220556 (ID: 1)

[INFO] === Phase 1 Test erfolgreich! ===
[INFO] === Phase 2: Starte autonome Navigation ===
[INFO] Fortschritt: 10/10 Sektoren gescannt
[INFO] === Phase 2 Test erfolgreich! ===
[INFO] === Phase 3: Datenbank-Statistiken ===
[INFO] Gespeicherte Scans: 11
[INFO] Gespeicherte Positionen: 11
[INFO] === Phase 3 Test erfolgreich! ===
```

---

## 📊 DATENBANK PRÜFEN

### DataGrip / IntelliJ DB Plugin

**Verbindung:**
- Host: `localhost`
- Port: `3306`
- Benutzer: `root`
- Passwort: `oceanexplorer_root`
- Datenbank: `oceanexplorer`

**Oder als App-User:**
- Benutzer: `oceanapp`
- Passwort: `oceanpass123`

---

### Nützliche SQL-Abfragen

```sql
-- Alle Schiffe
SELECT * FROM ship;

-- Alle Positionen (neueste zuerst)
SELECT * FROM ship_position ORDER BY timestamp DESC;

-- Alle Scans (neueste zuerst)
SELECT * FROM ship_scan ORDER BY timestamp DESC;

-- Letzte Position mit Schiffsname
SELECT s.name, sp.x, sp.y, sp.direction_x, sp.direction_y, sp.timestamp
FROM ship s
JOIN ship_position sp ON s.id = sp.ship_id
ORDER BY sp.timestamp DESC
LIMIT 1;

-- Scans mit Sektorinfo
SELECT s.name, sc.x, sc.y, sc.average_depth, sc.std_deviation, sc.timestamp
FROM ship s
JOIN ship_scan sc ON s.id = sc.ship_id
ORDER BY sc.timestamp DESC;

-- Übersicht: Alle Tabellen mit Zeilenanzahl
SELECT table_name, table_rows
FROM information_schema.tables
WHERE table_schema = 'oceanexplorer'
ORDER BY table_name;

-- Alles löschen (Daten, nicht Struktur)
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE submarine_measurement_point;
TRUNCATE TABLE submarine_dive;
TRUNCATE TABLE submarine;
TRUNCATE TABLE ship_scan;
TRUNCATE TABLE ship_position;
TRUNCATE TABLE ship;
TRUNCATE TABLE sector;
TRUNCATE TABLE ocean;
SET FOREIGN_KEY_CHECKS = 1;
```

---

### MySQL CLI (PowerShell)

```powershell
docker exec -it oceanexplorer-mysql mysql -u root -poceanexplorer_root oceanexplorer
```

```sql
SHOW TABLES;
SELECT * FROM ship;
SELECT * FROM ship_position;
SELECT * FROM ship_scan;
SELECT * FROM sector;
```

---

## 🧪 MANUELLE TESTS

### Test 1: Schiff wird gespeichert

```powershell
docker exec -it oceanexplorer-mysql mysql -u root -poceanexplorer_root -e "SELECT id, name, vehicle_type, current_x, current_y, launched_at FROM oceanexplorer.ship;"
```

**Erwartung:**
```
+----+------------------+--------------+-----------+-----------+---------------------+
| id | name             | vehicle_type | current_x | current_y | launched_at         |
+----+------------------+--------------+-----------+-----------+---------------------+
|  1 | Explorer-220556  | ship         |        50 |        59 | 2026-02-23 22:05:56 |
+----+------------------+--------------+-----------+-----------+---------------------+
```

---

### Test 2: Positionen werden gespeichert

```powershell
docker exec -it oceanexplorer-mysql mysql -u root -poceanexplorer_root -e "SELECT ship_id, x, y, direction_x, direction_y, timestamp FROM oceanexplorer.ship_position ORDER BY timestamp;"
```

**Erwartung:**
```
+---------+----+----+-------------+-------------+---------------------+
| ship_id |  x |  y | direction_x | direction_y | timestamp           |
+---------+----+----+-------------+-------------+---------------------+
|       1 | 50 | 50 |           0 |           1 | 2026-02-23 22:05:56 |
|       1 | 50 | 51 |           0 |           1 | 2026-02-23 22:05:57 |
|       1 | 50 | 52 |           0 |           1 | 2026-02-23 22:05:57 |
...
```

---

### Test 3: Scans werden gespeichert

```powershell
docker exec -it oceanexplorer-mysql mysql -u root -poceanexplorer_root -e "SELECT ship_id, sector_id, x, y, average_depth, std_deviation FROM oceanexplorer.ship_scan;"
```

**Erwartung:**
```
+---------+-----------+----+----+---------------+---------------+
| ship_id | sector_id |  x |  y | average_depth | std_deviation |
+---------+-----------+----+----+---------------+---------------+
|       1 |         1 | 50 | 50 |           -53 |     2.9409692 |
|       1 |         2 | 50 | 51 |           -40 |     4.9972790 |
...
```

---

## 🔧 TROUBLESHOOTING

### Problem: Docker Container startet nicht

```powershell
docker ps
docker compose ps
docker compose logs mysql
```

**Neustart:**
```powershell
docker compose down
docker compose up -d
Start-Sleep 20
```

---

### Problem: MySQL Connection Error

**Symptom:**
```
java.sql.SQLNonTransientConnectionException: Public Key Retrieval is not allowed
```

**Lösung:** Bereits konfiguriert in `DatabaseConnection.java`:
```
allowPublicKeyRetrieval=true&useSSL=false
```

---

### Problem: Tabellen existieren nicht

```powershell
docker exec -it oceanexplorer-mysql mysql -u root -poceanexplorer_root -e "SHOW TABLES FROM oceanexplorer;"
```

**Falls leer, manuell initialisieren:**
```powershell
Get-Content docker\init.sql | docker exec -i oceanexplorer-mysql mysql -u root -poceanexplorer_root oceanexplorer
```

---

### Problem: OceanServer antwortet nicht

**Symptom:** App hängt 30 Sekunden, dann:
```
OceanServer hat nach 30 Sekunden NICHT geantwortet!
→ Schiff-Name bereits vergeben? Bitte OceanServer neu starten.
```

**Lösung:**
1. OceanServer GUI geöffnet?
2. **"Stop"** dann **"Start"** klicken
3. Console muss zeigen: "Waiting on Port 8150"

**Port prüfen (PowerShell):**
```powershell
netstat -ano | findstr ":8150"
```

---

### Problem: ShipApp verbindet sich nicht

```powershell
# TCP-Verbindung testen
(New-Object System.Net.Sockets.TcpClient).Connect("localhost", 8150)
```

---

## 🧹 CLEANUP

### Container stoppen:
```powershell
docker compose down
```

### Datenbank-Daten löschen (Struktur bleibt):
```sql
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE submarine_measurement_point;
TRUNCATE TABLE submarine_dive;
TRUNCATE TABLE submarine;
TRUNCATE TABLE ship_scan;
TRUNCATE TABLE ship_position;
TRUNCATE TABLE ship;
TRUNCATE TABLE sector;
TRUNCATE TABLE ocean;
SET FOREIGN_KEY_CHECKS = 1;
```

### Datenbank komplett zurücksetzen (inkl. Volumes):
```powershell
docker compose down -v
docker compose up -d
```

---

## 📈 ERWARTETE ERGEBNISSE

Nach erfolgreichem Test sollten folgende Daten in MySQL sein:

| Tabelle        | Einträge | Beschreibung                |
|----------------|----------|-----------------------------|
| sector         | ~10      | Besuchte Sektoren           |
| ship           | 1        | Explorer-HHMMSS             |
| ship_position  | ~11      | Start + 10 Bewegungen       |
| ship_scan      | ~11      | Initial-Scan + 10 Sektoren  |

---

## 🎯 ERFOLGSKRITERIEN

✅ **Phase 3 ist erfolgreich, wenn:**

1. ✅ Docker Container startet ohne Fehler (`healthy`)
2. ✅ MySQL erreichbar auf Port 3306
3. ✅ OceanServer antwortet auf Kommandos
4. ✅ ShipApp verbindet sich mit OceanServer
5. ✅ ShipApp verbindet sich mit MySQL
6. ✅ Schiff wird in `ship` gespeichert
7. ✅ Positionen werden in `ship_position` aufgezeichnet
8. ✅ Scans werden in `ship_scan` gespeichert
9. ✅ Sektoren werden in `sector` angelegt
10. ✅ Keine Exceptions in der Console
11. ✅ Daten in DataGrip / IntelliJ DB Plugin sichtbar

---

## 📚 NEXT STEPS

Nach erfolgreichem Phase 3 Test:

→ **Phase 4:** Submarine-Integration
- SubmarineServer implementieren
- Submarine-Session-Handling
- 3D-Messpunkte speichern
- Bilder speichern

---

## 💡 TIPPS

- **Immer Docker zuerst starten** (20 Sek warten bis `healthy`)
- **Dann OceanServer** (GUI → Stop → Start)
- **Dann ShipApp** via `.\test-phase3.ps1`
- **DataGrip / IntelliJ DB Plugin** zum Debuggen
- **Logs** in Console aufmerksam lesen
- Bei Problemen: OceanServer Stop → Start

---

**Viel Erfolg! 🚢**
