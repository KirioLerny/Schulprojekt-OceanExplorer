package ocean;
import ocean.api.PhotoApiServer;
import ocean.communication.oceanserver.OceanClient;
import ocean.communication.submarine.SubmarineServer;
import ocean.data.DatabaseConnection;
import ocean.data.repository.PhotoRepository;
import ocean.data.repository.ScanRepository;
import ocean.data.repository.ShipRepository;
import ocean.data.repository.SubmarineRepository;
import ocean.logic.navigation.NavigationController;
import ocean.model.Ship;
import ocean.model.Vec2D;
import ocean.model.RadarEcho;
import ocean.util.AppLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Hauptklasse der OceanExplorer ShipApp.
 *
 * <pre>
 * Verbindet sich mit dem OceanServer, startet ein Schiff, navigiert autonom,
 * startet Submarines und persistiert alle Daten in der Datenbank.
 * </pre>
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final String OCEAN_SERVER_HOST          = "localhost";
    private static final int    OCEAN_SERVER_SHIP_PORT      = 8150;
    private static final int    OCEAN_SERVER_SUBMARINE_PORT = 8151;

    /**
     * Haupteinstiegspunkt der Anwendung.
     *
     * @param args Kommandozeilenargumente (optional: host)
     */
    public static void main(String[] args) {
        logger.info("Ocean Explorer - ShipApp gestartet");
        logger.info("Verbinde zu OceanServer:");
        logger.info("  Ship Port: {}", OCEAN_SERVER_SHIP_PORT);
        logger.info("  Submarine Port: {}", OCEAN_SERVER_SUBMARINE_PORT);

        String host = OCEAN_SERVER_HOST;
        if (args.length >= 1) {
            host = args[0];
        }

        Main app = new Main();
        app.run(host, OCEAN_SERVER_SHIP_PORT);
    }

    /**
     * Fuehrt die Hauptlogik der ShipApp aus.
     *
     * @param host OceanServer-Hostname
     * @param port OceanServer-Port
     */
    public void run(String host, int port) {
        OceanClient client = new OceanClient(host, port);
        DatabaseConnection db = null;
        ShipRepository shipRepo;
        ScanRepository scanRepo;
        PhotoApiServer photoApi = null;

        try {
            logger.info("Phase 3: Initialisiere Datenbank");
            db = DatabaseConnection.getInstance();
            db.connect();

            shipRepo = new ShipRepository(db);
            scanRepo = new ScanRepository(db);
            logger.info("Datenbank bereit");

            PhotoRepository photoRepo = new PhotoRepository(db);
            photoApi = new PhotoApiServer(photoRepo);
            photoApi.start();
            logger.info("Foto-Galerie: http://localhost:{}/", PhotoApiServer.DEFAULT_PORT);

            client.connect();

            String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
            Ship ship = new Ship("Explorer-" + timestamp);
            Vec2D startPosition  = new Vec2D(50, 50);
            Vec2D startDirection = new Vec2D(0, 1);

            boolean launched = client.launch(ship.getName(), startPosition, startDirection);

            if (!launched) {
                logger.error("Schiff konnte nicht gestartet werden!");
                return;
            }

            ship.setPosition(startPosition);
            ship.setDirection(startDirection);
            logger.info("Schiff gestartet: {}", ship);

            long shipId = shipRepo.save(ship);
            scanRepo.savePosition(shipId, startPosition, startDirection);

            List<RadarEcho> radarEchoes = client.radar();
            logger.info("Radar-Scan ergab {} Sektoren:", radarEchoes.size());
            for (RadarEcho echo : radarEchoes) {
                logger.info("  - {}", echo);
            }

            var scanResult = client.scan();
            if (scanResult != null) {
                logger.info("Tiefen-Scan: {}", scanResult);
                scanRepo.saveScan(shipId, startPosition, scanResult);
            }

            logger.info("Phase 1 Test erfolgreich!");

            logger.info("Phase 2: Starte autonome Navigation");
            NavigationController navigator = new NavigationController(client, ship, shipRepo, scanRepo);
            navigator.explore(10);
            ship = navigator.getShip();
            logger.info("Finale Position: {}", ship);
            logger.info("Phase 2 Test erfolgreich!");

            logger.info("Phase 3: Datenbank-Statistiken");
            var scans     = scanRepo.findScansByShip(shipId);
            var positions = scanRepo.findPositionsByShip(shipId);
            logger.info("Gespeicherte Scans: {}", scans.size());
            logger.info("Gespeicherte Positionen: {}", positions.size());
            logger.info("Phase 3 Test erfolgreich!");

            logger.info("Phase 4: Starte Submarine-Integration");
            SubmarineRepository subRepo = new SubmarineRepository(db);
            final int NUM_SUBMARINES = 3;

            SubmarineServer subServer = new SubmarineServer(
                    SubmarineServer.DEFAULT_PORT, subRepo, shipId, NUM_SUBMARINES);
            subServer.start();
            logger.info("SubmarineServer lauscht auf Port {} (max. {} Submarines)",
                    SubmarineServer.DEFAULT_PORT, NUM_SUBMARINES);

            navigator.setSubmarineServer(subServer);

            try { Thread.sleep(500); } catch (InterruptedException ignored) {}

            String oceanShipId = client.getShipServerId();
            if (oceanShipId == null) {
                logger.warn("Server-ID nicht verfuegbar, ueberspringe Submarine-Start");
                subServer.shutdown();
                return;
            }

            logger.info("Starte {} Submarines fuer Schiff: {}", NUM_SUBMARINES, oceanShipId);

            int startedCount = 0;
            for (int i = 0; i < NUM_SUBMARINES; i++) {
                if (i > 0) {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                }
                boolean subStarted = AppLauncher.startSubmarine(
                        "external",
                        oceanShipId,
                        "localhost",
                        SubmarineServer.DEFAULT_PORT,
                        OCEAN_SERVER_HOST,
                        OCEAN_SERVER_SUBMARINE_PORT
                );
                if (subStarted) {
                    startedCount++;
                    logger.info("Submarine {} von {} gestartet", startedCount, NUM_SUBMARINES);
                } else {
                    logger.warn("Submarine {} konnte nicht gestartet werden", i + 1);
                }
            }

            logger.info("{} Submarines gestartet - warte auf alle Tauchgaenge (max. 120s)...", startedCount);

            try {
                subServer.waitForAllSessions(120_000);
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}

            subServer.shutdown();
            logger.info("Phase 4 abgeschlossen");

        } catch (IOException e) {
            logger.error("Kommunikationsfehler: {}", e.getMessage());
        } catch (SQLException e) {
            logger.error("Datenbankfehler: {}", e.getMessage());
        } finally {
            client.disconnect();
            if (photoApi != null) {
                photoApi.stop();
            }
            if (db != null) {
                db.disconnect();
            }
            try {
                com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.uncheckedShutdown();
            } catch (Exception ignored) {}
        }

        logger.info("ShipApp beendet");
    }
}
