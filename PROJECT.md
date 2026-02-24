# 🌊 Ocean Explorer – Projektübersicht

> **Schulprojekt** | Java 21 | Maven | jOOQ | MySQL | Docker  
> Dieses Dokument dient als zentrale Referenz – auch von einem Laptop/anderen Rechner aus nutzbar.

---

## 📌 Kurzbeschreibung

**Ocean Explorer** ist ein Java-Schulprojekt, das ein autonomes Schiff simuliert, das einen Ozean erkundet.
Das Schiff kommuniziert über TCP/JSON mit einem vorgegebenen **OceanServer**, navigiert autonom, vermeidet Kollisionen und speichert alle Messdaten persistent in einer Datenbank.

---

## 🗂️ Projektstruktur

```
Schulprojekt-OceanExplorer/
├── src/main/java/ocean/
│   ├── Main.java                        # Einstiegspunkt
│   ├── communication/
│   │   └── oceanserver/
│   │       ├── OceanClient.java         # TCP-Verbindung zum OceanServer
│   │       └── CommandFactory.java      # JSON-Befehle bauen
│   ├── data/
│   │   ├── DatabaseConnection.java      # Singleton DB-Verbindung (SQLite/MySQL)
│   │   └── repository/
│   │       ├── ShipRepository.java      # CRUD für Schiffe
│   │       └── ScanRepository.java      # CRUD für Scans & Positionen
│   ├── logic/
│   │   └── navigation/
│   │       ├── NavigationController.java # Autonome Navigation
│   │       └── CollisionAvoidance.java   # Radar-basierte Ausweichlogik
│   ├── model/
│   │   ├── Ship.java, Vec.java, Vec2D.java
│   │   ├── Course.java, Rudder.java
│   │   ├── RadarEcho.java, ScanResult.java
│   │   ├── Ground.java, OceanPicture.java, Route.java
│   │   └── VehicleType.java
│   └── util/
│       └── AppLauncher.java
├── src/main/resources/
│   ├── db/schema.sql                    # Datenbank-Schema
│   └── simplelogger.properties          # Logging-Konfiguration
├── external/
│   ├── oceanserver.jar                  # Vorgegebener OceanServer (GUI)
│   ├── oceanstarter.jar
│   ├── submarine.jar
│   └── oceanserver.conf
├── docker/
│   └── init.sql                         # MySQL Init-Script
├── docker-compose.yml                   # MySQL Container
├── docs/                                # Detaillierte Dokumentation
├── pom.xml                              # Maven Build
└── PROJECT.md                           # Diese Datei
```

---

## 🎯 Projektziele & Phasen

| Phase | Status | Ziel |
|-------|--------|------|
| **Phase 1** | ✅ Fertig | Verbindung, Schiff starten, Radar, Tiefen-Scan |
| **Phase 2** | ✅ Fertig | Autonome Navigation, Kollisionsvermeidung |
| **Phase 3** | ✅ Fertig | Datenbank-Persistierung (SQLite + jOOQ) |
| **Phase 4** | 🔜 TODO | Submarine-Server, Sessions, Route-Pilotierung |
| **Phase 5** | 🔜 TODO | GUI (JavaFX / QtJambi), Karten-Visualisierung |

**Aktueller Fortschritt: ~60 % (3 von 5 Phasen)**

---

## 🏗️ Architektur & Design Patterns

- **Singleton** – `DatabaseConnection` (globale DB-Verbindung)
- **Repository Pattern** – Klare Trennung von Business-Logik und Datenbankzugriff
- **Factory** – `CommandFactory` erzeugt alle JSON-Befehle
- **DTO/Records** – `ScanData`, `PositionData` für Daten-Transfer
- **Layer-Architektur:** `model → data/repository → logic → communication → Main`

---

## 📡 OceanServer-Protokoll (Kurzfassung)

| Befehl | JSON | Antwort |
|--------|------|---------|
| Start Schiff | `{"cmd":"launch","name":"...","typ":"ship","sector":{"vec2":[x,y]},"dir":{"vec2":[dx,dy]}}` | `{"cmd":"launched","id":"..."}` |
| Radar | `{"cmd":"radar"}` | `{"cmd":"radarresponse","echos":[...]}` |
| Tiefen-Scan | `{"cmd":"scan"}` | `{"cmd":"scanned","depth":-2500,"stddev":123.45}` |
| Navigieren | `{"cmd":"navigate","rudder":"Center","course":"Forward"}` | `{"cmd":"move2d","sector":{"vec2":[x,y]},"dir":{"vec2":[dx,dy]}}` |
| Trennen | `{"cmd":"exit"}` | *(keine Antwort)* |

**Ports:** Ship → `8150` | Submarine → `8151`

