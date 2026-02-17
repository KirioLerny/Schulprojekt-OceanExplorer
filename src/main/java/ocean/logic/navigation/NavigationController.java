package ocean.logic.navigation;

import ocean.communication.oceanserver.OceanClient;
import ocean.data.repository.ScanRepository;
import ocean.data.repository.ShipRepository;
import ocean.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Controller für die automatische Navigation eines Schiffs.
 *
 * Diese Klasse steuert ein Schiff autonom durch das Meeresgebiet.
 * Sie nutzt Radar zur Kollisionsvermeidung und führt systematisch
 * Scans durch.
 *
 * Strategie:
 * - Gittersuche (systematisches Abfahren von Sektoren)
 * - Hindernissen ausweichen via Radar
 * - Jeden besuchten Sektor scannen
 *
 * @author OceanExplorer Team
 */
public class NavigationController {

    private static final Logger logger = LoggerFactory.getLogger(NavigationController.class);

    /** Client für OceanServer-Kommunikation */
    private final OceanClient client;

    /** Aktuelles Schiff */
    private Ship ship;

    /** Schiffs-ID in der Datenbank */
    private Long shipId;

    /** Besuchte Sektoren (zur Vermeidung von Duplikaten) */
    private final Set<Vec2D> visitedSectors = new HashSet<>();

    /** Kollisionsvermeidung */
    private final CollisionAvoidance collisionAvoidance;

    /** Repository für Schiffsdaten (optional für Phase 3) */
    private final ShipRepository shipRepository;

    /** Repository für Scan-Daten (optional für Phase 3) */
    private final ScanRepository scanRepository;

    /**
     * Erstellt einen neuen NavigationController.
     *
     * @param client OceanClient für Server-Kommunikation
     * @param ship Zu steuerndes Schiff
     */
    public NavigationController(OceanClient client, Ship ship) {
        this(client, ship, null, null);
    }

    /**
     * Erstellt einen neuen NavigationController mit Datenbank-Persistierung.
     *
     * @param client OceanClient für Server-Kommunikation
     * @param ship Zu steuerndes Schiff
     * @param shipRepository Repository für Schiffsdaten (optional)
     * @param scanRepository Repository für Scan-Daten (optional)
     */
    public NavigationController(OceanClient client, Ship ship,
                                ShipRepository shipRepository,
                                ScanRepository scanRepository) {
        this.client = client;
        this.ship = ship;
        this.collisionAvoidance = new CollisionAvoidance();
        this.shipRepository = shipRepository;
        this.scanRepository = scanRepository;

        // Schiffs-ID aus Datenbank laden (falls Repositories vorhanden)
        if (shipRepository != null) {
            this.shipId = shipRepository.getIdByName(ship.getName());
            if (shipId == null) {
                logger.warn("Schiff {} nicht in Datenbank gefunden", ship.getName());
            }
        }
    }

    /**
     * Startet die autonome Navigation.
     *
     * Fährt systematisch ein Gebiet ab und scannt jeden Sektor.
     *
     * @param maxSectors Maximale Anzahl zu besuchender Sektoren
     * @throws IOException bei Kommunikationsfehlern
     */
    public void explore(int maxSectors) throws IOException {
        logger.info("=== Starte autonome Navigation ===");
        logger.info("Ziel: {} Sektoren erkunden", maxSectors);

        int scannedCount = 0;

        while (scannedCount < maxSectors) {
            // Aktuellen Sektor scannen
            if (scanCurrentSector()) {
                scannedCount++;
                logger.info("Fortschritt: {}/{} Sektoren gescannt", scannedCount, maxSectors);
            }

            // Abbruch wenn Ziel erreicht
            if (scannedCount >= maxSectors) {
                break;
            }

            // Zur nächsten Position navigieren
            if (!moveToNextSector()) {
                logger.warn("Navigation blockiert - beende Exploration");
                break;
            }
        }

        logger.info("=== Navigation abgeschlossen ===");
        logger.info("Insgesamt {} Sektoren erforscht", scannedCount);
    }

    /**
     * Scannt den aktuellen Sektor (falls noch nicht besucht).
     *
     * @return true wenn Scan durchgeführt wurde
     * @throws IOException bei Kommunikationsfehlern
     */
    private boolean scanCurrentSector() throws IOException {
        Vec2D currentPos = ship.getPosition();

        // Prüfen ob Sektor bereits besucht
        if (visitedSectors.contains(currentPos)) {
            logger.debug("Sektor {} bereits besucht - überspringe", currentPos);
            return false;
        }

        logger.info("Scanne Sektor {}...", currentPos);

        // Tiefen-Scan durchführen
        ScanResult scanResult = client.scan();
        if (scanResult != null) {
            logger.info("  → Tiefe: {} m, StdDev: {}", scanResult.getAverageDepth(), scanResult.getStandardDeviation());
            visitedSectors.add(currentPos);

            // In Datenbank speichern (Phase 3)
            if (scanRepository != null && shipId != null) {
                scanRepository.saveScan(shipId, currentPos, scanResult);
                logger.debug("Scan in Datenbank gespeichert");
            }

            return true;
        } else {
            logger.error("Scan fehlgeschlagen!");
            return false;
        }
    }

    /**
     * Bewegt das Schiff zum nächsten Sektor.
     *
     * Nutzt Radar zur Kollisionsvermeidung und wählt eine sichere Richtung.
     *
     * @return true wenn Bewegung erfolgreich
     * @throws IOException bei Kommunikationsfehlern
     */
    private boolean moveToNextSector() throws IOException {
        logger.debug("Führe Radar-Scan durch...");
        List<RadarEcho> radarData = client.radar();

        if (radarData.isEmpty()) {
            logger.error("Radar-Scan lieferte keine Daten!");
            return false;
        }

        // Sichere Richtung wählen
        Course course = Course.Forward;
        Rudder rudder = collisionAvoidance.chooseSafeDirection(radarData, ship.getDirection());

        if (rudder == null) {
            logger.warn("Keine sichere Richtung gefunden!");
            return false;
        }

        logger.debug("Navigiere: rudder={}, course={}", rudder, course);

        // Bewegung durchführen
        OceanClient.NavigateResult result = client.navigate(rudder, course);

        if (result == null) {
            logger.error("Navigation fehlgeschlagen!");
            return false;
        }

        // Schiffsposition aktualisieren
        ship = new Ship(ship.getName(), result.position(), result.direction());
        logger.info("Neue Position: {} (Richtung: {})", result.position(), result.direction());

        // Position in Datenbank speichern (Phase 3)
        if (shipRepository != null && scanRepository != null && shipId != null) {
            shipRepository.updatePosition(ship.getName(), result.position(), result.direction());
            scanRepository.savePosition(shipId, result.position(), result.direction());
            logger.debug("Position in Datenbank gespeichert");
        }

        return true;
    }

    /**
     * Gibt die Anzahl besuchter Sektoren zurück.
     *
     * @return Anzahl besuchter Sektoren
     */
    public int getVisitedCount() {
        return visitedSectors.size();
    }

    /**
     * Gibt das aktuelle Schiff zurück.
     *
     * @return Aktuelles Schiff
     */
    public Ship getShip() {
        return ship;
    }
}
