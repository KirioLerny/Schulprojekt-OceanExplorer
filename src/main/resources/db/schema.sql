-- ============================================
-- Ocean Explorer Database Schema
-- SQLite Datenbank-Schema für Phase 3
-- ============================================

-- Tabelle: Ocean (Singleton - nur 1 Zeile)
CREATE TABLE IF NOT EXISTS ocean (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    name TEXT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP
);

-- Tabelle: Sector (Gitter-Zellen des Ozeans)
CREATE TABLE IF NOT EXISTS sector (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    ground_type TEXT NOT NULL,  -- WATER, LAND, REEF
    height INTEGER DEFAULT 0,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(x, y)
);

-- Tabelle: Ship (Schiffe)
CREATE TABLE IF NOT EXISTS ship (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    vehicle_type TEXT NOT NULL,  -- ship, submarine
    current_x INTEGER,
    current_y INTEGER,
    direction_x INTEGER,
    direction_y INTEGER,
    launched_at TEXT DEFAULT CURRENT_TIMESTAMP,
    active INTEGER DEFAULT 1
);

-- Tabelle: ShipPosition (Positions-Historie)
CREATE TABLE IF NOT EXISTS ship_position (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ship_id INTEGER NOT NULL,
    sector_id INTEGER,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    direction_x INTEGER,
    direction_y INTEGER,
    timestamp TEXT DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ship_id) REFERENCES ship(id),
    FOREIGN KEY (sector_id) REFERENCES sector(id)
);

-- Tabelle: ShipScan (Tiefen-Scans)
CREATE TABLE IF NOT EXISTS ship_scan (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ship_id INTEGER NOT NULL,
    sector_id INTEGER,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    average_depth REAL NOT NULL,
    std_deviation REAL NOT NULL,
    timestamp TEXT DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ship_id) REFERENCES ship(id),
    FOREIGN KEY (sector_id) REFERENCES sector(id)
);

-- Tabelle: Submarine (später für Phase 4)
CREATE TABLE IF NOT EXISTS submarine (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    ship_id INTEGER,
    active INTEGER DEFAULT 1,
    launched_at TEXT DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ship_id) REFERENCES ship(id)
);

-- Tabelle: Accident (Unfälle/Kollisionen)
CREATE TABLE IF NOT EXISTS accident (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ship_id INTEGER,
    submarine_id INTEGER,
    sector_id INTEGER,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    description TEXT,
    timestamp TEXT DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ship_id) REFERENCES ship(id),
    FOREIGN KEY (submarine_id) REFERENCES submarine(id),
    FOREIGN KEY (sector_id) REFERENCES sector(id)
);

-- ============================================
-- Indizes für Performance
-- ============================================

CREATE INDEX IF NOT EXISTS idx_sector_coords ON sector(x, y);
CREATE INDEX IF NOT EXISTS idx_ship_position_ship ON ship_position(ship_id);
CREATE INDEX IF NOT EXISTS idx_ship_scan_ship ON ship_scan(ship_id);
CREATE INDEX IF NOT EXISTS idx_ship_scan_sector ON ship_scan(sector_id);
CREATE INDEX IF NOT EXISTS idx_ship_position_timestamp ON ship_position(timestamp);
CREATE INDEX IF NOT EXISTS idx_ship_scan_timestamp ON ship_scan(timestamp);

-- ============================================
-- Initiale Daten
-- ============================================

-- Ocean erstellen (Singleton)
INSERT OR IGNORE INTO ocean (id, name, width, height)
VALUES (1, 'Atlantik', 100, 100);
