package ocean.communication.oceanserver;

import ocean.model.Course;
import ocean.model.Rudder;
import ocean.model.Vec2D;
import ocean.model.VehicleType;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory-Klasse zum Erstellen von JSON-Befehlen für den OceanServer.
 *
 * Der OceanServer erwartet JSON-Nachrichten in einem bestimmten Format.
 * Diese Klasse kapselt die Erstellung dieser Nachrichten.
 *
 * Verfügbare Befehle:
 * - launch: Schiff ins Meer setzen
 * - navigate: Schiff bewegen
 * - radar: Umgebung scannen (8 Nachbarsektoren)
 * - scan: Tiefenmessung im aktuellen Sektor
 * - exit: Verbindung beenden
 *
 * @author OceanExplorer Team
 */
public final class CommandFactory {

    // Privater Konstruktor verhindert Instanziierung
    private CommandFactory() {
    }

    /**
     * Erstellt einen Launch-Befehl zum Starten eines Schiffs.
     *
     * @param name Eindeutiger Schiffsname
     * @param type Fahrzeugtyp (ship/submarine)
     * @param sector Startsektor (0-99, 0-99)
     * @param direction Startrichtung als Richtungsvektor
     * @return JSON-String für den OceanServer
     */
    public static String launch(String name, VehicleType type, Vec2D sector, Vec2D direction) {
        // Baue JSON manuell - Feldnamen müssen exakt mit CmdLaunch.class übereinstimmen!
        // typ (nicht type!), dir (nicht direction!)
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"cmd\":\"launch\",");
        json.append("\"name\":\"").append(name).append("\",");
        json.append("\"typ\":\"").append(type.name()).append("\",");  // typ, nicht type!
        json.append("\"sector\":{\"vec2\":[").append(sector.getX()).append(",").append(sector.getY()).append("]},");
        json.append("\"dir\":{\"vec2\":[").append(direction.getX()).append(",").append(direction.getY()).append("]}");  // dir, nicht direction!
        json.append("}");
        return json.toString();
    }

    /**
     * Erstellt einen Navigate-Befehl zum Bewegen des Schiffs.
     *
     * @param rudder Lenkrichtung
     * @param course Fahrtrichtung
     * @return JSON-String für den OceanServer
     */
    public static String navigate(Rudder rudder, Course course) {
        JSONObject json = new JSONObject();
        json.put("cmd", "navigate");
        json.put("rudder", rudder.name());
        json.put("course", course.name());
        return json.toString();
    }

    /**
     * Erstellt einen Radar-Befehl zum Scannen der 8 Nachbarsektoren.
     *
     * @return JSON-String für den OceanServer
     */
    public static String radar() {
        JSONObject json = new JSONObject();
        json.put("cmd", "radar");
        return json.toString();
    }

    /**
     * Erstellt einen Scan-Befehl zur Tiefenmessung im aktuellen Sektor.
     *
     * @return JSON-String für den OceanServer
     */
    public static String scan() {
        JSONObject json = new JSONObject();
        json.put("cmd", "scan");
        return json.toString();
    }

    /**
     * Erstellt einen Exit-Befehl zum Beenden der Verbindung.
     *
     * @return JSON-String für den OceanServer
     */
    public static String exit() {
        JSONObject json = new JSONObject();
        json.put("cmd", "exit");
        return json.toString();
    }
}

