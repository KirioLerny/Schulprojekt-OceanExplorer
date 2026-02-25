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
 * TCP-Client fuer die Kommunikation mit dem OceanServer.
 *
 * <pre>
 * Stellt die Verbindung zum OceanServer her und bietet Methoden
 * fuer alle verfuegbaren Befehle.
 *
 * Verwendung:
 *   OceanClient client = new OceanClient("localhost", 3000);
 *   client.connect();
 *   client.launch("MeinSchiff", new Vec2D(50, 50), new Vec2D(0, 1));
 *   List&lt;RadarEcho&gt; radar = client.radar();
 *   client.disconnect();
 * </pre>
 */
public class OceanClient {

    private static final Logger logger = LoggerFactory.getLogger(OceanClient.class);

    private final String host;
    private final int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = false;
    private String shipServerId = null;

    /**
     * Erstellt einen neuen OceanClient.
     *
     * @param host Server-Hostname (z.B. "localhost")
     * @param port Server-Port
     */
    public OceanClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Stellt die Verbindung zum OceanServer her.
     *
     * @throws IOException wenn Verbindung fehlschlaegt
     */
    public void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(30_000);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        connected = true;
        logger.info("Verbunden mit OceanServer: {}:{}", host, port);
    }

    /**
     * Trennt die Verbindung zum OceanServer.
     */
    public void disconnect() {
        logger.info("Trenne Verbindung zum OceanServer...");
        try {
            if (connected && out != null) {
                String exitCmd = CommandFactory.exit();
                out.println(exitCmd);
                out.flush();
            }
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            logger.warn("Fehler beim Schliessen der Verbindung: {}", e.getMessage());
        } finally {
            connected = false;
        }
        logger.info("Verbindung getrennt.");
    }

    /**
     * Startet ein Schiff im OceanServer.
     *
     * @param name      Eindeutiger Schiffsname
     * @param sector    Startsektor (0-99, 0-99)
     * @param direction Startrichtung
     * @return true wenn erfolgreich
     * @throws IOException bei Kommunikationsfehler
     */
    public boolean launch(String name, Vec2D sector, Vec2D direction) throws IOException {
        String command = CommandFactory.launch(name, VehicleType.ship, sector, direction);
        String response = sendCommand(command);

        JSONObject json = new JSONObject(response);
        String cmd = json.optString("cmd", "");
        boolean success = cmd.equals("launched");

        if (success) {
            shipServerId = json.optString("id", null);
            logger.info("Schiff '{}' erfolgreich gestartet bei {} (Server-ID: {})", name, sector, shipServerId);

            String move2dLine = in.readLine();
            if (move2dLine != null) {
                logger.debug("Launch-Folgenachricht gelesen: {}", move2dLine);
            } else {
                throw new IOException("Verbindung verloren beim Lesen der move2d-Nachricht nach launch");
            }
        } else {
            String error = json.optString("error", "Unbekannter Fehler");
            logger.error("Launch fehlgeschlagen: {} | Vollstaendige Antwort: {}", error, response);
        }

        return success;
    }

    /**
     * Bewegt das Schiff in die angegebene Richtung.
     *
     * @param rudder Lenkrichtung (Left, Center, Right)
     * @param course Fahrtrichtung (Forward, Backward)
     * @return Neue Position und Richtung, oder null bei Fehler
     * @throws IOException bei Kommunikationsfehler
     */
    public NavigateResult navigate(Rudder rudder, Course course) throws IOException {
        String command = CommandFactory.navigate(rudder, course);
        String response = sendCommand(command);

        JSONObject json = new JSONObject(response);
        String cmd = json.optString("cmd", "");
        if (!cmd.equals("move2d")) {
            String error = json.optString("error", "Unbekannter Fehler");
            logger.error("Navigate fehlgeschlagen: {}", error);
            return null;
        }

        Vec2D newPosition  = Vec2D.fromJson(json.getJSONObject("sector"));
        Vec2D newDirection = Vec2D.fromJson(json.getJSONObject("dir"));
        logger.debug("Schiff bewegt zu {} mit Richtung {}", newPosition, newDirection);

        return new NavigateResult(newPosition, newDirection);
    }

    /**
     * Fuehrt einen Radar-Scan der 8 Nachbarsektoren durch.
     *
     * @return Liste der RadarEcho-Objekte fuer alle Nachbarsektoren
     * @throws IOException bei Kommunikationsfehler
     */
    public List<RadarEcho> radar() throws IOException {
        String command = CommandFactory.radar();
        String response = sendCommand(command);

        JSONObject json = new JSONObject(response);
        List<RadarEcho> echoes = new ArrayList<>();

        String cmd = json.optString("cmd", "");
        if (!cmd.equals("radarresponse")) {
            String error = json.optString("error", "Unbekannter Fehler");
            logger.error("Radar fehlgeschlagen: {}", error);
            return echoes;
        }

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
     * Fuehrt einen Tiefen-Scan im aktuellen Sektor durch.
     *
     * @return ScanResult mit Tiefe und Standardabweichung, oder null bei Fehler
     * @throws IOException bei Kommunikationsfehler
     */
    public ScanResult scan() throws IOException {
        String command = CommandFactory.scan();
        String response = sendCommand(command);

        JSONObject json = new JSONObject(response);
        String cmd = json.optString("cmd", "");
        if (!cmd.equals("scanned")) {
            String error = json.optString("error", "Unbekannter Fehler");
            logger.error("Scan fehlgeschlagen: {}", error);
            return null;
        }

        int depth = json.getInt("depth");
        float stdDev = (float) json.getDouble("stddev");
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

        logger.debug(">>> {}", command);
        out.println(command);
        out.flush();

        String response;
        try {
            response = in.readLine();
        } catch (SocketTimeoutException e) {
            connected = false;
            throw new IOException(
                "OceanServer hat nach 30 Sekunden NICHT geantwortet!\n" +
                "  Schiff-Name bereits vergeben? Bitte OceanServer neu starten.\n" +
                "  Laeuft der OceanServer noch?"
            );
        }

        if (response == null) {
            connected = false;
            throw new IOException(
                "OceanServer hat die Verbindung geschlossen!\n" +
                "  Wurde in der OceanServer-GUI auf 'Start' geklickt?\n" +
                "  Laeuft der OceanServer noch?"
            );
        }

        logger.debug("<<< {}", response);
        return response;
    }

    /**
     * Gibt die vom OceanServer vergebene Ship-ID zurueck (z.B. "#1#Explorer-220556").
     * Nur verfuegbar nach erfolgreichem launch().
     *
     * @return Server-ID oder null wenn noch kein launch() durchgefuehrt
     */
    public String getShipServerId() {
        return shipServerId;
    }

    /**
     * Prueft, ob eine Verbindung besteht.
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

