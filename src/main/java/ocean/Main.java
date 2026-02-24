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
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /** Standard-Host des OceanServers */
    private static final String OCEAN_SERVER_HOST = "localhost";

    /** OceanServer Ship Port (fest) */
    private static final int OCEAN_SERVER_SHIP_PORT = 8150;

    /** OceanServer Submarine Port (fest) */
    private static final int OCEAN_SERVER_SUBMARINE_PORT = 8151;

    /**
     * Haupteinstiegspunkt der Anwendung.
     *
     * @param args Kommandozeilenargumente (optional: host)
     */
    public static void main(String[] args) {
        logger.info("=== Ocean Explorer - ShipApp gestartet ===");
        logger.info("Verbinde zu OceanServer:");
        logger.info("  - Ship Port: {}", OCEAN_SERVER_SHIP_PORT);
        logger.info("  - Submarine Port: {}", OCEAN_SERVER_SUBMARINE_PORT);

        String host = OCEAN_SERVER_HOST;

        if (args.length >= 1) {
            // Host aus Kommandozeile
            host = args[0];
        }

        // ShipApp starten
        Main app = new Main();
        app.run(host, OCEAN_SERVER_SHIP_PORT);
    }

    /**
     * Führt die Hauptlogik der ShipApp aus.
     *
     * @param host OceanServer-Hostname
     * @param port OceanServer-Port
     */
    public void run(String host, int port) {
        OceanClient client = new OceanClient(host, port);
        DatabaseConnection db = null;
        ShipRepository shipRepo = null;
        ScanRepository scanRepo = null;
        PhotoApiServer photoApi = null;

        try {
            // === PHASE 3: DATENBANK INITIALISIEREN ===
            logger.info("=== Phase 3: Initialisiere Datenbank ===");
            db = DatabaseConnection.getInstance();
            db.connect();

            shipRepo = new ShipRepository(db);
            scanRepo = new ScanRepository(db);
            logger.info("✅ Datenbank bereit");
            logger.info("");

            // === PHOTO API SERVER (REST) ===
            PhotoRepository photoRepo = new PhotoRepository(db);
            photoApi = new PhotoApiServer(photoRepo);
            photoApi.start();
            logger.info("✅ Foto-Galerie: http://localhost:{}/", PhotoApiServer.DEFAULT_PORT);

            // 1. Mit OceanServer verbinden
            client.connect();

            // 2. Schiff erstellen und starten
            // Eindeutiger Name pro Run → kein Namenskonflikt auf dem OceanServer
            String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
            Ship ship = new Ship("Explorer-" + timestamp);
            Vec2D startPosition = new Vec2D(50, 50);  // Mitte des Ozeans
            Vec2D startDirection = new Vec2D(0, 1);   // Richtung Nord

            boolean launched = client.launch(
                ship.getName(),
                startPosition,
                startDirection
            );

            if (!launched) {
                logger.error("Schiff konnte nicht gestartet werden!");
                return;
            }

            // Position im Ship-Objekt aktualisieren
            ship.setPosition(startPosition);
            ship.setDirection(startDirection);

            logger.info("Schiff gestartet: {}", ship);

            // Schiff in Datenbank speichern (Phase 3)
            long shipId = shipRepo.save(ship);
            scanRepo.savePosition(shipId, startPosition, startDirection);

            // 3. Radar-Scan durchführen
            List<RadarEcho> radarEchoes = client.radar();
            logger.info("Radar-Scan ergab {} Sektoren:", radarEchoes.size());
            for (RadarEcho echo : radarEchoes) {
                logger.info("  - {}", echo);
            }

            // 4. Tiefen-Scan durchführen
            var scanResult = client.scan();
            if (scanResult != null) {
                logger.info("Tiefen-Scan: {}", scanResult);
                // Ersten Scan speichern
                scanRepo.saveScan(shipId, startPosition, scanResult);
            }

            logger.info("=== Phase 1 Test erfolgreich! ===");
            logger.info("");

            // === PHASE 2: NAVIGATION ===
            logger.info("=== Phase 2: Starte autonome Navigation ===");

            NavigationController navigator = new NavigationController(client, ship, shipRepo, scanRepo);

            // Erkunde 10 Sektoren
            navigator.explore(10);

            ship = navigator.getShip(); // Aktualisierte Position
            logger.info("Finale Position: {}", ship);
            logger.info("=== Phase 2 Test erfolgreich! ===");
            logger.info("");

            // === PHASE 3: STATISTIKEN ===
            logger.info("=== Phase 3: Datenbank-Statistiken ===");
            var scans = scanRepo.findScansByShip(shipId);
            var positions = scanRepo.findPositionsByShip(shipId);
            logger.info("Gespeicherte Scans: {}", scans.size());
            logger.info("Gespeicherte Positionen: {}", positions.size());
            logger.info("=== Phase 3 Test erfolgreich! ===");
            logger.info("");

            // === PHASE 4: SUBMARINE ===
            logger.info("=== Phase 4: Starte Submarine-Integration ===");

            SubmarineRepository subRepo = new SubmarineRepository(db);

            // 3 Submarines – Server akzeptiert genau diese Anzahl
            final int NUM_SUBMARINES = 3;

            // SubmarineServer starten – akzeptiert genau NUM_SUBMARINES Verbindungen
            SubmarineServer subServer = new SubmarineServer(
                    SubmarineServer.DEFAULT_PORT, subRepo, shipId, NUM_SUBMARINES);
            subServer.start();
            logger.info("SubmarineServer lauscht auf Port {} (max. {} Submarines)",
                    SubmarineServer.DEFAULT_PORT, NUM_SUBMARINES);

            // NavigationController über aktive Tauchgänge informieren (context.md Regel 2)
            navigator.setSubmarineServer(subServer);

            // Kurz warten bis ServerSocket bereit ist
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}

            // Die echte Ship-ID wie der OceanServer sie vergeben hat (z.B. "#1#Explorer-220556")
            String oceanShipId = client.getShipServerId();
            if (oceanShipId == null) {
                logger.warn("Server-ID nicht verfügbar, überspringe Submarine-Start");
                subServer.shutdown();
                return;
            }

            logger.info("Starte {} Submarines für Schiff: {}", NUM_SUBMARINES, oceanShipId);

            int startedCount = 0;
            for (int i = 0; i < NUM_SUBMARINES; i++) {
                // Pause zwischen den Starts damit OceanServer + Submarine JVMs nicht gleichzeitig hochlaufen
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
                    logger.info("✅ Submarine {} von {} gestartet", startedCount, NUM_SUBMARINES);
                } else {
                    logger.warn("⚠️ Submarine {} konnte nicht gestartet werden", i + 1);
                }
            }

            logger.info("✅ {} Submarines gestartet – warte auf alle Tauchgänge (max. 120s)...", startedCount);

            try {
                subServer.waitForAllSessions(120_000);
                // Kurzer Puffer damit letzte DB-Schreibvorgänge abgeschlossen werden
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}

            subServer.shutdown();
            logger.info("=== Phase 4 abgeschlossen ===");

        } catch (IOException e) {
            logger.error("Kommunikationsfehler: {}", e.getMessage());
        } catch (SQLException e) {
            logger.error("Datenbankfehler: {}", e.getMessage());
        } finally {
            // Verbindungen sauber trennen
            client.disconnect();
            if (photoApi != null) {
                photoApi.stop();
            }
            if (db != null) {
                db.disconnect();
            }
            // MySQL-Treiber internen Cleanup-Thread sauber beenden
            // (verhindert die "[WARNING] thread mysql-cj-abandoned-connection-cleanup will linger" Warnung)
            try {
                com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.uncheckedShutdown();
            } catch (Exception ignored) {}
        }

        logger.info("=== ShipApp beendet ===");
    }
}


