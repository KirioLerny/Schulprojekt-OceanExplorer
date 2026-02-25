package ocean.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import ocean.data.repository.PhotoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST-Endpunkte fuer Submarine-Fotos.
 *
 * <pre>
 * GET  /api/photos                    Liste aller Foto-Metadaten (JSON)
 * GET  /api/photos/{id}               Einzelnes Foto als PNG (image/png)
 * GET  /api/submarines/{id}/photos    Fotos eines bestimmten Submarines
 *
 * Die HTML-Galerie wird von der Angular WebApp bereitgestellt.
 * Laeuft standardmaessig auf Port 8080.
 * </pre>
 */
public class PhotoApiServer {

    private static final Logger logger = LoggerFactory.getLogger(PhotoApiServer.class);

    public static final int DEFAULT_PORT = 8080;

    private final Javalin app;
    private final PhotoRepository photoRepo;
    private final int port;

    public PhotoApiServer(PhotoRepository photoRepo) {
        this(photoRepo, DEFAULT_PORT);
    }

    public PhotoApiServer(PhotoRepository photoRepo, int port) {
        this.photoRepo = photoRepo;
        this.port      = port;
        this.app       = buildApp();
    }

    private Javalin buildApp() {
        Javalin javalin = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));
            config.useVirtualThreads = true;
        });

        javalin.get("/api/photos",                 this::handleGetAllPhotos);
        javalin.get("/api/photos/{id}",            this::handleGetPhotoImage);
        javalin.get("/api/submarines/{id}/photos", this::handleGetSubmarinePhotos);

        return javalin;
    }

    private void handleGetAllPhotos(Context ctx) {
        try {
            ctx.json(photoRepo.findAllMeta());
        } catch (Exception e) {
            logger.error("Fehler beim Abrufen der Fotos: {}", e.getMessage());
            ctx.status(500).result("Datenbankfehler: " + e.getMessage());
        }
    }

    private void handleGetPhotoImage(Context ctx) {
        String idStr = ctx.pathParam("id");
        try {
            long id = Long.parseLong(idStr);
            byte[] data = photoRepo.findPhotoData(id);
            if (data == null) {
                ctx.status(404).result("Foto nicht gefunden: " + id);
                return;
            }
            ctx.contentType("image/png").result(data);
        } catch (NumberFormatException e) {
            ctx.status(400).result("Ungueltige ID: " + idStr);
        } catch (Exception e) {
            logger.error("Fehler beim Abrufen des Fotos {}: {}", idStr, e.getMessage());
            ctx.status(500).result("Datenbankfehler: " + e.getMessage());
        }
    }

    private void handleGetSubmarinePhotos(Context ctx) {
        String idStr = ctx.pathParam("id");
        try {
            long id = Long.parseLong(idStr);
            ctx.json(photoRepo.findBySubmarine(id));
        } catch (NumberFormatException e) {
            ctx.status(400).result("Ungueltige ID: " + idStr);
        } catch (Exception e) {
            logger.error("Fehler: {}", e.getMessage());
            ctx.status(500).result("Datenbankfehler: " + e.getMessage());
        }
    }

    /**
     * Gibt die interne Javalin-Instanz zurueck (fuer Tests).
     *
     * @return Javalin-Instanz
     */
    public Javalin getApp() {
        return app;
    }

    /**
     * Startet den HTTP-Server.
     */
    public void start() {
        app.start(port);
        logger.info("PhotoApiServer gestartet auf http://localhost:{}", port);
    }

    /**
     * Stoppt den HTTP-Server.
     */
    public void stop() {
        app.stop();
        logger.info("PhotoApiServer gestoppt.");
    }
}

