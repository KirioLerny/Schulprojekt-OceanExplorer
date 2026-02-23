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

```bash
cd /Users/kirio/IdeaProjects/OceanExplorer
docker-compose up -d
```

⏰ **Warte 15 Sekunden** bis MySQL initialisiert ist.

Prüfe ob Container laufen:
```bash
docker ps
```

Sollte zeigen:
- `oceanexplorer-mysql` (Port 3306)
- `oceanexplorer-phpmyadmin` (Port 8080)

---

### **Schritt 2: OceanServer starten**

```bash
cd /Users/kirio/IdeaProjects/OceanExplorer/external
java -jar oceanserver.jar
```

→ GUI öffnet sich → **"Start"** klicken
→ Warte auf: "OceanServer: Waiting on Port 8150 for ship-connections"

---

### **Schritt 3: Phase 3 Test ausführen**

**Terminal 3 (neues Terminal):**

```bash
cd /Users/kirio/IdeaProjects/OceanExplorer
./test-phase3.sh
```

**ODER mit Maven direkt:**

```bash
mvn exec:java -Dexec.mainClass="ocean.Main"
```

---

## ✅ ERWARTETE AUSGABE

### Console Output:

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

[INFO] Verbinde mit OceanServer localhost:8150
[DEBUG] Verbinde mit OceanServer localhost:8150
[DEBUG] Verbindung hergestellt!
[INFO] Verbunden mit OceanServer: localhost:8150

[INFO] Starte Schiff: Explorer-1 at (50,50) dir=(0,1)
>>> Sende: {"cmd":"launch","name":"Explorer-1","type":"ship","sector":{"x":50,"y":50},"dir":{"x":0,"y":1}}
<<< Empfangen: {"cmd":"launched","name":"Explorer-1","type":"ship","sector":{"x":50,"y":50},"dir":{"x":0,"y":1}}
<<< Empfangen: {"cmd":"move2d","sector":{"x":50,"y":50},"dir":{"x":0,"y":1}}
[INFO] Schiff gestartet: Ship[name=Explorer-1, position=(50,50), direction=(0,1)]
[INFO] ✅ Schiff gespeichert: Explorer-1 (ID: 1)

[INFO] === Phase 1: Teste Radar & Scan ===
[INFO] Führe Radar-Scan durch...
>>> Sende: {"cmd":"radar"}
<<< Empfangen: {"cmd":"radarresponse","echos":[...]}
[INFO] Radar-Scan ergab 8 Sektoren
[DEBUG] Radar: 8 Sektoren gescannt

[INFO] Führe Tiefen-Scan durch...
>>> Sende: {"cmd":"scan"}
<<< Empfangen: {"cmd":"scanned","depth":-2500,"stddev":123.45}
[INFO] Tiefen-Scan: depth=-2500, stdDev=123.45

[INFO] === Phase 2: Autonome Navigation ===
[INFO] Starte autonome Navigation
[INFO] Ziel: 10 Sektoren erkunden

[INFO] Bewegung 1: FORWARD/CENTER
>>> Sende: {"cmd":"navigate","rudder":"Center","course":"Forward"}
<<< Empfangen: {"cmd":"move2d","sector":{"x":50,"y":51},"dir":{"x":0,"y":1}}
[DEBUG] Schiff bewegt zu (50,51) mit Richtung (0,1)
[INFO] Position aktualisiert: Explorer-1 -> (50,51)

[INFO] Scanne Sektor (50,51)...
>>> Sende: {"cmd":"scan"}
<<< Empfangen: {"cmd":"scanned","depth":-2498,"stddev":118.32}
[INFO]   → Tiefe: -2498.0 m, StdDev: 118.32
[INFO] Fortschritt: 2/10 Sektoren gescannt

...

[INFO] === Test erfolgreich abgeschlossen! ===
[INFO] Gespeichert:
[INFO]   - 1 Schiff
[INFO]   - 10 Positionen
[INFO]   - 10 Scans
```

---

## 📊 DATENBANK PRÜFEN

### Option 1: PHPMyAdmin (GUI)

```bash
open http://localhost:8080
```

**Login:**
- Server: `db`
- Benutzer: `root`
- Passwort: `oceanexplorer`

**Datenbank:** `oceanexplorer`

**Tabellen prüfen:**
```sql
-- Alle Schiffe
SELECT * FROM Ship;

-- Alle Positionen
SELECT * FROM ShipPosition ORDER BY timestamp DESC;

-- Alle Scans
SELECT * FROM ShipScan ORDER BY timestamp DESC;

