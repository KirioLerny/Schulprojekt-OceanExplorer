-- ============================================
-- Ocean Explorer Database Schema - MySQL
-- MySQL Datenbank-Schema für Phase 3
-- ============================================

-- Datenbank erstellen (falls nicht vorhanden)
CREATE DATABASE IF NOT EXISTS oceanexplorer
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE oceanexplorer;

-- ============================================
-- TABELLEN
-- ============================================

-- Tabelle: ocean (Singleton - nur 1 Zeile)
CREATE TABLE IF NOT EXISTS ocean (
    id INT PRIMARY KEY CHECK (id = 1),
    name VARCHAR(255) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabelle: sector (Gitter-Zellen des Ozeans)
CREATE TABLE IF NOT EXISTS sector (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    x INT NOT NULL,
    y INT NOT NULL,
    ground_type VARCHAR(50) NOT NULL,  -- WATER, LAND, REEF
    height INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_coords (x, y),
    INDEX idx_coords (x, y)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabelle: ship (Schiffe)
CREATE TABLE IF NOT EXISTS ship (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    vehicle_type VARCHAR(50) NOT NULL,  -- ship, submarine
    current_x INT,
    current_y INT,
    direction_x INT,
    direction_y INT,
    launched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    active TINYINT(1) DEFAULT 1,
    INDEX idx_name (name),
    INDEX idx_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabelle: ship_position (Positions-Historie)
CREATE TABLE IF NOT EXISTS ship_position (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ship_id BIGINT NOT NULL,
    sector_id BIGINT,
    x INT NOT NULL,
    y INT NOT NULL,
    direction_x INT,
    direction_y INT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ship (ship_id),
    INDEX idx_sector (sector_id),
    INDEX idx_timestamp (timestamp),
    FOREIGN KEY (ship_id) REFERENCES ship(id) ON DELETE CASCADE,
    FOREIGN KEY (sector_id) REFERENCES sector(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabelle: ship_scan (Tiefen-Scans)
CREATE TABLE IF NOT EXISTS ship_scan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ship_id BIGINT NOT NULL,
    sector_id BIGINT,
    x INT NOT NULL,
    y INT NOT NULL,
    average_depth DOUBLE NOT NULL,
    std_deviation DOUBLE NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ship (ship_id),
    INDEX idx_sector (sector_id),
    INDEX idx_timestamp (timestamp),
    FOREIGN KEY (ship_id) REFERENCES ship(id) ON DELETE CASCADE,
    FOREIGN KEY (sector_id) REFERENCES sector(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabelle: submarine (später für Phase 4)
CREATE TABLE IF NOT EXISTS submarine (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    ship_id BIGINT,
    active TINYINT(1) DEFAULT 1,
    launched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ship (ship_id),
    INDEX idx_active (active),
    FOREIGN KEY (ship_id) REFERENCES ship(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabelle: submarine_dive (Tauchgänge)
CREATE TABLE IF NOT EXISTS submarine_dive (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submarine_id BIGINT NOT NULL,
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP NULL,
    status VARCHAR(50),
    INDEX idx_submarine (submarine_id),
    FOREIGN KEY (submarine_id) REFERENCES submarine(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabelle: submarine_measurement_point (3D-Messpunkte)
CREATE TABLE IF NOT EXISTS submarine_measurement_point (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dive_id BIGINT NOT NULL,
    sector_id BIGINT,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_dive (dive_id),
    INDEX idx_sector (sector_id),
    FOREIGN KEY (dive_id) REFERENCES submarine_dive(id) ON DELETE CASCADE,
    FOREIGN KEY (sector_id) REFERENCES sector(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabelle: submarine_photo (Fotos als BLOB)
CREATE TABLE IF NOT EXISTS submarine_photo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dive_id BIGINT NOT NULL,
    photo_data LONGBLOB NOT NULL,
    photo_format VARCHAR(10) DEFAULT 'PNG',
    x INT,
    y INT,
    z INT,
    dir_x INT,
    dir_y INT,
    dir_z INT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_dive (dive_id),
    FOREIGN KEY (dive_id) REFERENCES submarine_dive(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabelle: accident (Unfälle/Kollisionen)
CREATE TABLE IF NOT EXISTS accident (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ship_id BIGINT,
    submarine_id BIGINT,
    sector_id BIGINT,
    x INT NOT NULL,
    y INT NOT NULL,
    description TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ship (ship_id),
    INDEX idx_submarine (submarine_id),
    INDEX idx_sector (sector_id),
    INDEX idx_timestamp (timestamp),
    FOREIGN KEY (ship_id) REFERENCES ship(id) ON DELETE SET NULL,
    FOREIGN KEY (submarine_id) REFERENCES submarine(id) ON DELETE SET NULL,
    FOREIGN KEY (sector_id) REFERENCES sector(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- INITIALE DATEN
-- ============================================

-- Ocean erstellen (Singleton)
INSERT INTO ocean (id, name, width, height)
VALUES (1, 'Atlantik', 100, 100)
ON DUPLICATE KEY UPDATE name=name;

-- ============================================
-- VIEWS für Abfragen (optional)
-- ============================================

-- View: Aktuelle Schiffspositionen
CREATE OR REPLACE VIEW v_current_ship_positions AS
SELECT
    s.id,
    s.name,
    s.current_x AS x,
    s.current_y AS y,
    s.direction_x AS dir_x,
    s.direction_y AS dir_y,
    s.launched_at
FROM ship s
WHERE s.active = 1;

-- View: Scan-Statistiken pro Schiff
CREATE OR REPLACE VIEW v_ship_scan_stats AS
SELECT
    s.id AS ship_id,
    s.name AS ship_name,
    COUNT(ss.id) AS total_scans,
    AVG(ss.average_depth) AS avg_depth,
    MIN(ss.average_depth) AS min_depth,
    MAX(ss.average_depth) AS max_depth,
    COUNT(DISTINCT CONCAT(ss.x, ',', ss.y)) AS unique_sectors
FROM ship s
LEFT JOIN ship_scan ss ON s.id = ss.ship_id
WHERE s.active = 1
GROUP BY s.id, s.name;
