# 🌊 Ocean Explorer WebApp

Angular-basierte Steuerungsoberfläche für den Ocean Explorer.

## Features

| Tab | Funktion |
|-----|---------|
| **⚓ Steuerung** | Schiff starten / bewegen / scannen / beenden · bis zu 4 Submarines launchen & einziehen |
| **📈 Messdaten** | Live-Chart der Tiefenmessungen · Heatmap des 100×100-Rasters · Scan-Protokoll-Tabelle |
| **🖼 Bildgalerie** | Alle Submarine-Fotos · Filter nach Sub · Lightbox-Vollansicht |

## Voraussetzungen

1. **MySQL** läuft:  `docker-compose up -d`
2. **OceanServer** läuft: `java -jar external/oceanserver.jar`
3. **Java 21** & **Node 18+** installiert

## Starten

### 1. Java-Backend (REST-API auf Port 8080)

```bash
mvn package -DskipTests
java -cp target/OceanExplorer-1.0-SNAPSHOT-jar-with-dependencies.jar ocean.WebMain
```

### 2. Angular-WebApp (Port 4200)

```bash
cd ocean-webapp
ng serve
```

Dann **http://localhost:4200** im Browser öffnen.

---

## REST-API Endpunkte (Port 8080)

### Schiff

| Methode | Pfad | Beschreibung |
|---------|------|-------------|
| `POST` | `/api/ship/launch` | Schiff starten |
| `POST` | `/api/ship/navigate` | Schiff bewegen |
| `POST` | `/api/ship/scan` | Sektor scannen |
| `POST` | `/api/ship/exit` | Schiff beenden |
| `GET`  | `/api/ships` | Alle Schiffe |
| `GET`  | `/api/ships/{name}/positions` | Positionshistorie |
| `GET`  | `/api/ships/{name}/scans` | Scans eines Schiffs |

### Submarine

| Methode | Pfad | Beschreibung |
|---------|------|-------------|
| `POST` | `/api/submarine/launch` | Submarine starten |
| `POST` | `/api/submarine/{id}/exit` | Submarine einziehen |
| `GET`  | `/api/submarines` | Alle Submarines |
| `GET`  | `/api/ships/{name}/submarines` | Submarines eines Schiffs |
| `GET`  | `/api/measurements` | 3D-Messpunkte |

### Fotos

| Methode | Pfad | Beschreibung |
|---------|------|-------------|
| `GET` | `/api/photos` | Alle Foto-Metadaten (JSON) |
| `GET` | `/api/photos/{id}` | Foto als PNG |
| `GET` | `/api/submarines/{id}/photos` | Fotos eines Submarines |

### Misc

| Methode | Pfad | Beschreibung |
|---------|------|-------------|
| `GET` | `/api/scans` | Alle Scan-Daten |
| `GET` | `/api/status` | Server-Status |

---

## Architektur

```
ocean-webapp/
  src/app/
    components/
      control/        ← Schiff & Submarine steuern
      data-view/      ← Charts, Heatmap, Tabelle
      gallery/        ← Bildgalerie mit Lightbox
    services/
      ocean-api.service.ts   ← HTTP-Client zum Backend
    models/
      models.ts              ← TypeScript-Interfaces
```

