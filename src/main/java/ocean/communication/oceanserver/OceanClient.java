package ocean.communication.oceanserver;

import ocean.model.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
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
public class OceanClient {

    private static final Logger logger = LoggerFactory.getLogger(OceanClient.class);

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
    public OceanClient(String host, int port) {
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
        socket.setSoTimeout(30_000); // 30 Sekunden Lese-Timeout – verhindert endloses Hängen
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        connected = true;

        System.out.println("[DEBUG] Verbindung hergestellt!");
        logger.info("Verbunden mit OceanServer: {}:{}", host, port);
    }

    /**
     * Trennt die Verbindung zum OceanServer.
     */
    public void disconnect() {
        logger.info("Trenne Verbindung zum OceanServer...");

        try {
            if (connected && out != null) {
                // Exit-Befehl senden – Server antwortet NICHT, schließt nur die Verbindung
                String exitCmd = CommandFactory.exit();
                System.out.println(">>> Sende: " + exitCmd);
                out.println(exitCmd);
                out.flush();
            }
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            logger.warn("Fehler beim Schließen der Verbindung: {}", e.getMessage());
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

        // Server antwortet mit "cmd":"launched" bei Erfolg
        // Protokoll (2 Nachrichten!):
        //   1. {"cmd":"launched","id":"...","abspos":{"vec2":[...]}}
        //   2. {"cmd":"move2d","sector":...,"dir":...}    ← muss auch gelesen werden!
        String cmd = json.optString("cmd", "");
        boolean success = cmd.equals("launched");

        if (success) {
            logger.info("Schiff '{}' erfolgreich gestartet bei {}", name, sector);

            // WICHTIG: Server sendet nach "launched" noch eine "move2d" Nachricht.
            // Diese muss gelesen werden, sonst verrutscht der gesamte Nachrichten-Buffer!
            String move2dLine = in.readLine();
            if (move2dLine != null) {
                System.out.println("<<< Empfangen (move2d): " + move2dLine);
                logger.debug("Launch-Folgenachricht gelesen: {}", move2dLine);
            } else {
                throw new IOException("Verbindung verloren beim Lesen der move2d-Nachricht nach launch");
            }
        } else {
            String error = json.optString("error", "Unbekannter Fehler");
            logger.error("Launch fehlgeschlagen: {} | Vollständige Antwort: {}", error, response);
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

        // Protokoll: Server antwortet mit "cmd":"move2d"
        String cmd = json.optString("cmd", "");
        if (!cmd.equals("move2d")) {
            String error = json.optString("error", "Unbekannter Fehler");
            logger.error("Navigate fehlgeschlagen: {}", error);
            return null;
        }

        // Protokoll: Felder sind "sector" und "dir" (als vec2)
        Vec2D newPosition = Vec2D.fromJson(json.getJSONObject("sector"));
        Vec2D newDirection = Vec2D.fromJson(json.getJSONObject("dir"));

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

        // Protokoll: Server antwortet mit "cmd":"radarresponse"
        String cmd = json.optString("cmd", "");
        if (!cmd.equals("radarresponse")) {
            String error = json.optString("error", "Unbekannter Fehler");
            logger.error("Radar fehlgeschlagen: {}", error);
            return echoes;
        }

        // Protokoll: Feld heißt "echos" (nicht "echoes")
        JSONArray echoArray = json.getJSONArray("echos");
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

        // Protokoll: Server antwortet mit "cmd":"scanned"
        String cmd = json.optString("cmd", "");
        if (!cmd.equals("scanned")) {
            String error = json.optString("error", "Unbekannter Fehler");
            logger.error("Scan fehlgeschlagen: {}", error);
            return null;
        }

        // Protokoll: Felder sind "depth" und "stddev"
        // Aktueller Sektor muss aus vorheriger Position bekannt sein
        // oder wir erstellen einen Dummy-Sector (0,0)
        int depth = json.getInt("depth");
        float stdDev = (float) json.getDouble("stddev");

        // TODO: Sektor aus aktuellem Schiffskontext holen
        Vec2D sector = new Vec2D(0, 0);

        ScanResult result = new ScanResult(sector, depth, stdDev);
        logger.debug("Scan: depth={}, stddev={}", depth, stdDev);

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

        String response;
        try {
            response = in.readLine();
        } catch (SocketTimeoutException e) {
            connected = false;
            throw new IOException(
                "OceanServer hat nach 30 Sekunden NICHT geantwortet!\n" +
                "  → Schiff-Name bereits vergeben? Bitte OceanServer neu starten.\n" +
                "  → Läuft der OceanServer noch?"
            );
        }

        if (response == null) {
            connected = false;
            throw new IOException(
                "OceanServer hat die Verbindung geschlossen!\n" +
                "  → Wurde in der OceanServer-GUI auf 'Start' geklickt?\n" +
                "  → Läuft der OceanServer noch?"
            );
        }

        System.out.println("<<< Empfangen: " + response);
        return response;
    }

    /**
     * Prüft, ob eine Verbindung besteht.
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

