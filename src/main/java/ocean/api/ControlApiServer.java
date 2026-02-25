package ocean.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import ocean.communication.oceanserver.OceanClient;
import ocean.communication.submarine.SubmarineServer;
import ocean.data.repository.*;
import ocean.model.*;
import ocean.util.AppLauncher;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * REST-Webservice für den Ocean Explorer – Steuerung & Daten.
 *
 * Ergänzt den PhotoApiServer um Endpunkte zur interaktiven Steuerung
 * von Schiff und Submarines über die Angular WebApp.
 *
 * Alle Endpunkte:
 *
 *   POST /api/ship/launch      – Schiff starten
 *   POST /api/ship/navigate    – Schiff bewegen
 *   POST /api/ship/scan        – Sektor scannen
 *   POST /api/ship/exit        – Schiff beenden
 *   GET  /api/ships            – Alle Schiffe
 *   GET  /api/ships/{name}/positions – Positionshistorie
 *   GET  /api/ships/{name}/scans     – Scans eines Schiffs
 *   GET  /api/scans            – Alle Scans
 *
 *   POST /api/submarine/launch        – Submarine starten
 *   POST /api/submarine/{id}/exit     – Submarine einziehen
 *   GET  /api/ships/{name}/submarines – Submarines eines Schiffs
 *   GET  /api/submarines              – Alle Submarines
 *   GET  /api/measurements            – Alle 3D-Messpunkte
 *
 *   GET  /api/status           – Server-Status
 *
 * @author OceanExplorer Team
 */
public class ControlApiServer {

    private static final Logger logger = LoggerFactory.getLogger(ControlApiServer.class);

    /** Auf welchem Port der OceanServer lauscht (Ship-Verbindung) */
    private static final int OCEAN_SHIP_PORT = 8150;

    /** Auf welchem Port der OceanServer Submarines akzeptiert */
    private static final int OCEAN_SUBMARINE_PORT = 8151;

    /** Maximale Submarines pro Schiff */
    private static final int MAX_SUBMARINES = 4;

    private final Javalin app;
    private final int port;

    // ── Repositories ─────────────────────────────────────────────────────
    private final ShipRepository shipRepo;
    private final ScanRepository scanRepo;
    private final SubmarineRepository subRepo;
    private final PhotoRepository photoRepo;

    // ── Live-State (Ein Schiff aktiv zur Zeit) ────────────────────────────
    /** Name des aktuell aktiven Schiffs (null = keines gestartet) */
    private volatile String activeShipName = null;

    /** OceanClient für das aktive Schiff */
    private volatile OceanClient activeClient = null;

    /** SubmarineServer für das aktive Schiff */
    private volatile SubmarineServer subServer = null;

    /** DB-ID des aktiven Schiffs */
    private volatile long activeShipDbId = -1;

    /** Server-ID wie vom OceanServer vergeben (z.B. "#1#Explorer-220556") */
    private volatile String activeShipServerId = null;

    /** OceanServer-Hostname */
    private final String oceanHost;

    /** Submarineserver-Port für diesen Run */
    private volatile int subServerPort = SubmarineServer.DEFAULT_PORT;

    /** Zähler gestarteter Submarines in der aktuellen Session */
    private final java.util.concurrent.atomic.AtomicInteger launchedSubCount =
            new java.util.concurrent.atomic.AtomicInteger(0);

    public ControlApiServer(ShipRepository shipRepo,
                            ScanRepository scanRepo,
                            SubmarineRepository subRepo,
                            PhotoRepository photoRepo,
                            String oceanHost,
                            int port) {
        this.shipRepo  = shipRepo;
        this.scanRepo  = scanRepo;
        this.subRepo   = subRepo;
        this.photoRepo = photoRepo;
        this.oceanHost = oceanHost;
        this.port      = port;
        this.app       = buildApp();
    }

    // =========================================================
    // BUILD
    // =========================================================

