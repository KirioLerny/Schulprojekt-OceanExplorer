package ocean.communication.oceanserver;

import ocean.model.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * TCP-Client für die Kommunikation mit dem OceanServer.
 *
 * Diese Klasse stellt die Verbindung zum OceanServer her und
 * bietet Methoden für alle verfügbaren Befehle.
 *
 * Verwendung:
 * <pre>
 * OceanServerClient client = new OceanServerClient("localhost", 3000);
 * client.connect();
 * client.launch("MeinSchiff", new Vec2D(50, 50), new Vec2D(0, 1));
 * List<RadarEcho> radar = client.radar();
 * client.disconnect();
 * </pre>
 *
 * @author OceanExplorer Team
 */
public class OceanServerClient {

    private static final Logger logger = LoggerFactory.getLogger(OceanServerClient.class);

    /** Server-Hostname oder IP-Adresse */
    private final String host;

    /** Server-Port */
    private final int port;

    /** TCP-Socket zum Server */
    private Socket socket;

    /** Ausgabe-Stream zum Senden von Befehlen */
    private PrintWriter out;

    /** Eingabe-Stream zum Empfangen von Antworten */
    private BufferedReader in;

    /** Verbindungsstatus */
    private boolean connected = false;

    /**
     * Erstellt einen neuen OceanServerClient.
     *
     * @param host Server-Hostname (z.B. "localhost")
     * @param port Server-Port (z.B. 3000)
     */
    public OceanServerClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Stellt die Verbindung zum OceanServer her.
     * Der Server sendet nach Verbindung automatisch eine Config-Nachricht.
     *
     * @throws IOException wenn Verbindung fehlschlägt
     */
    public void connect() throws IOException {
        System.out.println("[DEBUG] Verbinde mit OceanServer " + host + ":" + port);

        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        connected = true;

        System.out.println("[DEBUG] Verbindung hergestellt!");

        // Server sendet automatisch Config-Nachricht nach Verbindung
        String configMsg = in.readLine();
        System.out.println("[DEBUG] Server Config: " + configMsg);

        if (configMsg != null && configMsg.contains("\"cmd\":\"config\"")) {
            JSONObject config = new JSONObject(configMsg);
            System.out.println("[DEBUG] OceanServer Konfiguration empfangen");
        }
    }

    /**
     * Trennt die Verbindung zum OceanServer.
     */
    public void disconnect() {
        logger.info("Trenne Verbindung zum OceanServer...");

        try {
            if (connected) {
                // Exit-Befehl senden
                sendCommand(CommandFactory.exit());
            }
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            logger.warn("Fehler beim Trennen der Verbindung: {}", e.getMessage());
        } finally {
            connected = false;
        }

        logger.info("Verbindung getrennt.");
    }

    /**
     * Startet ein Schiff im OceanServer.
     *
     * @param name Eindeutiger Schiffsname
     * @param sector Startsektor (0-99, 0-99)
     * @param direction Startrichtung
     * @return true wenn erfolgreich
     * @throws IOException bei Kommunikationsfehler
     */
    public boolean launch(String name, Vec2D sector, Vec2D direction) throws IOException {
        String command = CommandFactory.launch(name, VehicleType.ship, sector, direction);
        String response = sendCommand(command);

        JSONObject json = new JSONObject(response);
        boolean success = json.optString("status", "").equals("ok");

        if (success) {
            logger.info("Schiff '{}' erfolgreich gestartet bei {}", name, sector);
        } else {
            String error = json.optString("error", "Unbekannter Fehler");
            logger.error("Launch fehlgeschlagen: {}", error);
        }

        return success;
    }

    /**
     * Bewegt das Schiff in die angegebene Richtung.
     *
     * @param rudder Lenkrichtung (Left, Center, Right)
     * @param course Fahrtrichtung (Forward, Backward)
     * @return Neue Position nach der Bewegung, oder null bei Fehler
     * @throws IOException bei Kommunikationsfehler
     */
    public NavigateResult navigate(Rudder rudder, Course course) throws IOException {
        String command = CommandFactory.navigate(rudder, course);
        String response = sendCommand(command);

        JSONObject json = new JSONObject(response);

        if (json.has("error")) {
            logger.error("Navigate fehlgeschlagen: {}", json.getString("error"));
            return null;
        }

        // Antwort enthält neue Position und Richtung
        Vec2D newPosition = Vec2D.fromJson(json.getJSONArray("sector"));
        Vec2D newDirection = Vec2D.fromJson(json.getJSONArray("direction"));

        logger.debug("Schiff bewegt zu {} mit Richtung {}", newPosition, newDirection);

        return new NavigateResult(newPosition, newDirection);
    }

    /**
     * Führt einen Radar-Scan der 8 Nachbarsektoren durch.
     *
     * @return Liste der RadarEcho-Objekte für alle Nachbarsektoren
     * @throws IOException bei Kommunikationsfehler
     */
    public List<RadarEcho> radar() throws IOException {
        String command = CommandFactory.radar();
        String response = sendCommand(command);

        JSONObject json = new JSONObject(response);
        List<RadarEcho> echoes = new ArrayList<>();

        if (json.has("error")) {
            logger.error("Radar fehlgeschlagen: {}", json.getString("error"));
            return echoes;
        }

        JSONArray echoArray = json.getJSONArray("echoes");
        for (int i = 0; i < echoArray.length(); i++) {
            JSONObject echoJson = echoArray.getJSONObject(i);
            RadarEcho echo = RadarEcho.fromJson(echoJson);
            if (echo != null) {
                echoes.add(echo);
            }
        }

        logger.debug("Radar: {} Sektoren gescannt", echoes.size());
        return echoes;
    }

    /**
     * Führt einen Tiefen-Scan im aktuellen Sektor durch.
     *
     * @return ScanResult mit Tiefe und Standardabweichung
     * @throws IOException bei Kommunikationsfehler
     */
    public ScanResult scan() throws IOException {
        String command = CommandFactory.scan();
        String response = sendCommand(command);

        JSONObject json = new JSONObject(response);

        if (json.has("error")) {
            logger.error("Scan fehlgeschlagen: {}", json.getString("error"));
            return null;
        }

        Vec2D sector = Vec2D.fromJson(json.getJSONArray("sector"));
        int depth = json.getInt("depth");
        float stdDev = json.getFloat("deviation");

        ScanResult result = new ScanResult(sector, depth, stdDev);
        logger.debug("Scan: {}", result);

        return result;
    }

    /**
     * Sendet einen Befehl an den Server und wartet auf Antwort.
     *
     * @param command JSON-Befehl als String
     * @return Server-Antwort als String
     * @throws IOException bei Kommunikationsfehler
     */
    private String sendCommand(String command) throws IOException {
        if (!connected) {
            throw new IOException("Nicht mit OceanServer verbunden!");
        }

        System.out.println(">>> Sende: " + command);
        out.println(command);
        out.flush();

        String response = in.readLine();
        System.out.println("<<< Empfangen: " + response);

        if (response == null) {
            throw new IOException("Verbindung zum Server verloren");
        }

        return response;
    }

    /**
     * Prüft ob eine Verbindung besteht.
     *
     * @return true wenn verbunden
     */
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    /**
     * Ergebnis einer Navigate-Operation.
     */
    public record NavigateResult(Vec2D position, Vec2D direction) {}
}

