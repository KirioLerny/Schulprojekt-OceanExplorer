# Ocean Explorer – context.md
Variante: Submarine  
Stack: Java, MySQL, jOOQ, Qt, Angular  

---

# 1. Projektüberblick

Ziel ist die Simulation der Erforschung eines 10x10 km großen Meeresgebiets.

## Sektor-Modell

- Raster: 100 x 100 Sektoren
- Koordinaten: x,y ∈ [0..99]
- Größe pro Sektor: 100m x 100m
- Daten pro Sektor:
  - mittlere Tiefe (int)
  - Standardabweichung (float)
  - hochauflösende 3D-Messpunkte (Submarine)

---

# 2. Systemarchitektur

## Komponenten

- OceanServer (vorgegeben, unveränderlich)
- ShipApp
  - TCP Client → OceanServer
  - TCP Server → Submarines
  - Qt GUI
  - Persistenz in MySQL
- Submarine (externe Java-Anwendung)
- MySQL Datenbank
- Java REST Webservice
- Angular WebApp (nur lesend)

---

# 3. Technologie-Stack (VERBINDLICH)

## Backend
- Java
- TCP Sockets
- JSON
- MySQL
- jOOQ

## Desktop
- Qt (UI)
- Keine Business-Logik im UI

## Web
- Angular
- Java REST API
- jOOQ für Datenbankzugriff

---

# 4. Wichtige Architekturregeln

1. ShipApp ist Client UND Server
2. Jede ShipApp verwendet eigenen Submarine-Port
3. Mehrere Submarines parallel verwalten
4. Thread-sichere Implementierung
5. Saubere Schichten:
   - communication
   - domain
   - persistence
   - ui
6. Alle DB-Operationen über jOOQ
7. Keine Datenverluste bei Fehlern

---

# 5. OceanServer Protokoll (A) – KRITISCH

Kommunikation: JSON über TCP

## Launch

Request:
{
  "cmd":"launch",
  "name":"shipName",
  "typ":"ship",
  "sector":{"vec2":[x,y]},
  "dir":{"vec2":[dx,dy]}
}

Response:
{
  "cmd":"launched",
  "id":"ShipID",
  "abspos":{"vec2":[xm,ym]}
}

ShipID global speichern.

---

## Navigate

Request:
{
  "cmd":"navigate",
  "rudder":"Left|Center|Right",
  "course":"Forward|Backward"
}

Response:
{
  "cmd":"move2d",
  "id":"ShipID",
  "sector":{"vec2":[x,y]},
  "dir":{"vec2":[dx,dy]},
  "abspos":{"vec2":[xm,ym]}
}

WICHTIG:
- Vor jeder Navigation Radar prüfen
- Kein Land
- Kein belegter Sektor

---

## Radar

Request:
{ "cmd":"radar" }

Response:
{
  "cmd":"radarresponse",
  "id":"ShipID",
  "echos":[
    {
      "sector":{"vec2":[x,y]},
      "height":int,
      "ground":"WATER|LAND|NONE"
    }
  ]
}

Regeln:
- height > 0 → nicht befahrbar
- ground NONE → außerhalb

---

## Scan

Request:
{ "cmd":"scan" }

Response:
{
  "cmd":"scanned",
  "id":"ShipID",
  "depth":int,
  "stddev":float
}

Speichern:
- sector_x
- sector_y
- depth
- stddev
- timestamp

---

# 6. Submarine Protokoll (B) – KRITISCH

ShipApp stellt ServerSocket bereit.  
Submarine verbindet sich aktiv.

## Ready

{
  "cmd":"ready",
  "id":"submarineID",
  "pos":{"vec":[x,y,z]},
  "dir":{"vec":[dx,dy,dz]},
  "depth":int,
  "distance":int
}

## Measure

{
  "cmd":"measure",
  "vecs":[[x,y,z], ...]
}

Regel:
- Jeder Punkt nur einmal speichern
- UNIQUE(x,y,z)

---

# 7. Datenbank (MySQL – jOOQ)

## ship
- id (PK)
- name
- active
- created_at

## ship_position
- id
- ship_id (FK)
- sector_x
- sector_y
- dir_x
- dir_y
- abs_x
- abs_y
- timestamp

## sector_scan
- id
- sector_x
- sector_y
- depth
- stddev
- timestamp

## submarine
- id
- ship_id (FK)
- status
- started_at
- ended_at

## submarine_measurement
- id
- submarine_id (FK)
- x
- y
- z
- UNIQUE(x,y,z)

## submarine_photo
- id
- submarine_id (FK)
- x
- y
- z
- dir_x
- dir_y
- dir_z
- image_blob
- timestamp

## accident
- id
- type (SHIP|SUBMARINE)
- sector_x
- sector_y
- x
- y
- z
- timestamp

---

# 8. Kritische Regeln

1. Vor Navigate → Radar auswerten
2. Schiff darf Sektor nicht wechseln während Submarine taucht
3. Mehrere Submarines parallel möglich
4. Thread-Safety bei Socket, Sessions, DB
5. Logging integrieren