-- Letzte Position
SELECT s.name, sp.x, sp.y, sp.dir_x, sp.dir_y, sp.timestamp
FROM Ship s
JOIN ShipPosition sp ON s.id = sp.ship_id
ORDER BY sp.timestamp DESC
LIMIT 1;
```

---

### Option 2: MySQL CLI

```bash
docker exec -it oceanexplorer-mysql mysql -u root -poceanexplorer oceanexplorer
```

```sql
SHOW TABLES;
SELECT * FROM Ship;
SELECT * FROM ShipPosition;
SELECT * FROM ShipScan;
```

---

## 🧪 MANUELLE TESTS

### Test 1: Schiff wird gespeichert

```bash
# Prüfe nach Start der App
docker exec -it oceanexplorer-mysql mysql -u root -poceanexplorer -e "SELECT * FROM oceanexplorer.Ship;"
```

**Erwartung:**
```
+----+------------+--------------+---------------------+
| id | name       | vehicle_type | launched_at         |
+----+------------+--------------+---------------------+
|  1 | Explorer-1 | ship         | 2026-02-20 15:30:00 |
+----+------------+--------------+---------------------+
```

---

### Test 2: Positionen werden gespeichert

```bash
docker exec -it oceanexplorer-mysql mysql -u root -poceanexplorer -e "SELECT ship_id, x, y, dir_x, dir_y FROM oceanexplorer.ShipPosition ORDER BY timestamp;"
```

**Erwartung:**
```
+---------+----+----+-------+-------+
| ship_id | x  | y  | dir_x | dir_y |
+---------+----+----+-------+-------+
|       1 | 50 | 50 |     0 |     1 |
|       1 | 50 | 51 |     0 |     1 |
|       1 | 50 | 52 |     0 |     1 |
...
```

---

### Test 3: Scans werden gespeichert

```bash
docker exec -it oceanexplorer-mysql mysql -u root -poceanexplorer -e "SELECT ship_id, sector_id, depth, std_dev FROM oceanexplorer.ShipScan;"
```

**Erwartung:**
```
+---------+-----------+-------+---------+
| ship_id | sector_id | depth | std_dev |
+---------+-----------+-------+---------+
|       1 |         1 | -2500 |  123.45 |
|       1 |         2 | -2498 |  118.32 |
...
```

---

## 🔧 TROUBLESHOOTING

### Problem: Docker Container startet nicht

**Prüfe Docker:**
```bash
docker ps
docker-compose ps
```

**Logs anschauen:**
```bash
docker-compose logs mysql
```

**Neustart:**
```bash
docker-compose down
docker-compose up -d
sleep 15
```

---

### Problem: MySQL Connection Error

**Symptom:**
```
java.sql.SQLNonTransientConnectionException: Public Key Retrieval is not allowed
```

**Lösung:**
Ist bereits in `DatabaseConnection.java` konfiguriert mit:
```
allowPublicKeyRetrieval=true&useSSL=false
```

Falls Problem besteht, prüfe `pom.xml`:
```xml
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.33</version>
</dependency>
```

---

### Problem: Tabellen existieren nicht

**Prüfe ob Schema initialisiert wurde:**
```bash
docker exec -it oceanexplorer-mysql mysql -u root -poceanexplorer -e "SHOW TABLES FROM oceanexplorer;"
```

**Falls leer, manuell initialisieren:**
```bash
docker exec -i oceanexplorer-mysql mysql -u root -poceanexplorer oceanexplorer < docker/init.sql
```

---

### Problem: OceanServer antwortet nicht

**Symptom:**
```
java.net.ConnectException: Connection refused (Connection refused)
```

**Lösung:**
1. OceanServer GUI geöffnet?
2. **"Start"** Button geklickt?
3. Console zeigt "Waiting on Port 8150"?

**Port prüfen:**
```bash
lsof -i :8150
```

---

### Problem: ShipApp verbindet sich nicht

**Prüfe ob OceanServer tatsächlich lauscht:**
```bash
nc -zv localhost 8150
```

**Sollte zeigen:**
```
Connection to localhost port 8150 [tcp/*] succeeded!
```

**Falls nicht:** OceanServer neu starten und auf "Start" klicken warten.

---

## 🧹 CLEANUP

### Container stoppen:
```bash
docker-compose down
```

### Datenbank zurücksetzen:
```bash
docker-compose down -v  # Löscht auch Volumes (Datenbank-Daten)
docker-compose up -d
```

### Alle Docker-Ressourcen löschen:
```bash
docker-compose down -v --remove-orphans
```

---

## 📈 ERWARTETE ERGEBNISSE

Nach erfolgreichem Test sollten folgende Daten in MySQL sein:

| Tabelle         | Einträge | Beschreibung                    |
|-----------------|----------|---------------------------------|
| Ocean           | 1        | Ozean-Metadaten (Singleton)     |
| Sector          | ~10      | Besuchte Sektoren               |
| Ship            | 1        | Explorer-1                      |
| ShipPosition    | ~11      | Start + 10 Bewegungen           |
| ShipScan        | ~10      | Tiefen-Messungen                |

---

## 🎯 ERFOLGSKRITERIEN

✅ **Phase 3 ist erfolgreich, wenn:**

1. ✅ Docker Container starten ohne Fehler
2. ✅ MySQL erreichbar auf Port 3306
3. ✅ ShipApp verbindet sich mit OceanServer
4. ✅ ShipApp verbindet sich mit MySQL
5. ✅ Tabellen werden automatisch erstellt
6. ✅ Schiff wird in DB gespeichert
7. ✅ Positionen werden aufgezeichnet
8. ✅ Scans werden gespeichert
9. ✅ Keine SQL-Fehler in Console
10. ✅ Daten in PHPMyAdmin sichtbar

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

- **Immer Docker zuerst starten** (15 Sek warten)
- **Dann OceanServer** (GUI Start klicken)
- **Dann ShipApp**
- **PHPMyAdmin** super zum Debuggen
- **Logs** in Console aufmerksam lesen
- Bei Problemen: Container neu starten

---

**Viel Erfolg! 🚢**

