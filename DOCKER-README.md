# 🐳 Docker + MySQL Setup für Ocean Explorer

## Was ist enthalten?

Dieses Projekt nutzt **Docker** und **MySQL** für die Datenbank-Persistierung:

- **MySQL 8.0** - Produktionsreife relationale Datenbank
- **phpMyAdmin** - Webbasiertes Datenbank-Management-Tool
- **HikariCP** - High-Performance JDBC Connection Pool
- **jOOQ** - Typsichere SQL-Abfragen

---

## 🚀 Schnellstart

### 1. Docker Container starten

```powershell
docker-compose up -d
```

Warte 10-15 Sekunden bis MySQL bereit ist.

### 2. Verbindung testen

```powershell
docker exec -it oceanexplorer-mysql mysql -u oceanapp -poceanpass123 -e "SELECT VERSION();"
```

### 3. Datenbank inspizieren

**phpMyAdmin:** http://localhost:8080
- Benutzer: `oceanapp`
- Passwort: `oceanpass123`

---

## 📊 Services

| Service | Container | Port | Beschreibung |
|---------|-----------|------|--------------|
| **MySQL** | oceanexplorer-mysql | 3306 | Datenbank-Server |
| **phpMyAdmin** | oceanexplorer-phpmyadmin | 8080 | Web-GUI für MySQL |

---

## 🔧 Konfiguration

### Umgebungsvariablen (optional)

Du kannst die Datenbankverbindung über Umgebungsvariablen anpassen:

```powershell
$env:DB_HOST = "localhost"
$env:DB_PORT = "3306"
$env:DB_NAME = "oceanexplorer"
$env:DB_USER = "oceanapp"
$env:DB_PASSWORD = "oceanpass123"
```

### docker-compose.yml

Standard-Konfiguration:
```yaml
services:
  mysql:
    image: mysql:8.0
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: oceanexplorer_root
      MYSQL_DATABASE: oceanexplorer
      MYSQL_USER: oceanapp
      MYSQL_PASSWORD: oceanpass123
```

---

## 📁 Datenbank-Schema

Das Schema wird automatisch beim ersten Start initialisiert:
- Datei: `docker/init.sql`
- Tabellen: ocean, sector, ship, ship_position, ship_scan, submarine, etc.
- Views: v_current_ship_positions, v_ship_scan_stats

---

## 🐳 Docker Befehle

### Container starten
```powershell
docker-compose up -d
```

### Container stoppen
```powershell
docker-compose down
```

### Datenbank komplett löschen (Neustart)
```powershell
docker-compose down -v
docker-compose up -d
```

### Logs anzeigen
```powershell
docker logs -f oceanexplorer-mysql
```

### Status prüfen
```powershell
docker-compose ps
```

### MySQL CLI öffnen
```powershell
docker exec -it oceanexplorer-mysql mysql -u oceanapp -poceanpass123 oceanexplorer
```

---

## 💾 Daten-Persistierung

Daten werden in einem Docker Volume gespeichert:
- Volume: `mysql_data`
- Persistiert auch nach Container-Neustart
- Löschen mit: `docker-compose down -v`

---

## 🔍 Datenbank abfragen

### Via phpMyAdmin
1. Öffne http://localhost:8080
2. Login mit: oceanapp / oceanpass123
3. Wähle Datenbank: oceanexplorer

### Via MySQL CLI
```powershell
docker exec -it oceanexplorer-mysql mysql -u oceanapp -poceanpass123 oceanexplorer
```

```sql
-- Alle Tabellen anzeigen
SHOW TABLES;

-- Tabellenstruktur prüfen
DESCRIBE ship;

-- Daten abfragen
SELECT * FROM ship;
SELECT * FROM ship_scan ORDER BY timestamp DESC LIMIT 10;

-- Views nutzen
SELECT * FROM v_ship_scan_stats;
```

---

## 🛠️ Troubleshooting

### Problem: Docker läuft nicht
**Lösung:** Docker Desktop starten und warten bis grün

### Problem: Port 3306 belegt
**Lösung:** Anderen MySQL stoppen oder Port in docker-compose.yml ändern

### Problem: Container startet nicht
**Lösung:** 
```powershell
docker-compose down -v
docker-compose up -d
docker logs oceanexplorer-mysql
```

### Problem: Verbindung schlägt fehl
**Lösung:** 15 Sekunden warten nach Container-Start

---

## 🎓 Vorteile dieser Lösung

✅ **Produktionsreif**: MySQL ist Enterprise-Grade  
✅ **Isolation**: Datenbank läuft in eigenem Container  
✅ **Connection Pooling**: HikariCP für hohe Performance  
✅ **GUI-Tool**: phpMyAdmin für einfache Verwaltung  
✅ **Portabilität**: Läuft überall wo Docker läuft  
✅ **Skalierbar**: Einfach auf Cloud/Server deployen  
✅ **Versionierung**: docker-compose.yml im Git-Repo  

---

## 📚 Weiterführende Infos

- **MySQL Docs:** https://dev.mysql.com/doc/
- **Docker Compose:** https://docs.docker.com/compose/
- **HikariCP:** https://github.com/brettwooldridge/HikariCP
- **jOOQ:** https://www.jooq.org/

---

**Made with ❤️ for Ocean Explorer Phase 3**
