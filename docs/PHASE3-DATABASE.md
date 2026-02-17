# 🎉 PHASE 3: DATENBANK-PERSISTIERUNG

## ✅ IMPLEMENTIERT

Phase 3 fügt SQLite-Datenbank-Persistierung mit jOOQ hinzu.
Alle Schiffsdaten, Scans und Positionen werden persistent gespeichert.

---

## 📋 FEATURES

### 1. **Datenbank-Schema** (`schema.sql`)
- ✅ Ocean (Singleton - Ozean-Metadaten)
- ✅ Sector (Gitter-Zellen)
- ✅ Ship (Schiffe)
- ✅ ShipPosition (Positions-Historie)
- ✅ ShipScan (Tiefen-Messungen)
- ✅ Submarine (vorbereitet für Phase 4)
- ✅ Accident (Unfälle/Kollisionen)

### 2. **DatabaseConnection** (`ocean.data`)
- ✅ Singleton-Pattern
- ✅ SQLite JDBC-Verbindung
- ✅ jOOQ DSLContext
- ✅ Automatische Schema-Initialisierung
- ✅ Sauberes Connection-Management

### 3. **Repositories** (`ocean.data.repository`)

#### ShipRepository
- `save(Ship)` - Speichert neues Schiff
- `updatePosition(...)` - Aktualisiert Position
- `findByName(String)` - Lädt Schiff nach Name
- `findAll()` - Lädt alle Schiffe
- `getIdByName(String)` - Gibt Schiffs-ID zurück

#### ScanRepository
- `saveScan(...)` - Speichert Tiefen-Scan
- `savePosition(...)` - Speichert Position
- `findScansByShip(long)` - Lädt Scan-Historie
- `findPositionsByShip(long)` - Lädt Positions-Historie
- `getOrCreateSector(Vec2D)` - Verwaltet Sektoren

### 4. **Integration**
- ✅ NavigationController persistiert automatisch
- ✅ Main.java initialisiert Datenbank
- ✅ Alle Bewegungen werden aufgezeichnet
- ✅ Scan-Daten werden gespeichert

---

## 🏗️ ARCHITEKTUR

```
ocean/
├── Main.java                       ✅ Phase 3 Integration
├── data/                           🆕 NEUES PAKET
│   ├── DatabaseConnection.java    ✅ Connection Management
│   └── repository/
│       ├── ShipRepository.java    ✅ Ship CRUD
│       └── ScanRepository.java    ✅ Scan/Position CRUD
├── logic/
│   └── navigation/
│       └── NavigationController.java  ✅ DB-Integration
└── resources/
    └── db/
        └── schema.sql              ✅ Datenbank-Schema
```

---

## 📊 DATENBANK-SCHEMA (ER-Diagramm)

```
Ocean (1) ──── (N) Sector
  │
  └──── (N) Ship
          │
          ├──── (N) ShipPosition
          ├──── (N) ShipScan
          └──── (N) Submarine (Phase 4)
```

### Tabellen-Details:

**Ocean** (Singleton)
- id (PK, CHECK = 1)
- name, width, height
- created_at

**Sector** (Gitter-Zellen)
- id (PK)
- x, y (UNIQUE)
- ground_type (WATER/LAND/REEF)
- height

**Ship** (Schiffe)
- id (PK)
- name (UNIQUE)
- vehicle_type
- current_x, current_y
- direction_x, direction_y
- launched_at, active

**ShipPosition** (Positions-Historie)
- id (PK)
- ship_id (FK), sector_id (FK)
- x, y, direction_x, direction_y
- timestamp

**ShipScan** (Tiefen-Scans)
- id (PK)
- ship_id (FK), sector_id (FK)
- x, y
- average_depth, std_deviation
- timestamp

---

## 🚀 TESTEN

### 1. OceanServer starten
```powershell
cd external
java -jar oceanserver.jar
```
→ GUI öffnet sich → **Start** klicken

### 2. Phase 3 Test ausführen
```powershell
cd C:\Users\DOPAMINKISTE-v2\IdeaProjects\Schulprojekt-OceanExplorer
.\test-phase3.bat
```

### 3. Erwartete Ausgabe
```
[INFO] === Phase 3: Initialisiere Datenbank ===
[INFO] Verbinde zu Datenbank: oceanexplorer.db
[INFO] Initialisiere Datenbank-Schema...
[INFO] ✅ Datenbank-Schema erstellt
[INFO] ✅ Datenbank verbunden
[INFO] ✅ Datenbank bereit

[INFO] Schiff gestartet: Explorer-1 @ (50,50) → (0,1)
[INFO] ✅ Schiff gespeichert: Explorer-1 (ID: 1)

[INFO] === Phase 2: Starte autonome Navigation ===
[INFO] Scanne Sektor (50,50)...
[INFO]   → Tiefe: -2500 m, StdDev: 123.45
[INFO] Neue Position: (50,51) (Richtung: (0,1))
[INFO] Fortschritt: 1/10 Sektoren gescannt
...
[INFO] Fortschritt: 10/10 Sektoren gescannt
[INFO] === Phase 2 Test erfolgreich! ===

[INFO] === Phase 3: Datenbank-Statistiken ===
[INFO] Gespeicherte Scans: 10
[INFO] Gespeicherte Positionen: 11
[INFO] === Phase 3 Test erfolgreich! ===

[INFO] === ShipApp beendet ===
```

