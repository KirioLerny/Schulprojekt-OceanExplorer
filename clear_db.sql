-- ============================================
-- ALLE DATEN LÖSCHEN (Reihenfolge = FK-Abhängigkeiten)
-- AUTO_INCREMENT wird ebenfalls zurückgesetzt
-- ============================================

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE submarine_measurement_point;
TRUNCATE TABLE submarine_dive;
TRUNCATE TABLE submarine;
TRUNCATE TABLE ship_scan;
TRUNCATE TABLE ship_position;
TRUNCATE TABLE ship;
TRUNCATE TABLE sector;
TRUNCATE TABLE ocean;

SET FOREIGN_KEY_CHECKS = 1;