> ⚠️ Nach dem Connect **kein** `readLine()` aufrufen – der Server sendet keine Config-Nachricht!  
> ⚠️ Feld heißt `"echos"` (nicht `"echoes"`), `"stddev"` (nicht `"deviation"`), `"dir"` (nicht `"direction"`)

Details: [`docs/OCEANSERVER-PROTOKOLL.md`](docs/OCEANSERVER-PROTOKOLL.md)

---

## 🚀 Schnellstart

### Voraussetzungen

- Java 21
- Maven
- Docker Desktop (für MySQL – optional, SQLite läuft ohne Docker)

### 1. OceanServer starten

```powershell
cd external
java -jar oceanserver.jar
```

GUI öffnet sich → **"Start"** klicken  
→ Ship Server läuft auf Port **8150**

### 2. Projekt bauen

```powershell
cd C:\Users\DOPAMINKISTE-v2\IdeaProjects\Schulprojekt-OceanExplorer
mvn clean package -q
```

### 3. Anwendung starten

```powershell
mvn exec:java -Dexec.mainClass="ocean.Main"
```

Oder die vorgefertigten Testskripte nutzen:

```powershell
.\test-phase3.bat     # Phase 3 (inkl. DB)
.\test-phase3.ps1     # Phase 3 (PowerShell)
.\test-phase4.ps1     # Phase 4
.\test-navigation.bat # Navigation
```

### 4. MySQL via Docker (optional)

```powershell
docker-compose up -d
# Warte ~15 Sekunden
docker ps   # oceanexplorer-mysql sollte laufen
```

Details: [`DOCKER-README.md`](DOCKER-README.md)

---

## 🗄️ Datenbank

**Standard:** SQLite (kein Docker nötig, Datei `oceanexplorer.db` im Projekt-Root)

### Schema-Übersicht

| Tabelle | Beschreibung |
|---------|--------------|
| `ocean` | Ozean-Metadaten (Singleton, id=1) |
| `sector` | Gitter-Zellen (x, y, ground_type, height) |
| `ship` | Schiffsdaten (Name, Typ, aktuelle Position) |
| `ship_position` | Komplette Positions-Historie |
| `ship_scan` | Tiefen-Scan-Messungen pro Sektor |
| `submarine` | Vorbereitet für Phase 4 |
| `submarine_dive` | Tauchgänge |
| `submarine_measurement_point` | 3D-Messpunkte |
| `submarine_photo` | Fotos als BLOB |
| `accident` | Kollisionen/Unfälle |

### Nützliche SQL-Abfragen

```sql
-- Alle Schiffe
SELECT * FROM ship;

-- Scan-Historie
SELECT x, y, average_depth, std_deviation, timestamp
FROM ship_scan ORDER BY timestamp;

-- Positions-Route
SELECT x, y, direction_x, direction_y, timestamp
FROM ship_position ORDER BY timestamp;

-- Statistiken
SELECT
    (SELECT COUNT(*) FROM ship)          AS ships,
    (SELECT COUNT(*) FROM ship_scan)     AS scans,
    (SELECT COUNT(*) FROM ship_position) AS positions,
    (SELECT COUNT(*) FROM sector)        AS sectors;
```

