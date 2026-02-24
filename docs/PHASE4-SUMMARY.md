# 🎉 PHASE 4: SUBMARINE-INTEGRATION

## ✅ WAS WURDE IMPLEMENTIERT

### Neue Klassen erstellt:

1. **`SubmarineServer.java`**
   - Paket: `ocean.communication.submarine`
   - Funktion: TCP-Server, der auf eingehende Submarine-Verbindungen wartet
   - Features:
     - Eigener Daemon-Thread
     - Akzeptiert mehrere Submarine-Verbindungen
     - Für jede Verbindung wird eine `SubmarineSession` gestartet
     - `shutdown()` für sauberes Beenden

2. **`SubmarineSession.java`**
   - Paket: `ocean.communication.submarine`
   - Funktion: Verwaltet Kommunikation mit einem einzelnen Submarine
   - Protokoll (Submarine → ShipApp):
     - `ready` – Submarine bereit, sendet ID; ShipApp antwortet mit Pilot-Route
     - `measure` – 3D-Messpunkte werden empfangen und gespeichert
     - `picture` – PNG-Foto als Hex-String, wird als BLOB gespeichert
     - `crash` – Unfall wird protokolliert, Tauchgang als CRASHED beendet
     - `arise` – Submarine aufgetaucht, Tauchgang als SURFACED beendet
   - Protokoll (ShipApp → Submarine):
     - `pilot` – Piloten-Route mit `dive`, `measure`, `picture`, `arise` Aktionen

3. **`SubmarineRepository.java`**
   - Paket: `ocean.data.repository`
   - Funktion: CRUD-Operationen für Submarine-Daten
   - Features:
     - `saveSubmarine(name, shipId)` – Speichert Submarine-Metadaten
     - `deactivateSubmarine(id)` – Markiert Submarine als inaktiv
     - `startDive(submarineId)` – Startet neuen Tauchgang (Status: DIVING)
     - `endDive(diveId, status)` – Beendet Tauchgang (SURFACED/CRASHED)
     - `saveMeasurementPoint(diveId, x, y, z)` – Speichert einzelnen 3D-Punkt
     - `saveMeasurementPoints(diveId, points)` – Batch-Speicherung
     - `savePhoto(diveId, photoData)` – Speichert Foto als BLOB
     - `saveAccident(shipId, subId, pos, desc)` – Speichert Unfall

### Erweiterte Klassen:

4. **`AppLauncher.java`** (bereits vorhanden, unverändert)
   - `startSubmarine(path, shipId, shipHost, shipPort, oceanHost, oceanPort)` – Startet `submarine.jar` als separaten Prozess

5. **`Main.java`** (Phase 4-Abschnitt ergänzt)
   - SubmarineRepository initialisieren
   - SubmarineServer auf Port 9000 starten
   - OceanServer Ship-ID ermitteln (`client.getShipServerId()`)
   - `submarine.jar` via `AppLauncher.startSubmarine()` starten
   - 60 Sekunden auf Tauchgang warten
   - SubmarineServer sauber beenden

### Neue Datenbank-Tabellen (in `docker/init.sql`):

6. **`submarine_dive`**
   - Verknüpft mit `submarine`
   - Felder: `submarine_id`, `start_time`, `end_time`, `status`
   - Status-Werte: `DIVING`, `SURFACED`, `CRASHED`

7. **`submarine_measurement_point`**
   - 3D-Messpunkte eines Tauchgangs
   - Felder: `dive_id`, `sector_id`, `x`, `y`, `z`, `timestamp`

8. **`submarine_photo`**
   - PNG-Fotos als LONGBLOB
   - Felder: `dive_id`, `photo_data`, `photo_format`, `timestamp`

---

## 📁 NEUE DATEIEN

```
src/main/java/ocean/communication/submarine/
├── SubmarineServer.java     ✅ NEU
└── SubmarineSession.java    ✅ NEU

src/main/java/ocean/data/repository/
└── SubmarineRepository.java ✅ NEU

docker/init.sql              ✅ Phase 4 Tabellen ergänzt
```

---

## 🏗️ ARCHITEKTUR

```
Main
├── OceanClient (Port 8150)         → Schiff navigiert, scannt
├── SubmarineServer (Port 9000)     → Wartet auf Submarine-Verbindungen
│     └── SubmarineSession          → 1 Session pro Submarine
│           ├── handleReady()       → Pilot-Route senden
│           ├── handleMeasure()     → 3D-Punkte speichern
│           ├── handlePicture()     → Foto speichern
│           ├── handleCrash()       → Unfall protokollieren
│           └── handleArise()       → Tauchgang beenden
├── SubmarineRepository             → DB-Persistierung aller Submarine-Daten
└── AppLauncher.startSubmarine()    → submarine.jar starten
```

---

## 🔄 PROTOKOLL-ABLAUF

```
ShipApp                         submarine.jar
   |                                |
   |  [SubmarineServer start]       |
   |  [AppLauncher.startSubmarine]  |
   |                                |
   |  <------ ready ------          |
   |  ------ pilot ------>          |  (dive + measure + picture + arise)
   |                                |
   |  <------ measure ----          |  (3D-Messpunkte)
   |  <------ picture ----          |  (PNG als Hex-String)
   |  <------ arise ------          |  (Tauchgang beendet)
   |                                |
```

---

## 🗄️ DATENBANK-SCHEMA (Phase 4 Erweiterung)

```
submarine
    └─── submarine_dive (1:N)
             ├─── submarine_measurement_point (1:N)
             └─── submarine_photo (1:N)

accident (Unfälle mit Ship und Submarine)
```

---

## 📊 SQL-ABFRAGEN (Phase 4)

```sql
-- Alle Submarines
SELECT * FROM submarine;

-- Alle Tauchgänge
SELECT sd.id, s.name, sd.status, sd.start_time, sd.end_time
FROM submarine_dive sd
         JOIN submarine s ON s.id = sd.submarine_id
ORDER BY sd.start_time DESC;

-- 3D-Messpunkte eines Tauchgangs
SELECT * FROM submarine_measurement_point ORDER BY timestamp DESC;

-- Fotos (nur Metadaten, kein BLOB)
SELECT id, dive_id, photo_format, LENGTH(photo_data) AS groesse_bytes, timestamp
FROM submarine_photo;

-- Unfälle
SELECT * FROM accident;
```

---

## 🚀 STARTEN

```bash
# 1. Docker starten (MySQL)
docker-compose up -d

# 2. OceanServer starten (external/oceanstarter.jar)

# 3. ShipApp starten
mvn compile exec:java -Dexec.mainClass="ocean.Main"
```

Das Submarine (`external/submarine.jar`) wird automatisch von der App gestartet.

---

## ✅ CHECKLISTE

- [x] SubmarineServer TCP-Server
- [x] SubmarineSession Protokoll-Handler
- [x] SubmarineRepository (Dive, Messpunkte, Fotos, Unfälle)
- [x] MySQL-Schema (submarine_dive, submarine_measurement_point, submarine_photo)
- [x] Main.java Phase 4 Integration
- [x] AppLauncher.startSubmarine() Aufruf
- [x] UML-Klassendiagramm aktualisiert
- [x] Pilot-Route: dive → measure → picture → arise
