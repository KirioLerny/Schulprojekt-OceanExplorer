package ocean;

import ocean.communication.oceanserver.OceanServerClient;
import ocean.model.Ship;
import ocean.model.Vec2D;
import ocean.model.RadarEcho;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Hauptklasse der ShipApp - Einstiegspunkt der Anwendung.
 *
 * Die ShipApp ist:
 * - Client des OceanServers (steuert Schiffe)
 * - Server für Submarines (empfängt Messdaten)
 * - Datenspeicher (persistiert Messungen)
 * - GUI-Anwendung (visualisiert Daten)
 *
 * @author OceanExplorer Team
 */
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
        OceanServerClient client = new OceanServerClient(host, port);

        try {
            // 1. Mit OceanServer verbinden
            client.connect();

            // 2. Schiff erstellen und starten
            Ship ship = new Ship("Explorer-1");
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
            }

            // TODO: Hier wird später die GUI gestartet
            // TODO: Hier wird später der SubmarineServer gestartet

            logger.info("=== Phase 1 Test erfolgreich! ===");

        } catch (IOException e) {
            logger.error("Kommunikationsfehler: {}", e.getMessage());
        } finally {
            // Verbindung sauber trennen
            client.disconnect();
        }

        logger.info("=== ShipApp beendet ===");
    }
}