**DB inspizieren:**
- [DB Browser for SQLite](https://sqlitebrowser.org/) (GUI)
- CLI: `sqlite3 oceanexplorer.db`
- IntelliJ: Database-Panel → `+` → SQLite → Datei wählen

---

## 🔧 Technologie-Stack

| Bereich | Technologie |
|---------|-------------|
| Sprache | Java 21 |
| Build | Maven |
| Datenbank (Standard) | SQLite (via JDBC) |
| Datenbank (Optional) | MySQL 8 (Docker) |
| DB-Zugriff | jOOQ 3.19 |
| Connection Pool | HikariCP 5.1 |
| JSON | org.json |
| Logging | SLF4J + slf4j-simple |
| Tests | JUnit 5, Python-Skripte |
| Container | Docker + docker-compose |

---

## 📋 Phase-Details

### Phase 1 – Grundlagen ✅

- TCP-Verbindung zum OceanServer aufbauen
- Schiff starten (`launch`-Befehl)
- Radar-Scan durchführen → 8 umliegende Sektoren
- Tiefen-Scan durchführen → Durchschnittstiefe + Standardabweichung

**Schlüsselklassen:** `OceanClient`, `CommandFactory`, `Ship`, `RadarEcho`, `ScanResult`

---

### Phase 2 – Autonome Navigation ✅

- `NavigationController` steuert das Schiff komplett autonom
- Erkundet systematisch Sektoren (kein Duplikat-Besuch)
- `CollisionAvoidance` analysiert Radar → wählt sichere Richtung
  - Priorität: `Center` > `Left` > `Right`
  - 8 Himmelsrichtungen (N, NE, E, SE, S, SW, W, NW)

**Schlüsselklassen:** `NavigationController`, `CollisionAvoidance`  
Details: [`docs/PHASE2-NAVIGATION.md`](docs/PHASE2-NAVIGATION.md)

---

### Phase 3 – Datenbank-Persistierung ✅

- `DatabaseConnection` (Singleton) verwaltet SQLite-Verbindung + jOOQ DSL
- `ShipRepository` – CRUD für Schiffe
- `ScanRepository` – Scans + Positions-Historie speichern
- `NavigationController` persistiert **jeden Scan und jede Bewegung** automatisch
- Schema wird bei erstem Start automatisch aus `schema.sql` initialisiert

**Schlüsselklassen:** `DatabaseConnection`, `ShipRepository`, `ScanRepository`  
Details: [`docs/PHASE3-DATABASE.md`](docs/PHASE3-DATABASE.md)

---

### Phase 4 – Submarine (TODO) 🔜

- TCP-Server für Submarines (Port 8151)
- Session-Management (mehrere Submarines gleichzeitig)
- Route-Pilotierung: Wegpunkte an Submarine senden
- Messdaten empfangen (3D-Punkte, Fotos)
- Unfälle (`crash`) und Auftauchen (`arise`) verarbeiten
- Alles in Datenbank persistieren

**Submarine-Protokoll (Vorschau):**

```
Submarine → Server: ready, measure, picture, crash, arise
Server → Submarine: pilot (Route mit Aktionen)
```

---

### Phase 5 – GUI (TODO) 🔜

- JavaFX oder QtJambi
- Karten-Visualisierung des Ozeans
- Echtzeit-Tracking von Schiff und Submarine
- Scan-Heatmap / Tiefenkarte
- Submarine-Steuerungspanel

---

## 🧪 Tests

| Skript | Beschreibung |
|--------|--------------|
| `test-phase3.bat` / `.ps1` | Phase 3 (mit DB) |
| `test-phase4.ps1` | Phase 4 |
| `test-navigation.bat` | Nur Navigation |
| `test-grundlagen.bat` / `.sh` | Phase 1 Grundlagen |
| `test-server.py` | Server-Kommunikation (Python) |
| `test-correct.py` | Korrektheitsprüfung |
| `test-format.py` | Format-Validierung |
| `test-variants.py` | Verschiedene Szenarien |

---

## 📚 Dokumentation (in `/docs`)

| Datei | Inhalt |
|-------|--------|
| `OCEANSERVER-PROTOKOLL.md` | Vollständiges TCP/JSON-Protokoll |
| `PHASE2-NAVIGATION.md` | Phase 2 Test-Anleitung |
| `PHASE2-SUMMARY.md` | Phase 2 Zusammenfassung |
| `PHASE3-DATABASE.md` | Phase 3 Datenbank-Details |
| `PHASE3-SUMMARY.md` | Phase 3 Zusammenfassung |
| `PROTOKOLL-KORREKTUREN.md` | Protokoll-Korrekturen / Bugfixes |
| `uml/oceanExplorer_ClassDiagram.puml` | UML Klassendiagramm |
| `uml/oceanExplorerDatabase_erDiagram.puml` | ER-Diagramm |

---

## ⚠️ Wichtige Hinweise

1. **OceanServer muss laufen** bevor die App gestartet wird
2. Nach dem Connect **niemals** sofort `readLine()` aufrufen – der Server sendet keine Willkommensnachricht
3. Jeder TCP-Befehl muss mit `\n` enden
4. Nach jedem gesendeten Befehl **auf Antwort warten**
5. `oceanexplorer.db` liegt im Projekt-Root und wird automatisch erstellt
6. Schema wird bei jedem Start geprüft (`CREATE TABLE IF NOT EXISTS`)

---

## 🐛 Bekannte Limitierungen

- Kein Compile-Zeit-Check für Tabellennamen (kein jOOQ Code-Generator)
- AutoCommit aktiv (keine Transaktionen bei Batch-Inserts)
- SQLite nicht für Prod-Lastszenarien geeignet (für dieses Projekt ausreichend)

---

## 🎓 Noteninformation

**Was beeindruckt Lehrer besonders:**
- ✅ Layer-Architektur (Communication → Logic → Data → Model)
- ✅ Design Patterns (Singleton, Repository, Factory)
- ✅ jOOQ typsichere DB-Abfragen (keine rohen SQL-Strings)
- ✅ Automatisches Schema-Setup
- ✅ Vollständige Persistierung aller Aktionen
- ✅ Positions- und Scan-Historie mit Timestamps
- ✅ JavaDocs für alle Klassen
- ✅ SLF4J Logging
- ✅ Vorbereitung für Phase 4 (submarine-Tabellen vorhanden)
- ✅ ER-Diagramm + UML-Klassendiagramm

---

*Letzte Aktualisierung: Phase 3 abgeschlossen ✅ | Nächste Phase: Submarine-Server*
