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
 * <pre>
 * Verwaltet die Kommunikation mit einem einzelnen Submarine.
 * Das Submarine sendet nach JEDER Aktion ein neues "ready".
 * Die Session antwortet jeweils mit dem naechsten pilot-Schritt (State-Machine).
 *
 * Ablauf:
 *   ready (Schritt 0)  pilot DOWN None    taucht ab
 *   ready (Schritt 1)  pilot C measure    Submarine sendet "measure"
 *   ready (Schritt 2)  pilot C picture    Submarine sendet "picture"
 *   ready (Schritt 3)  pilot UP None      taucht auf
 *   ready (Schritt 4)  pilot UP arise     Submarine sendet "arise"
 * </pre>
 */
public class SubmarineSession extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(SubmarineSession.class);

    private static final String[][] PILOT_STEPS = {
        {"DOWN", "None"},
        {"C",    "measure"},
        {"C",    "picture"},
        {"UP",   "None"},
        {"UP",   "arise"},
    };

    private final Socket socket;
    private final SubmarineRepository subRepo;
    private final long shipId;

    private PrintWriter out;
    private BufferedReader in;

    private long submarineDbId = -1;
    private long diveId        = -1;
    private String submarineId = "unknown";

    private int pilotStep = 0;
    /** true nachdem arise sauber abgehandelt wurde – verhindert ABORTED im finally-Block */
    private volatile boolean arised = false;
    /** true wenn manuelle Steuerung aktiv ist – deaktiviert den automatischen PILOT_STEPS-Ablauf */
    private volatile boolean manualMode = false;

    private Runnable onFinished;

    public void setOnFinished(Runnable onFinished) {
        this.onFinished = onFinished;
    }

    /**
     * Gibt die Submarine-ID zurueck (gesetzt nach dem ersten ready-Paket).
     *
     * @return Submarine-ID oder "unknown" vor dem ersten ready
     */
    public String getSubmarineId() {
        return submarineId;
    }

    /**
     * Gibt den aktuellen Pilot-Schritt zurueck (0-4).
     */
    public int getPilotStep() {
        return pilotStep;
    }

    /**
     * Trennt die Verbindung zum Submarine (Force-Exit).
     */
    public void disconnect() {
        closeQuietly();
    }

    public SubmarineSession(Socket socket, SubmarineRepository subRepo, long shipId) {
        this.socket  = socket;
        this.subRepo = subRepo;
        this.shipId  = shipId;
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
            if (!arised && diveId >= 0) {
                try {
                    subRepo.endDive(diveId, "ABORTED");
                    logger.warn("Tauchgang {} als ABORTED beendet (Verbindung verloren)", diveId);
                } catch (Exception e) {
                    logger.error("Fehler beim Beenden des Tauchgangs: {}", e.getMessage());
                }
            }
            if (!arised && submarineDbId >= 0) {
                try { subRepo.deactivateSubmarine(submarineDbId); } catch (Exception ignored) {}
            }
            closeQuietly();
            logger.info("SubmarineSession beendet: {}", submarineId);
            if (onFinished != null) onFinished.run();
        }
    }

    private void handleMessage(String raw) {
        logger.debug("<<< Submarine [{}]: {}", submarineId, raw);
        JSONObject json;
        try {
            json = new JSONObject(raw);
        } catch (Exception e) {
            logger.warn("Ungueltiges JSON vom Submarine: {}", raw);
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

    /**
     * Submarine sendet "ready" nach jeder ausgefuehrten Aktion.
     * Beim ersten ready: in DB anlegen und Tauchgang starten.
     * Bei allen readys: naechsten Pilot-Schritt senden.
     */
    private void handleReady(JSONObject json) {
        submarineId = json.optString("id", "unknown");

        if (pilotStep == 0) {
            logger.info("Submarine initialisiert: {}", submarineId);
            try {
                submarineDbId = subRepo.saveSubmarine(submarineId, shipId);
            } catch (Exception e) {
                logger.error("Fehler beim Speichern des Submarines: {}", e.getMessage());
                return;
            }
            diveId = subRepo.startDive(submarineDbId);
        } else {
            logger.debug("Submarine ready nach Schritt {}: {}", pilotStep, submarineId);
        }

        // Im manuellen Modus sendet die WebApp Pilot-Befehle – kein automatischer Ablauf
        if (manualMode) {
            logger.debug("Manuelle Steuerung aktiv - warte auf Pilot-Befehl vom Nutzer");
            return;
        }

        if (pilotStep < PILOT_STEPS.length) {
            String route  = PILOT_STEPS[pilotStep][0];
            String action = PILOT_STEPS[pilotStep][1];
            logger.info(">>> Pilot [{}/{}]: route={}, action={}", pilotStep + 1, PILOT_STEPS.length, route, action);
            sendPilot(route, action);
            pilotStep++;
        } else {
            logger.debug("Alle Pilot-Schritte gesendet - warte auf arise/crash");
        }
    }

    /**
     * 3D-Messpunkte empfangen und speichern.
     */
    private void handleMeasure(JSONObject json) {
        JSONArray measurement = json.optJSONArray("vecs");
        if (measurement == null) {
            logger.warn("measure ohne 'vecs' Array: {}", json);
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
            logger.info("{} Messpunkte gespeichert ({})", points.size(), submarineId);
        } else {
            logger.warn("Messpunkte ohne aktiven Tauchgang - ignoriert");
        }
    }

    /**
     * PNG-Foto (Hex-kodiert) empfangen und als BLOB speichern.
     */
    private void handlePicture(JSONObject json) {
        String pictureHex = json.optString("picture", "");
        if (pictureHex.isEmpty() || pictureHex.equals("ERROR")) {
            logger.warn("picture ohne Bilddaten oder mit Fehler: {}", json);
            return;
        }

        int px = 0, py = 0, pz = 0;
        if (json.has("pos")) {
            try {
                JSONArray vec = json.getJSONObject("pos").getJSONArray("vec");
                px = vec.getInt(0); py = vec.getInt(1); pz = vec.getInt(2);
            } catch (Exception e) { logger.warn("Konnte pos nicht lesen: {}", e.getMessage()); }
        }

        int dx = 0, dy = 0, dz = 0;
        if (json.has("dir")) {
            try {
                JSONArray vec = json.getJSONObject("dir").getJSONArray("vec");
                dx = vec.getInt(0); dy = vec.getInt(1); dz = vec.getInt(2);
            } catch (Exception e) { logger.warn("Konnte dir nicht lesen: {}", e.getMessage()); }
        }

        try {
            byte[] photoData = hexToBytes(pictureHex);
            if (diveId >= 0) {
                subRepo.savePhoto(diveId, photoData, px, py, pz, dx, dy, dz);
                logger.info("Foto gespeichert ({} Bytes, pos=({},{},{}) dir=({},{},{}) {})",
                        photoData.length, px, py, pz, dx, dy, dz, submarineId);
            } else {
                logger.warn("Foto ohne aktiven Tauchgang - ignoriert");
            }
        } catch (Exception e) {
            logger.error("Fehler beim Speichern des Fotos: {}", e.getMessage());
        }
    }

    /**
     * Unfall - Tauchgang als CRASHED beenden.
     */
    private void handleCrash(JSONObject json) {
        String message = json.optString("message", "Unbekannter Unfall");
        logger.warn("CRASH von {}: {}", submarineId, message);

        Vec2D position = new Vec2D(0, 0);
        if (json.has("sector")) {
            try {
                JSONArray vec = json.getJSONObject("sector").getJSONArray("vec2");
                position = new Vec2D(vec.getInt(0), vec.getInt(1));
            } catch (Exception e) { logger.warn("Konnte Crash-Position nicht lesen: {}", e.getMessage()); }
        }

        if (diveId >= 0) {
            try { subRepo.endDive(diveId, "CRASHED"); }
            catch (Exception e) { logger.error("Fehler beim Beenden des Tauchgangs: {}", e.getMessage()); }
            diveId = -1;
        }
        if (submarineDbId >= 0) {
            try {
                subRepo.deactivateSubmarine(submarineDbId);
                subRepo.saveAccident(shipId, submarineDbId, position, message);
                logger.warn("Unfall gespeichert");
            } catch (Exception e) { logger.error("Fehler beim Speichern des Unfalls: {}", e.getMessage()); }
            submarineDbId = -1;
        }
    }

    /**
     * Submarine aufgetaucht (arise) - Tauchgang als SURFACED beenden und Session sauber schliessen.
     *
     * <pre>
     * Das Protokoll sieht vor:
     *   C => Srv Arise: Submarine ist wieder aufgetaucht und wird vom Schiff aufgenommen.
     *   Die Submarine-Anwendung beendet sich danach automatisch.
     * Wir schliessen die Socket-Verbindung sauber, damit der Prozess wirklich endet.
     * </pre>
     */
    private void handleArise(JSONObject json) {
        arised = true;   // verhindert ABORTED im finally-Block
        logger.info("Submarine {} aufgetaucht (arise) - beende Session sauber", submarineId);

        // Arise-Position loggen (optional)
        if (json.has("arisePos")) {
            try {
                JSONArray vec = json.getJSONObject("arisePos").getJSONArray("vec");
                logger.info("  arise-Position: ({},{},{})", vec.getInt(0), vec.getInt(1), vec.getInt(2));
            } catch (Exception e) {
                logger.debug("Konnte arisePos nicht lesen: {}", e.getMessage());
            }
        }

        if (diveId >= 0) {
            subRepo.endDive(diveId, "SURFACED");
            diveId = -1;
        }
        if (submarineDbId >= 0) {
            subRepo.deactivateSubmarine(submarineDbId);
            submarineDbId = -1;
        }

        // Verbindung sauber schliessen - Submarine-App beendet sich dann automatisch
        closeQuietly();
    }

    /**
     * Schaltet auf manuelle Steuerung um.
     * Der automatische PILOT_STEPS-Ablauf wird deaktiviert.
     * Die WebApp sendet danach Pilot-Befehle direkt via sendPilotManual().
     */
    public void enableManualMode() {
        manualMode = true;
        pilotStep  = PILOT_STEPS.length; // Automat abschalten
        logger.info("Submarine {} wechselt in manuellen Modus", submarineId);
    }

    /**
     * Sendet einen manuellen Pilot-Befehl an das Submarine.
     * Protokoll: { "cmd":"pilot", "route":"ROUTE", "action":"..." }
     *
     * @param route  Route-Wert (C, N, NE, E, SE, S, SW, W, NW, UP, DOWN, None)
     * @param action Aktion (None, measure, picture, take_photo, locate, arise)
     */
    public void sendPilotManual(String route, String action) {
        logger.info(">>> Manual Pilot: route={}, action={} -> {}", route, action, submarineId);
        sendPilot(route, action);
    }

    /**
     * Schickt dem Submarine den Pilot-Befehl UP+arise, damit es auftaucht und sich sauber beendet.
     */
    public void sendAriseAndClose() {
        arised = true;   // verhindert ABORTED im finally-Block
        logger.info("Sende Pilot UP+arise an Submarine {} (Einziehen)", submarineId);
        pilotStep = PILOT_STEPS.length;
        sendPilot("UP", "arise");
    }

    private void sendPilot(String route, String action) {
        JSONObject pilot = new JSONObject();
        pilot.put("cmd",    "pilot");
        pilot.put("route",  route);
        pilot.put("action", action);
        out.println(pilot);
    }

    private byte[] hexToBytes(String hex) {
        return java.util.HexFormat.of().parseHex(hex.toLowerCase());
    }

    private void closeQuietly() {
        try {
            if (in     != null) in.close();
            if (out    != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
