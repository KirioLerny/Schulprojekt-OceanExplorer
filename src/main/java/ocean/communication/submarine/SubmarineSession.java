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
        submarineDbId = subRepo.saveSubmarine(submarineId, shipId);
        diveId = subRepo.startDive(submarineDbId);
        String pilot = buildPilotRoute();
        logger.info(">>> Sende Pilot-Route an {}", submarineId);
        out.println(pilot);
    }

    /** 3D-Messpunkte empfangen und speichern */
    private void handleMeasure(JSONObject json) {
        JSONArray measurement = json.optJSONArray("measurement");
        if (measurement == null) {
            logger.warn("measure-Nachricht ohne 'measurement' Array");
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
        }
    }

    /** PNG-Foto empfangen (Hex-kodiert) und als BLOB speichern */
    private void handlePicture(JSONObject json) {
        String pictureHex = json.optString("picture", "");
        if (pictureHex.isEmpty()) {
            logger.warn("picture-Nachricht ohne Bilddaten");
            return;
        }
        try {
            byte[] photoData = hexToBytes(pictureHex);
            if (diveId >= 0) {
                subRepo.savePhoto(diveId, photoData);
                logger.info("✅ Foto gespeichert ({} Bytes, {})", photoData.length, submarineId);
            }
        } catch (Exception e) {
            logger.error("Fehler beim Speichern des Fotos: {}", e.getMessage());
        }
    }

    /** Unfall – Tauchgang als CRASHED beenden */
    private void handleCrash(JSONObject json) {
        String message = json.optString("message", "Unbekannter Unfall");
        logger.warn("Unfall von {}: {}", submarineId, message);
        Vec2D position = new Vec2D(0, 0);
        if (json.has("sector")) {
            try {
                JSONArray vec = json.getJSONObject("sector").getJSONArray("vec2");
                position = new Vec2D(vec.getInt(0), vec.getInt(1));
            } catch (Exception ignored) {}
        }
        if (diveId >= 0)        subRepo.endDive(diveId, "CRASHED");
        if (submarineDbId >= 0) {
            subRepo.deactivateSubmarine(submarineDbId);
            subRepo.saveAccident(shipId, submarineDbId, position, message);
        }
    }

    /** Submarine aufgetaucht – Tauchgang als SURFACED beenden */
    private void handleArise(JSONObject json) {
        logger.info("Submarine {} aufgetaucht ✅", submarineId);
        if (diveId >= 0)        subRepo.endDive(diveId, "SURFACED");
        if (submarineDbId >= 0) subRepo.deactivateSubmarine(submarineDbId);
    }

    /** Einfache Route: tauche ab, miss, fotografiere, tauche auf */
    private String buildPilotRoute() {
        JSONObject pilot   = new JSONObject();
        pilot.put("cmd", "pilot");
        JSONArray actions  = new JSONArray();

        JSONObject dive = new JSONObject(); dive.put("action", "dive"); dive.put("distance", 5); actions.put(dive);
        JSONObject meas = new JSONObject(); meas.put("action", "measure"); actions.put(meas);
        JSONObject pic  = new JSONObject(); pic.put("action",  "picture"); actions.put(pic);
        JSONObject aris = new JSONObject(); aris.put("action", "arise");   actions.put(aris);

        pilot.put("actions", actions);
        return pilot.toString();
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
