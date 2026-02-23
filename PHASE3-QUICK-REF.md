# ⚡ PHASE 3 QUICK REFERENCE

## 🚀 START (3 Terminals)

### Terminal 1: MySQL
```bash
cd /Users/kirio/IdeaProjects/OceanExplorer
docker-compose up -d
sleep 15
```

### Terminal 2: OceanServer
```bash
cd /Users/kirio/IdeaProjects/OceanExplorer/external
java -jar oceanserver.jar
# GUI -> Start klicken
```

### Terminal 3: ShipApp
```bash
cd /Users/kirio/IdeaProjects/OceanExplorer
./test-phase3.sh
```

---

## 📊 DATENBANK PRÜFEN

### PHPMyAdmin (Browser)
```bash
open http://localhost:8080
# Login: root / oceanexplorer
```

### MySQL CLI
```bash
docker exec -it oceanexplorer-mysql mysql -u root -poceanexplorer oceanexplorer
```

### Quick Queries
```sql
-- Alle Schiffe
SELECT * FROM Ship;

-- Letzte Position
SELECT s.name, sp.x, sp.y, sp.timestamp
FROM Ship s
JOIN ShipPosition sp ON s.id = sp.ship_id
ORDER BY sp.timestamp DESC LIMIT 1;

-- Alle Scans
SELECT ship_id, depth, std_dev, timestamp FROM ShipScan ORDER BY timestamp;
```

---

## 🧹 CLEANUP

```bash
# Container stoppen
docker-compose down

# Datenbank zurücksetzen
docker-compose down -v && docker-compose up -d
```

---

## 🐛 TROUBLESHOOTING

| Problem | Lösung |
|---------|--------|
| Docker startet nicht | `docker ps` → Docker Desktop öffnen |
| Port 3306 belegt | `docker-compose down && docker-compose up -d` |
| OceanServer antwortet nicht | GUI öffnen → **Start** klicken |
| Port 8150 nicht erreichbar | `lsof -i :8150` → OceanServer prüfen |
| Connection refused | OceanServer neu starten |
| MySQL Connection Error | `docker-compose logs mysql` |
| Tabellen nicht erstellt | `docker exec -i oceanexplorer-mysql mysql -u root -poceanexplorer oceanexplorer < docker/init.sql` |

---

## ✅ SUCCESS CRITERIA

- [x] Docker Container laufen
- [x] OceanServer lauscht auf 8150
- [x] ShipApp verbindet sich
- [x] Schiff wird gespeichert
- [x] Positionen werden aufgezeichnet
- [x] Scans werden gespeichert
- [x] Keine Fehler in Console

---

## 📁 WICHTIGE DATEIEN

```
OceanExplorer/
├── test-phase3.sh              ← Test-Skript (macOS/Linux)
├── test-phase3.bat             ← Test-Skript (Windows)
├── docker-compose.yml          ← MySQL Container Config
├── docker/init.sql             ← Datenbank-Schema
├── PHASE3-TESTING.md           ← Ausführliche Anleitung
└── src/main/java/ocean/
    ├── Main.java               ← Einstiegspunkt
    └── data/
        ├── DatabaseConnection.java
        └── repository/
            ├── ShipRepository.java
            └── ScanRepository.java
```

---

**Hilfe?** → Lies `PHASE3-TESTING.md`

