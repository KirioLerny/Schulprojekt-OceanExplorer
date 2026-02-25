SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE submarine_measurement_point;
TRUNCATE TABLE submarine_photo;
TRUNCATE TABLE submarine_dive;
TRUNCATE TABLE submarine;
TRUNCATE TABLE accident;
TRUNCATE TABLE ship_scan;
TRUNCATE TABLE ship_position;
TRUNCATE TABLE ship;
TRUNCATE TABLE sector;
TRUNCATE TABLE ocean;

SET FOREIGN_KEY_CHECKS = 1;

-- Ocean-Singleton wiederherstellen (wird vom OceanServer benötigt)
INSERT INTO ocean (id, name, width, height)
VALUES (1, 'Atlantik', 100, 100)
ON DUPLICATE KEY UPDATE name = name;










