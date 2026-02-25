package ocean.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import ocean.data.repository.PhotoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST-Webservice für den Ocean Explorer.
 *
 * Stellt Fotos und Metadaten der Submarine-Tauchgänge über HTTP bereit.
 * Wird von der Angular WebApp (oder direkt im Browser) genutzt.
 *
 * Endpoints:
 *   GET  /api/photos            – Liste aller Foto-Metadaten (JSON)
 *   GET  /api/photos/{id}       – Einzelnes Foto als PNG (image/png)
 *   GET  /api/submarines/{id}/photos – Fotos eines bestimmten Submarines
 *   GET  /                      – Einfache HTML-Galerie zum Durchklicken
 *
 * Läuft standardmäßig auf Port 8080.
 *
 * @author OceanExplorer Team
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
        this.port = port;
        this.app = buildApp();
    }

    private Javalin buildApp() {
        Javalin javalin = Javalin.create(config -> {
            // CORS für Angular-Dev-Server erlauben
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(rule -> rule.anyHost());
            });
            config.useVirtualThreads = true;
        });

        // ── Alle Foto-Metadaten ──────────────────────────────────────────
        javalin.get("/api/photos", this::handleGetAllPhotos);

        // ── Einzelnes Foto als PNG-Bild ──────────────────────────────────
        javalin.get("/api/photos/{id}", this::handleGetPhotoImage);

        // ── Fotos eines bestimmten Submarines ───────────────────────────
        javalin.get("/api/submarines/{id}/photos", this::handleGetSubmarinePhotos);

        // ── Einfache HTML-Galerie (kein Angular nötig zum Testen) ────────
        javalin.get("/", this::handleGallery);
        javalin.get("/gallery", this::handleGallery);

        return javalin;
    }

    // =========================================================
    // HANDLER
    // =========================================================

    /** GET /api/photos → JSON-Liste aller Foto-Metadaten */
    private void handleGetAllPhotos(Context ctx) {
        try {
            var photos = photoRepo.findAllMeta();
            ctx.json(photos);
        } catch (Exception e) {
            logger.error("Fehler beim Abrufen der Fotos: {}", e.getMessage());
            ctx.status(500).result("Datenbankfehler: " + e.getMessage());
        }
    }

    /** GET /api/photos/{id} → PNG-Bild direkt */
    private void handleGetPhotoImage(Context ctx) {
        String idStr = ctx.pathParam("id");
        try {
            long id = Long.parseLong(idStr);
            byte[] data = photoRepo.findPhotoData(id);
            if (data == null) {
                ctx.status(404).result("Foto nicht gefunden: " + id);
                return;
            }
            ctx.contentType("image/png");
            ctx.result(data);
        } catch (NumberFormatException e) {
            ctx.status(400).result("Ungültige ID: " + idStr);
        } catch (Exception e) {
            logger.error("Fehler beim Abrufen des Fotos {}: {}", idStr, e.getMessage());
            ctx.status(500).result("Datenbankfehler: " + e.getMessage());
        }
    }

    /** GET /api/submarines/{id}/photos → JSON-Liste der Fotos des Submarines */
    private void handleGetSubmarinePhotos(Context ctx) {
        String idStr = ctx.pathParam("id");
        try {
            long id = Long.parseLong(idStr);
            var photos = photoRepo.findBySubmarine(id);
            ctx.json(photos);
        } catch (NumberFormatException e) {
            ctx.status(400).result("Ungültige ID: " + idStr);
        } catch (Exception e) {
            logger.error("Fehler: {}", e.getMessage());
            ctx.status(500).result("Datenbankfehler: " + e.getMessage());
        }
    }

    /**
     * GET / oder /gallery → Einfache HTML-Foto-Galerie.
     * Zeigt alle Fotos als anklickbare Thumbnails an.
     * Kein Angular nötig – funktioniert direkt im Browser.
     */
    private void handleGallery(Context ctx) {
        try {
            var photos = photoRepo.findAllMeta();

            StringBuilder html = new StringBuilder();
            html.append("""
                <!DOCTYPE html>
                <html lang="de">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>🌊 Ocean Explorer – Submarine Galerie</title>
                    <style>
                        * { box-sizing: border-box; margin: 0; padding: 0; }
                        body {
                            font-family: 'Segoe UI', sans-serif;
                            background: #0a1628;
                            color: #e0f0ff;
                            min-height: 100vh;
                        }
                        header {
                            background: linear-gradient(135deg, #0d2b4e, #1a4a7a);
                            padding: 24px 32px;
                            border-bottom: 2px solid #1e6fba;
                        }
                        header h1 { font-size: 1.8rem; color: #7ec8ff; }
                        header p  { color: #8ab8d8; margin-top: 6px; }
                        .stats {
                            padding: 16px 32px;
                            background: #0e1f38;
                            border-bottom: 1px solid #1e3a5e;
                            font-size: 0.9rem;
                            color: #6aa5cc;
                        }
                        .gallery {
                            display: grid;
                            grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
                            gap: 20px;
                            padding: 32px;
                        }
                        .card {
                            background: #0f2035;
                            border: 1px solid #1e3a5e;
                            border-radius: 12px;
                            overflow: hidden;
                            transition: transform 0.2s, box-shadow 0.2s;
                            cursor: pointer;
                        }
                        .card:hover {
                            transform: translateY(-4px);
                            box-shadow: 0 8px 24px rgba(30,111,186,0.4);
                            border-color: #3a8cc8;
                        }
                        .card img {
                            width: 100%;
                            height: 200px;
                            object-fit: cover;
                            display: block;
                            background: #071020;
                        }
                        .card-info {
                            padding: 14px 16px;
                        }
                        .card-info .sub  { font-weight: bold; color: #7ec8ff; font-size: 0.95rem; }
                        .card-info .pos  { color: #6aa5cc; font-size: 0.82rem; margin-top: 4px; }
                        .card-info .time { color: #446a8c; font-size: 0.78rem; margin-top: 6px; }
                        .empty {
                            grid-column: 1/-1;
                            text-align: center;
                            padding: 60px 0;
                            color: #446a8c;
                        }
                        .empty .icon { font-size: 4rem; margin-bottom: 16px; }

                        /* Lightbox */
                        #lightbox {
                            display: none;
                            position: fixed; inset: 0;
                            background: rgba(0,0,0,0.92);
                            z-index: 1000;
                            align-items: center;
                            justify-content: center;
                            flex-direction: column;
                            gap: 16px;
                        }
                        #lightbox.open { display: flex; }
                        #lightbox img {
                            max-width: 90vw;
                            max-height: 80vh;
                            border-radius: 8px;
                            border: 2px solid #3a8cc8;
                        }
                        #lightbox .close-btn {
                            position: absolute;
                            top: 20px; right: 28px;
                            font-size: 2rem;
                            cursor: pointer;
                            color: #7ec8ff;
                            background: none; border: none;
                        }
                        #lightbox .lb-info {
                            color: #8ab8d8;
                            font-size: 0.9rem;
                            text-align: center;
                        }
                        #lightbox .nav-btn {
                            position: absolute;
                            top: 50%; transform: translateY(-50%);
                            font-size: 2.5rem;
                            background: rgba(14,31,56,0.7);
                            border: 1px solid #1e6fba;
                            color: #7ec8ff;
                            border-radius: 50%;
                            width: 56px; height: 56px;
                            cursor: pointer;
                            display: flex; align-items: center; justify-content: center;
                        }
                        #prev-btn { left: 20px; }
                        #next-btn { right: 20px; }
                    </style>
                </head>
                <body>
                <header>
                    <h1>🌊 Ocean Explorer – Submarine Galerie</h1>
                    <p>Aufnahmen der Tauchgänge</p>
                </header>
                <div class="stats">
                """);

            html.append("📸 ").append(photos.size()).append(" Fotos gespeichert");
            html.append("&nbsp;&nbsp;|&nbsp;&nbsp;<a href='/api/photos' style='color:#5ba3d8'>JSON-API</a>");
            html.append("</div>\n<div class='gallery' id='gallery'>\n");

            if (photos.isEmpty()) {
                html.append("""
                    <div class="empty">
                        <div class="icon">🤿</div>
                        <div>Noch keine Fotos vorhanden.<br>Starte ein Submarine um Aufnahmen zu machen.</div>
                    </div>
                    """);
            } else {
                for (int i = 0; i < photos.size(); i++) {
                    var p = photos.get(i);
                    html.append(String.format("""
                        <div class="card" onclick="openLightbox(%d)" data-index="%d">
                            <img src="/api/photos/%d" alt="Foto %d" loading="lazy"
                                 onerror="this.style.display='none'">
                            <div class="card-info">
                                <div class="sub">🤿 %s</div>
                                <div class="pos">📍 Pos: (%d, %d, %d) | Dir: (%d, %d, %d)</div>
                                <div class="time">🕐 %s</div>
                            </div>
                        </div>
                        """,
                        p.id(), i, p.id(), p.id(),
                        escapeHtml(p.submarineName()),
                        p.x(), p.y(), p.z(),
                        p.dirX(), p.dirY(), p.dirZ(),
                        p.timestamp() != null ? p.timestamp() : "–"
                    ));
                }
            }

            html.append("</div>\n");

            // Lightbox + Navigation
            html.append("""
                <div id="lightbox">
                    <button class="close-btn" onclick="closeLightbox()">✕</button>
                    <button class="nav-btn" id="prev-btn" onclick="navigate(-1)">‹</button>
                    <img id="lb-img" src="" alt="">
                    <div class="lb-info" id="lb-info"></div>
                    <button class="nav-btn" id="next-btn" onclick="navigate(1)">›</button>
                </div>
                <script>
                    const photos = """);

            // Foto-IDs als JS-Array
            html.append("[");
            for (int i = 0; i < photos.size(); i++) {
                if (i > 0) html.append(",");
                var p = photos.get(i);
                html.append(String.format("{id:%d,sub:'%s',pos:'(%d,%d,%d)',time:'%s'}",
                        p.id(),
                        escapeJs(p.submarineName()),
                        p.x(), p.y(), p.z(),
                        p.timestamp() != null ? escapeJs(p.timestamp()) : ""
                ));
            }
            html.append("];\n");

            html.append("""
                    let current = 0;
                    function openLightbox(id) {
                        const idx = photos.findIndex(p => p.id === id);
                        showPhoto(idx);
                        document.getElementById('lightbox').classList.add('open');
                    }
                    function closeLightbox() {
                        document.getElementById('lightbox').classList.remove('open');
                    }
                    function navigate(dir) {
                        showPhoto((current + dir + photos.length) % photos.length);
                    }
                    function showPhoto(idx) {
                        current = idx;
                        const p = photos[idx];
                        document.getElementById('lb-img').src = '/api/photos/' + p.id;
                        document.getElementById('lb-info').textContent =
                            '🤿 ' + p.sub + '  |  📍 ' + p.pos + '  |  🕐 ' + p.time +
                            '  (' + (idx+1) + '/' + photos.length + ')';
                    }
                    document.getElementById('lightbox').addEventListener('click', function(e) {
                        if (e.target === this) closeLightbox();
                    });
                    document.addEventListener('keydown', function(e) {
                        if (e.key === 'Escape') closeLightbox();
                        if (e.key === 'ArrowRight') navigate(1);
                        if (e.key === 'ArrowLeft')  navigate(-1);
                    });
                </script>
                </body></html>
                """);

            ctx.contentType("text/html;charset=UTF-8").result(html.toString());

        } catch (Exception e) {
            logger.error("Fehler beim Rendern der Galerie: {}", e.getMessage());
            ctx.status(500).result("Fehler: " + e.getMessage());
        }
    }

    // =========================================================
    // START / STOP
    // =========================================================

    /** Gibt die interne Javalin-Instanz zurück (für Tests). */
    public Javalin getApp() {
        return app;
    }

    /** Startet den HTTP-Server (blockiert nicht). */
    public void start() {
        app.start(port);
        logger.info("=== PhotoApiServer gestartet auf http://localhost:{} ===", port);
        logger.info("  Galerie:    http://localhost:{}/", port);
        logger.info("  REST-API:   http://localhost:{}/api/photos", port);
    }

    /** Stoppt den HTTP-Server. */
    public void stop() {
        app.stop();
        logger.info("PhotoApiServer gestoppt.");
    }

    // =========================================================
    // HILFSMETHODEN
    // =========================================================

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }
}