    private Javalin buildApp() {
        Javalin javalin = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> cors.addRule(r -> r.anyHost()));
            config.useVirtualThreads = true;
        });

        // ── Ship ──────────────────────────────────────────────────────────
        javalin.post("/api/ship/launch",   this::handleLaunchShip);
        javalin.post("/api/ship/navigate", this::handleNavigate);
        javalin.post("/api/ship/scan",     this::handleScan);
        javalin.post("/api/ship/exit",     this::handleExitShip);
        javalin.get("/api/ships",          this::handleGetShips);
        javalin.get("/api/ships/{name}/positions",  this::handleGetPositions);
        javalin.get("/api/ships/{name}/scans",      this::handleGetShipScans);
        javalin.get("/api/ships/{name}/submarines", this::handleGetShipSubmarines);

        // ── Scans ─────────────────────────────────────────────────────────
        javalin.get("/api/scans", this::handleGetAllScans);

        // ── Submarine ─────────────────────────────────────────────────────
        javalin.post("/api/submarine/launch",    this::handleLaunchSubmarine);
        javalin.post("/api/submarine/{id}/exit", this::handleExitSubmarine);
        javalin.get("/api/submarines",           this::handleGetAllSubmarines);
        javalin.get("/api/measurements",         this::handleGetMeasurements);

        // ── Status ────────────────────────────────────────────────────────
        javalin.get("/api/status", this::handleStatus);

        // ── Photos ────────────────────────────────────────────────────────
        javalin.get("/api/photos",                        this::handleGetAllPhotos);
        javalin.get("/api/photos/{id}",                   this::handleGetPhotoImage);
        javalin.get("/api/submarines/{id}/photos",        this::handleGetSubmarinePhotos);
        javalin.get("/",       ctx -> ctx.redirect("/gallery"));
        javalin.get("/gallery", this::handleGalleryRedirect);

        return javalin;
    }

    // =========================================================
    // SHIP HANDLERS
    // =========================================================

    private void handleLaunchShip(Context ctx) {
        if (activeShipName != null) {
            ctx.json(err("Schiff '" + activeShipName + "' ist bereits aktiv. Bitte erst beenden."));
            return;
        }

        JSONObject body;
        try { body = new JSONObject(ctx.body()); }
        catch (Exception e) { ctx.status(400).json(err("Ungültiger JSON-Body")); return; }

        String name    = body.optString("name",    "Explorer-" + LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss")));
        int    sectorX = body.optInt("sectorX", 50);
        int    sectorY = body.optInt("sectorY", 50);
        int    dirX    = body.optInt("dirX", 0);
        int    dirY    = body.optInt("dirY", 1);

        OceanClient client = new OceanClient(oceanHost, OCEAN_SHIP_PORT);
        try {
            client.connect();
            Vec2D startPos = new Vec2D(sectorX, sectorY);
            Vec2D startDir = new Vec2D(dirX, dirY);

            boolean launched = client.launch(name, startPos, startDir);
            if (!launched) {
                client.disconnect();
                ctx.json(err("OceanServer hat Launch abgelehnt (Name schon vergeben?)"));
                return;
            }

            // Zustand speichern
            activeClient       = client;
            activeShipName     = name;
            activeShipServerId = client.getShipServerId();
            launchedSubCount.set(0);

            // DB: Schiff speichern
            Ship ship = new Ship(name, startPos, startDir);
            ship.setPosition(startPos);
            ship.setDirection(startDir);
            activeShipDbId = shipRepo.save(ship);
            scanRepo.savePosition(activeShipDbId, startPos, startDir);

            logger.info("✅ Schiff '{}' gestartet (Server-ID: {})", name, activeShipServerId);
            ctx.json(ok(buildShipJson(name, sectorX, sectorY, dirX, dirY)));

        } catch (IOException e) {
            try { client.disconnect(); } catch (Exception ignored) {}
            ctx.json(err("OceanServer nicht erreichbar: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Launch-Fehler: {}", e.getMessage());
            ctx.json(err("Interner Fehler: " + e.getMessage()));
        }
    }

    private void handleNavigate(Context ctx) {
        if (activeClient == null || activeShipName == null) {
            ctx.json(err("Kein Schiff aktiv.")); return;
        }
        if (subServer != null && subServer.isDiving()) {
            ctx.json(err("Navigation gesperrt – Submarines tauchen noch.")); return;
        }

        JSONObject body;
        try { body = new JSONObject(ctx.body()); }
        catch (Exception e) { ctx.status(400).json(err("Ungültiger JSON-Body")); return; }

        String rudder = body.optString("rudder", "Center");
        String course = body.optString("course", "Forward");

        try {
            Rudder r = Rudder.valueOf(rudder);
            Course c = Course.valueOf(course);

            OceanClient.NavigateResult result = activeClient.navigate(r, c);
            if (result == null) {
                ctx.json(err("Navigation fehlgeschlagen (Hindernis?)")); return;
            }

            // DB-Update
            Vec2D newPos = result.position();
            Vec2D newDir = result.direction();
            shipRepo.updatePosition(activeShipName, newPos, newDir);
            scanRepo.savePosition(activeShipDbId, newPos, newDir);

            ctx.json(ok(buildShipJson(activeShipName,
                    newPos.getX(), newPos.getY(),
                    newDir.getX(), newDir.getY())));

        } catch (IllegalArgumentException e) {
            ctx.status(400).json(err("Ungültige rudder/course Werte: " + e.getMessage()));
        } catch (IOException e) {
            ctx.json(err("Kommunikationsfehler: " + e.getMessage()));
        }
    }

    private void handleScan(Context ctx) {
        if (activeClient == null) {
            ctx.json(err("Kein Schiff aktiv.")); return;
        }

        try {
            ScanResult result = activeClient.scan();
            if (result == null) {
                ctx.json(err("Scan fehlgeschlagen")); return;
            }

            // Aktuelle Position ermitteln
            Ship ship = shipRepo.findByName(activeShipName);
            Vec2D pos = ship != null && ship.getPosition() != null
                    ? ship.getPosition()
                    : new Vec2D(0, 0);

            // DB speichern
            scanRepo.saveScan(activeShipDbId, pos, result);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("x", pos.getX());
            data.put("y", pos.getY());
            data.put("averageDepth", result.getAverageDepth());
            data.put("stdDeviation", result.getStandardDeviation());
            ctx.json(ok(data));

        } catch (IOException e) {
            ctx.json(err("Kommunikationsfehler: " + e.getMessage()));
        }
    }

    private void handleExitShip(Context ctx) {
        if (activeClient == null) {
            ctx.json(err("Kein Schiff aktiv.")); return;
        }
        try {
            activeClient.disconnect();
        } catch (Exception ignored) {}

        if (subServer != null) {
            try { subServer.shutdown(); } catch (Exception ignored) {}
            subServer = null;
        }

        activeClient       = null;
        activeShipName     = null;
        activeShipServerId = null;
        activeShipDbId     = -1;

        ctx.json(ok("Schiff beendet"));
    }

    private void handleGetShips(Context ctx) {
        try {
            List<Ship> ships = shipRepo.findAll();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Ship s : ships) {
                result.add(buildShipJson(
                    s.getName(),
                    s.getPosition() != null ? s.getPosition().getX() : null,
                    s.getPosition() != null ? s.getPosition().getY() : null,
                    s.getDirection() != null ? s.getDirection().getX() : null,
                    s.getDirection() != null ? s.getDirection().getY() : null
                ));
            }
            ctx.json(result);
        } catch (Exception e) {
            ctx.status(500).json(err("DB-Fehler: " + e.getMessage()));
        }
    }

    private void handleGetPositions(Context ctx) {
        String name = ctx.pathParam("name");
        Long shipId = shipRepo.getIdByName(name);
        if (shipId == null) { ctx.status(404).json(err("Schiff nicht gefunden")); return; }
        ctx.json(scanRepo.findPositionsByShip(shipId));
    }

    private void handleGetShipScans(Context ctx) {
        String name = ctx.pathParam("name");
        Long shipId = shipRepo.getIdByName(name);
        if (shipId == null) { ctx.status(404).json(err("Schiff nicht gefunden")); return; }
        ctx.json(scanRepo.findScansByShip(shipId));
    }

    private void handleGetShipSubmarines(Context ctx) {
        String name = ctx.pathParam("name");
        Long shipId = shipRepo.getIdByName(name);
        if (shipId == null) { ctx.status(404).json(err("Schiff nicht gefunden")); return; }
        ctx.json(subRepo.findByShip(shipId));
    }

    // =========================================================
    // SCAN HANDLERS
    // =========================================================

    private void handleGetAllScans(Context ctx) {
        try {
            ctx.json(scanRepo.findAllScans());
        } catch (Exception e) {
            ctx.status(500).json(err("DB-Fehler: " + e.getMessage()));
        }
    }

    // =========================================================
    // SUBMARINE HANDLERS
    // =========================================================

    private void handleLaunchSubmarine(Context ctx) {
        if (activeShipServerId == null) {
            ctx.json(err("Kein Schiff aktiv – bitte zuerst ein Schiff starten.")); return;
        }
        if (launchedSubCount.get() >= MAX_SUBMARINES) {
            ctx.json(err("Maximal " + MAX_SUBMARINES + " Submarines erlaubt.")); return;
        }

        // SubmarineServer starten, falls noch nicht oben
        synchronized (this) {
            if (subServer == null || !subServer.isAlive()) {
                subServerPort = SubmarineServer.DEFAULT_PORT;
                subServer = new SubmarineServer(subServerPort, subRepo, activeShipDbId, MAX_SUBMARINES);
                subServer.start();
                logger.info("SubmarineServer gestartet auf Port {}", subServerPort);
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            }
        }

        boolean ok = AppLauncher.startSubmarine(
                "external",
                activeShipServerId,
                "localhost",
                subServerPort,
                oceanHost,
                OCEAN_SUBMARINE_PORT
        );

        if (!ok) {
            ctx.json(err("Submarine-Prozess konnte nicht gestartet werden.")); return;
        }

        launchedSubCount.incrementAndGet();
        logger.info("✅ Submarine #{} gestartet", launchedSubCount.get());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "Submarine gestartet");
        data.put("total",   launchedSubCount.get());
        ctx.json(ok(data));
    }

    private void handleExitSubmarine(Context ctx) {
        String idStr = ctx.pathParam("id");
        try {
            long subId = Long.parseLong(idStr);
            subRepo.deactivateSubmarine(subId);
            ctx.json(ok("Submarine " + subId + " deaktiviert"));
        } catch (NumberFormatException e) {
            ctx.status(400).json(err("Ungültige ID: " + idStr));
        } catch (Exception e) {
            ctx.status(500).json(err("DB-Fehler: " + e.getMessage()));
        }
    }

    private void handleGetAllSubmarines(Context ctx) {
        try {
            ctx.json(subRepo.findAll());
        } catch (Exception e) {
            ctx.status(500).json(err("DB-Fehler: " + e.getMessage()));
        }
    }

    private void handleGetMeasurements(Context ctx) {
        try {
            ctx.json(subRepo.findAllMeasurements());
        } catch (Exception e) {
            ctx.status(500).json(err("DB-Fehler: " + e.getMessage()));
        }
    }

    // =========================================================
    // PHOTO HANDLERS
    // =========================================================

    private void handleGetAllPhotos(Context ctx) {
        try { ctx.json(photoRepo.findAllMeta()); }
        catch (Exception e) { ctx.status(500).json(err("DB-Fehler: " + e.getMessage())); }
    }

    private void handleGetPhotoImage(Context ctx) {
        try {
            long id = Long.parseLong(ctx.pathParam("id"));
            byte[] data = photoRepo.findPhotoData(id);
            if (data == null) { ctx.status(404).result("Foto nicht gefunden"); return; }
            ctx.contentType("image/png").result(data);
        } catch (NumberFormatException e) {
            ctx.status(400).result("Ungültige ID");
        } catch (Exception e) {
            ctx.status(500).result("DB-Fehler: " + e.getMessage());
        }
    }

    private void handleGetSubmarinePhotos(Context ctx) {
        try {
            long id = Long.parseLong(ctx.pathParam("id"));
            ctx.json(photoRepo.findBySubmarine(id));
        } catch (NumberFormatException e) {
            ctx.status(400).json(err("Ungültige ID"));
        } catch (Exception e) {
            ctx.status(500).json(err("DB-Fehler: " + e.getMessage()));
        }
    }

    private void handleGalleryRedirect(Context ctx) {
        ctx.result("Ocean Explorer API läuft. Angular WebApp: http://localhost:4200")
           .contentType("text/plain");
    }

    // =========================================================
    // STATUS HANDLER
    // =========================================================

    private void handleStatus(Context ctx) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("serverRunning", true);
        status.put("activeShip",    activeShipName);
        status.put("subsLaunched",  launchedSubCount.get());
        status.put("subServerRunning", subServer != null && subServer.isAlive());
        ctx.json(status);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private Map<String, Object> buildShipJson(String name, Object x, Object y, Object dirX, Object dirY) {
        boolean isActive = name.equals(activeShipName);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name",       name);
        m.put("active",     isActive);
        m.put("currentX",   x);
        m.put("currentY",   y);
        m.put("directionX", dirX);
        m.put("directionY", dirY);
        return m;
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("data",    data);
        return m;
    }

    private Map<String, Object> err(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("error",   msg);
        return m;
    }

    // =========================================================
    // START / STOP
    // =========================================================

    public void start() {
        app.start(port);
        logger.info("=== ControlApiServer gestartet auf http://localhost:{} ===", port);
    }

    public void stop() {
        if (activeClient != null) {
            try { activeClient.disconnect(); } catch (Exception ignored) {}
        }
        if (subServer != null) {
            try { subServer.shutdown(); } catch (Exception ignored) {}
        }
        app.stop();
        logger.info("ControlApiServer gestoppt.");
    }

    public Javalin getApp() { return app; }
}

