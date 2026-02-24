package ocean.communication.submarine;

import ocean.data.repository.SubmarineRepository;
import ocean.model.Vec2D;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Verwaltet die Kommunikation mit einem einzelnen Submarine.
 * Läuft im eigenen Thread (ein Thread pro verbundenem Submarine).
 *
 * Submarine -> ShipApp: ready, measure, picture, crash, arise
 * ShipApp -> Submarine: pilot
 *
 * @author OceanExplorer Team
 */
public class SubmarineSession extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(SubmarineSession.class);

    private final Socket socket;
    private final SubmarineRepository subRepo;
    private final long shipId;

    private PrintWriter out;
    private BufferedReader in;

    private long submarineDbId = -1;
    private long diveId = -1;
    private String submarineId = "unknown";

    public SubmarineSession(Socket socket, SubmarineRepository subRepo, long shipId) {
        this.socket = socket;
        this.subRepo = subRepo;
        this.shipId = shipId;
        setName("SubmarineSession-" + socket.getRemoteSocketAddress());
        setDaemon(true);
    }

    @Override
    public void run() {
        logger.info("Submarine verbunden von: {}", socket.getRemoteSocketAddress());
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isBlank()) handleMessage(line.trim());
            }
        } catch (IOException e) {
            logger.warn("Submarine-Verbindung unterbrochen: {}", e.getMessage());
        } finally {
            // TODO 1 FIX: Wenn Submarine einfach wegbricht (Prozess gestoppt, Timeout, etc.)
            // ohne arise oder crash zu senden → Tauchgang auf ABORTED setzen,
            // damit er nicht ewig auf DIVING hängt.
            if (diveId >= 0) {
                try {
                    subRepo.endDive(diveId, "ABORTED");
                    logger.warn("Tauchgang {} wurde als ABORTED beendet (Verbindung verloren)", diveId);
                } catch (Exception e) {
                    logger.error("Fehler beim Beenden des Tauchgangs nach Verbindungsverlust: {}", e.getMessage());
                }
            }
            if (submarineDbId >= 0) {
                try {
                    subRepo.deactivateSubmarine(submarineDbId);
                } catch (Exception ignored) {}
            }
            closeQuietly();
            logger.info("SubmarineSession beendet: {}", submarineId);
        }
    }

    private void handleMessage(String raw) {
        logger.debug("<<< Submarine [{}]: {}", submarineId, raw);
        JSONObject json;
        try {
            json = new JSONObject(raw);
        } catch (Exception e) {
            logger.warn("Ungültiges JSON vom Submarine: {}", raw);
            return;
        }
        switch (json.optString("cmd", "")) {
            case "ready"   -> handleReady(json);
            case "measure" -> handleMeasure(json);
            case "picture" -> handlePicture(json);
            case "crash"   -> handleCrash(json);
            case "arise"   -> handleArise(json);
            default        -> logger.warn("Unbekannter Submarine-Befehl: {}", json.optString("cmd"));
        }
    }

    /** Submarine ist bereit – in DB speichern, Pilot-Route senden */
    private void handleReady(JSONObject json) {
        submarineId = json.optString("id", "unknown");
        logger.info("=== Submarine bereit: {} ===", submarineId);

        // Wenn diese Session bereits einen Tauchgang hat (z.B. nach erneutem ready nach Crash),
        // den alten Tauchgang erst sauber beenden bevor ein neuer gestartet wird.
        if (diveId >= 0) {
            logger.warn("Submarine sendet ready erneut – beende alten Tauchgang {} als ABORTED", diveId);
            try { subRepo.endDive(diveId, "ABORTED"); } catch (Exception ignored) {}
            diveId = -1;
        }

        // saveSubmarine ist idempotent (ON DUPLICATE KEY UPDATE) –
        // bei erneutem ready wird die vorhandene DB-ID zurückgeliefert
        try {
            submarineDbId = subRepo.saveSubmarine(submarineId, shipId);
        } catch (Exception e) {
            logger.error("Fehler beim Speichern des Submarines: {}", e.getMessage());
            return;
        }

        diveId = subRepo.startDive(submarineDbId);
        logger.info(">>> Sende Pilot-Route an {}", submarineId);
        sendPilotRoute();
    }

    /** 3D-Messpunkte empfangen und speichern */
    private void handleMeasure(JSONObject json) {
        // TODO 2 FIX: Das Submarine sendet die Punkte unter dem Schlüssel "vecs" (nicht "measurement")
        // Format: {"cmd":"measure","vecs":[[x,y,z],[x,y,z],...]}
        JSONArray measurement = json.optJSONArray("vecs");
        if (measurement == null) {
            logger.warn("measure-Nachricht ohne 'vecs' Array (empfangen: {})", json);
            return;
        }
        List<int[]> points = new ArrayList<>();
        for (int i = 0; i < measurement.length(); i++) {
            JSONArray pt = measurement.getJSONArray(i);
            if (pt.length() >= 3) {
                points.add(new int[]{pt.getInt(0), pt.getInt(1), pt.getInt(2)});
            }
        }
        if (diveId >= 0) {
            subRepo.saveMeasurementPoints(diveId, points);
            logger.info("✅ {} Messpunkte gespeichert ({})", points.size(), submarineId);
        } else {
            logger.warn("Messpunkte empfangen aber kein aktiver Tauchgang (diveId={})", diveId);
        }
    }

    /** PNG-Foto empfangen (Hex-kodiert) und als BLOB speichern */
    private void handlePicture(JSONObject json) {
        // Das Submarine sendet das Foto unter dem Schlüssel "picture" als Hex-String
        // Format: {"cmd":"picture","id":"...","pos":{...},"dir":{...},"picture":"<hex>"}
        String pictureHex = json.optString("picture", "");
        if (pictureHex.isEmpty() || pictureHex.equals("ERROR")) {
            logger.warn("picture-Nachricht ohne Bilddaten oder mit Fehler: {}", json);
            return;
        }
        try {
            byte[] photoData = hexToBytes(pictureHex);
            if (diveId >= 0) {
                subRepo.savePhoto(diveId, photoData);
                logger.info("✅ Foto gespeichert ({} Bytes, {})", photoData.length, submarineId);
            } else {
                logger.warn("Foto empfangen aber kein aktiver Tauchgang (diveId={})", diveId);
            }
        } catch (Exception e) {
            logger.error("Fehler beim Speichern des Fotos: {}", e.getMessage());
        }
    }

    /** Unfall – Tauchgang als CRASHED beenden und in accident-Tabelle speichern */
    private void handleCrash(JSONObject json) {
        String message = json.optString("message", "Unbekannter Unfall");
        logger.warn("💥 CRASH von {}: {} | JSON: {}", submarineId, message, json);

        // Position aus "sector" (Vec2D {"vec2":[x,y]}) auslesen
        Vec2D position = new Vec2D(0, 0);
        if (json.has("sector")) {
            try {
                JSONArray vec = json.getJSONObject("sector").getJSONArray("vec2");
                position = new Vec2D(vec.getInt(0), vec.getInt(1));
                logger.info("Unfall-Position: ({},{})", position.getX(), position.getY());
            } catch (Exception e) {
                logger.warn("Konnte Unfall-Position nicht lesen: {}", e.getMessage());
            }
        }

        if (diveId >= 0) {
            try {
                subRepo.endDive(diveId, "CRASHED");
                logger.info("Tauchgang {} als CRASHED beendet", diveId);
            } catch (Exception e) {
                logger.error("Fehler beim Beenden des Tauchgangs: {}", e.getMessage());
            }
            diveId = -1;
        }

        if (submarineDbId >= 0) {
            try {
                subRepo.deactivateSubmarine(submarineDbId);
                subRepo.saveAccident(shipId, submarineDbId, position, message);
                logger.warn("✅ Unfall gespeichert in DB");
            } catch (Exception e) {
                logger.error("Fehler beim Speichern des Unfalls: {}", e.getMessage());
            }
            submarineDbId = -1;
        }
    }

    /** Submarine aufgetaucht – Tauchgang als SURFACED beenden */
    private void handleArise(@SuppressWarnings("unused") JSONObject json) {
        logger.info("Submarine {} aufgetaucht ✅", submarineId);
        if (diveId >= 0) {
            subRepo.endDive(diveId, "SURFACED");
            diveId = -1; // Verhindert doppeltes Beenden in finally
        }
        if (submarineDbId >= 0) {
            subRepo.deactivateSubmarine(submarineDbId);
            submarineDbId = -1; // Verhindert doppeltes Deaktivieren in finally
        }
    }

    /**
     * Sendet eine Pilot-Route an das Submarine.
     *
     * Das Submarine erwartet EINZELNE Pilot-Nachrichten, nicht eine Liste.
     * Format: {"cmd":"pilot","route":"<Route>","action":"<action>"}
     *
     * Route: C, N, NE, E, SE, S, SW, W, NW, UP, DOWN
     * Action: "None", "measure", "picture", "arise"
     *
     * WICHTIG: Nur 1x DOWN – das Meer kann sehr flach sein (14–20m).
     * Bei 3x DOWN im flachen Wasser würde das Submarine auf Grund laufen und crashen.
     *
     * Ablauf:
     *  1. DOWN       → 1 Schritt tauchen
     *  2. C+measure  → 3D-Punkte messen (submarine sendet measure zurück)
     *  3. C+picture  → Foto (submarine sendet picture zurück)
     *  4. UP         → 1 Schritt auftauchen
     *  5. UP+arise   → weiterer Schritt + submarine sendet arise wenn oben
     */
    private void sendPilotRoute() {
        sendPilot("DOWN", "None");     // 1x Abtauchen (reicht für flaches Wasser)
        sendPilot("C",    "measure");  // Messen – submarine sendet measure
        sendPilot("C",    "picture");  // Foto – submarine sendet picture
        sendPilot("UP",   "None");     // Auftauchen
        sendPilot("UP",   "arise");    // Letzter Schritt – arise-Action auslösen
    }

    /** Sendet einen einzelnen Pilot-Befehl an das Submarine */
    private void sendPilot(String route, String action) {
        JSONObject pilot = new JSONObject();
        pilot.put("cmd", "pilot");
        pilot.put("route", route);
        pilot.put("action", action);
        logger.debug(">>> Pilot: route={}, action={}", route, action);
        out.println(pilot);
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private void closeQuietly() {
        try {
            if (in  != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