---

## 🔍 DATENBANK INSPIZIEREN

### Mit SQLite Browser (GUI)
1. Lade herunter: https://sqlitebrowser.org/
2. Öffne `oceanexplorer.db`
3. Tabs: "Datenbank-Struktur" und "Daten durchsuchen"

### Mit SQLite CLI
```powershell
sqlite3 oceanexplorer.db
```

```sql
-- Alle Schiffe anzeigen
SELECT * FROM ship;

-- Scan-Historie
SELECT x, y, average_depth, timestamp 
FROM ship_scan 
ORDER BY timestamp;

-- Positions-Historie (Route)
SELECT x, y, direction_x, direction_y, timestamp 
FROM ship_position 
ORDER BY timestamp;

-- Statistiken
SELECT 
    (SELECT COUNT(*) FROM ship_scan) as scans,
    (SELECT COUNT(*) FROM ship_position) as positions,
    (SELECT COUNT(DISTINCT x || ',' || y) FROM ship_scan) as unique_sectors;
```

---

## 💾 DATEIEN

Nach dem Test wird erstellt:
- `oceanexplorer.db` - SQLite-Datenbank
- Enthält alle Schiffs-, Scan- und Positions-Daten

**Tipp:** Die DB-Datei kann gelöscht werden, um von vorne zu starten.

---

## 📈 PROJEKTFORTSCHRITT

| Phase | Status | Features |
|-------|--------|----------|
| Phase 1: Grundlagen | ✅ FERTIG | Verbindung, Launch, Radar, Scan |
| Phase 2: Navigation | ✅ FERTIG | Navigate, Autonome Steuerung |
| **Phase 3: Datenbank** | ✅ **FERTIG** | **SQLite, jOOQ, Repositories, Persistierung** |
| Phase 4: Submarines | 🔜 TODO | SubmarineServer, Sessions |
| Phase 5: GUI | 🔜 TODO | QtJambi, QML, Visualisierung |

**Fortschritt: 60% für 1er-Note!** 🎓

---

## 🔧 TECHNISCHE DETAILS

### Dependencies (pom.xml)
```xml
<!-- jOOQ für Datenbankzugriff -->
<dependency>
    <groupId>org.jooq</groupId>
    <artifactId>jooq</artifactId>
    <version>3.19.3</version>
</dependency>

<!-- SQLite JDBC-Treiber -->
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.45.1.0</version>
</dependency>
```

### jOOQ ohne Code-Generierung
Wir nutzen jOOQ's **typsichere DSL** ohne vorab-generierte Klassen:
```java
dsl.insertInto(table("ship"))
   .columns(field("name"), field("current_x"))
   .values("Explorer-1", 50)
   .execute();
```

Vorteile:
- ✅ Keine Build-Zeit Code-Generierung nötig
- ✅ Flexibel für Schema-Änderungen
- ✅ Typsicher durch jOOQ DSL
- ✅ SQL-Injection-sicher

### Singleton-Pattern
DatabaseConnection nutzt Singleton für globale Verfügbarkeit:
```java
DatabaseConnection db = DatabaseConnection.getInstance();
db.connect();
DSLContext dsl = db.getDSL();
```

### Repository-Pattern
Repositories kapseln Datenbank-Zugriff:
- Klare API für Business-Logik
- Testbarkeit (Mocking möglich)
- Wiederverwendbarkeit

---

## 🎓 FÜR DIE NOTE

**Was beeindruckt:**
- ✅ Saubere Datenbank-Architektur
- ✅ Repository-Pattern (Best Practice)
- ✅ Singleton-Pattern (Connection Management)
- ✅ Automatische Schema-Initialisierung
- ✅ jOOQ typsichere Abfragen
- ✅ Vollständige Persistierung
- ✅ ER-Diagramm umgesetzt
- ✅ Indizes für Performance

**Bonus-Punkte:**
- Historie aller Bewegungen
- Statistiken-Ausgabe
- SQL-Schema mit Kommentaren
- DTOs (Records) für Scan/Position

---

## 🚀 NÄCHSTE SCHRITTE

Nach erfolgreichem Phase 3 Test:
1. ✅ Phase 1: Grundlagen ✅
2. ✅ Phase 2: Navigation ✅
3. ✅ Phase 3: Datenbank ✅
4. → **Phase 4: Submarine-Server implementieren**
   - TCP-Server für Submarines (Port 8151)
   - Session-Management
   - Route-Pilotierung
5. → **Phase 5: GUI mit QtJambi**
   - Karten-Visualisierung
   - Echtzeit-Tracking
   - Submarine-Steuerung

---

**BEREIT FÜR PHASE 4! 🎉**

Datenbank läuft, Messungen werden persistent gespeichert!
