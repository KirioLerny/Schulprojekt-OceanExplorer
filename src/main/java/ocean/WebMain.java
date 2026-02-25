package ocean;

import ocean.api.ControlApiServer;
import ocean.data.DatabaseConnection;
import ocean.data.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * Einstiegspunkt fuer die Web-gesteuerte Ocean-Explorer-Variante.
 *
 * <pre>
 * Startet keine automatische Navigation. Stattdessen wird ein REST-Server
 * auf Port 8080 gestartet, ueber den die Angular WebApp alle Operationen
 * steuert.
 *
 * Aufruf:
 *   java -cp target/OceanExplorer-1.0-SNAPSHOT-jar-with-dependencies.jar ocean.WebMain [oceanHost]
 *
 * Voraussetzungen:
 *   - MySQL laeuft  (docker-compose up -d)
 *   - OceanServer laeuft (oceanserver.jar)
 * </pre>
 */
public class WebMain {

    private static final Logger logger = LoggerFactory.getLogger(WebMain.class);

    private static final String DEFAULT_OCEAN_HOST = "localhost";
    private static final int    API_PORT           = 8080;

    public static void main(String[] args) {
        logger.info("Ocean Explorer - Web-Modus");
        logger.info("REST API  -> http://localhost:{}", API_PORT);
        logger.info("Angular   -> http://localhost:4200  (ng serve)");

        String oceanHost = args.length >= 1 ? args[0] : DEFAULT_OCEAN_HOST;
        logger.info("OceanServer-Host: {}", oceanHost);

        DatabaseConnection db        = null;
        ControlApiServer   controlApi = null;

        try {
            db = DatabaseConnection.getInstance();
            db.connect();
            logger.info("Datenbank verbunden");

            ShipRepository      shipRepo  = new ShipRepository(db);
            ScanRepository      scanRepo  = new ScanRepository(db);
            SubmarineRepository subRepo   = new SubmarineRepository(db);
            PhotoRepository     photoRepo = new PhotoRepository(db);

            controlApi = new ControlApiServer(shipRepo, scanRepo, subRepo, photoRepo, oceanHost, API_PORT);
            controlApi.start();

            logger.info("API bereit - warte auf Anfragen...");
            logger.info("Schiff starten: POST http://localhost:{}/api/ship/launch", API_PORT);
            logger.info("Fotos:          GET  http://localhost:{}/api/photos",      API_PORT);
            logger.info("Status:         GET  http://localhost:{}/api/status",      API_PORT);
            logger.info("Druecke Ctrl+C zum Beenden.");

            final ControlApiServer finalApi = controlApi;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown...");
                finalApi.stop();
                DatabaseConnection.getInstance().disconnect();
            }));

            Thread.currentThread().join();

        } catch (SQLException e) {
            logger.error("Datenbankfehler: {}", e.getMessage());
            logger.error("Laeuft MySQL? -> docker-compose up -d");
            System.exit(1);
        } catch (InterruptedException ignored) {
            logger.info("Unterbrochen - beende.");
        } catch (Exception e) {
            logger.error("Unerwarteter Fehler: {}", e.getMessage(), e);
            System.exit(1);
        } finally {
            if (controlApi != null) try { controlApi.stop(); } catch (Exception ignored) {}
            if (db != null) db.disconnect();
        }
    }
}
