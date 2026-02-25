package ocean.logic.navigation;

import ocean.communication.oceanserver.OceanClient;
import ocean.communication.submarine.SubmarineServer;
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
 * Controller fuer die automatische Navigation eines Schiffs.
 *
 * <pre>
 * Steuert ein Schiff autonom durch das Meeresgebiet.
 * Nutzt Radar zur Kollisionsvermeidung und fuehrt systematisch Scans durch.
 *
 * Strategie:
 *   - Gittersuche (systematisches Abfahren von Sektoren)
 *   - Hindernissen ausweichen via Radar
 *   - Jeden besuchten Sektor scannen
 * </pre>
 */
public class NavigationController {

    private static final Logger logger = LoggerFactory.getLogger(NavigationController.class);

    private final OceanClient client;
    private Ship ship;
    private Long shipId;
    private final Set<Vec2D> visitedSectors = new HashSet<>();
    private final CollisionAvoidance collisionAvoidance;
    private final ShipRepository shipRepository;
    private final ScanRepository scanRepository;

    private SubmarineServer submarineServer;

    /**
     * Erstellt einen neuen NavigationController ohne Datenbank-Persistierung.
     *
     * @param client OceanClient fuer Server-Kommunikation
     * @param ship   Zu steuerndes Schiff
     */
    public NavigationController(OceanClient client, Ship ship) {
        this(client, ship, null, null);
    }

    /**
     * Erstellt einen neuen NavigationController mit Datenbank-Persistierung.
     *
     * @param client          OceanClient fuer Server-Kommunikation
     * @param ship            Zu steuerndes Schiff
     * @param shipRepository  Repository fuer Schiffsdaten (optional)
     * @param scanRepository  Repository fuer Scan-Daten (optional)
     */
    public NavigationController(OceanClient client, Ship ship,
                                ShipRepository shipRepository,
                                ScanRepository scanRepository) {
        this.client = client;
        this.ship = ship;
        this.collisionAvoidance = new CollisionAvoidance();
        this.shipRepository = shipRepository;
        this.scanRepository = scanRepository;

        if (shipRepository != null) {
            this.shipId = shipRepository.getIdByName(ship.getName());
            if (shipId == null) {
                logger.warn("Schiff {} nicht in Datenbank gefunden", ship.getName());
            }
        }
    }

    /**
     * Registriert den SubmarineServer.
     * Wenn gesetzt, blockiert {@code navigate()} solange Submarines aktiv tauchen.
     *
     * @param submarineServer SubmarineServer-Instanz
     */
    public void setSubmarineServer(SubmarineServer submarineServer) {
        this.submarineServer = submarineServer;
    }

    /**
     * Startet die autonome Navigation.
     * Faehrt systematisch ein Gebiet ab und scannt jeden Sektor.
     *
     * @param maxSectors Maximale Anzahl zu besuchender Sektoren
     * @throws IOException bei Kommunikationsfehlern
     */
    public void explore(int maxSectors) throws IOException {
        logger.info("=== Starte autonome Navigation ===");
        logger.info("Ziel: {} Sektoren erkunden", maxSectors);

        int scannedCount = 0;

        while (scannedCount < maxSectors) {
            if (scanCurrentSector()) {
                scannedCount++;
                logger.info("Fortschritt: {}/{} Sektoren gescannt", scannedCount, maxSectors);
            }

            if (scannedCount >= maxSectors) {
                break;
            }

            if (!moveToNextSector()) {
                logger.warn("Navigation blockiert - beende Exploration");
                break;
            }
        }

        logger.info("=== Navigation abgeschlossen ===");
        logger.info("Insgesamt {} Sektoren erforscht", scannedCount);
    }

    /**
     * Scannt den aktuellen Sektor, sofern noch nicht besucht.
     *
     * @return {@code true} wenn Scan durchgefuehrt wurde
     * @throws IOException bei Kommunikationsfehlern
     */
    private boolean scanCurrentSector() throws IOException {
        Vec2D currentPos = ship.getPosition();

        if (visitedSectors.contains(currentPos)) {
            logger.debug("Sektor {} bereits besucht - ueberspringe", currentPos);
            return false;
        }

        logger.info("Scanne Sektor {}...", currentPos);

        ScanResult scanResult = client.scan();
        if (scanResult != null) {
            logger.info("  Tiefe: {} m, StdDev: {}", scanResult.getAverageDepth(), scanResult.getStandardDeviation());
            visitedSectors.add(currentPos);

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
     * Bewegt das Schiff zum naechsten Sektor.
     *
     * <pre>
     * Prueft zunaechst ob Submarines tauchen – falls ja, wird gewartet.
     * Nutzt Radar zur Kollisionsvermeidung und waehlt eine sichere Richtung.
     * </pre>
     *
     * @return {@code true} wenn Bewegung erfolgreich
     * @throws IOException bei Kommunikationsfehlern
     */
    private boolean moveToNextSector() throws IOException {
        if (submarineServer != null && submarineServer.isDiving()) {
            logger.info("Navigation blockiert - {} Submarine(s) aktiv - warte...",
                    submarineServer.getActiveSessionCount());
            while (submarineServer.isDiving()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            logger.info("Alle Submarines aufgetaucht - Navigation freigegeben");
        }

        logger.debug("Fuehre Radar-Scan durch...");
        List<RadarEcho> radarData = client.radar();

        if (radarData.isEmpty()) {
            logger.error("Radar-Scan lieferte keine Daten!");
            return false;
        }

        Course course = Course.Forward;
        Rudder rudder = collisionAvoidance.chooseSafeDirection(radarData, ship.getDirection());

        if (rudder == null) {
            logger.warn("Keine sichere Richtung gefunden!");
            return false;
        }

        logger.debug("Navigiere: rudder={}, course={}", rudder, course);

        OceanClient.NavigateResult result = client.navigate(rudder, course);

        if (result == null) {
            logger.error("Navigation fehlgeschlagen!");
            return false;
        }

        ship = new Ship(ship.getName(), result.position(), result.direction());
        logger.info("Neue Position: {} (Richtung: {})", result.position(), result.direction());

        if (shipRepository != null && scanRepository != null && shipId != null) {
            shipRepository.updatePosition(ship.getName(), result.position(), result.direction());
            scanRepository.savePosition(shipId, result.position(), result.direction());
            logger.debug("Position in Datenbank gespeichert");
        }

        return true;
    }

    /**
     * Gibt die Anzahl besuchter Sektoren zurueck.
     *
     * @return Anzahl besuchter Sektoren
     */
    public int getVisitedCount() {
        return visitedSectors.size();
    }

    /**
     * Gibt das aktuelle Schiff zurueck.
     *
     * @return Aktuelles Schiff
     */
    public Ship getShip() {
        return ship;
    }
}
