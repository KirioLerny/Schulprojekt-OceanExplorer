package ocean.communication.oceanserver;

import ocean.model.Course;
import ocean.model.Rudder;
import ocean.model.Vec2D;
import ocean.model.VehicleType;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CommandFactory – JSON-Befehl-Erzeugung")
class CommandFactoryTest {

    @Test
    @DisplayName("launch() erzeugt korrektes JSON mit allen Feldern")
    void testLaunch() {
        Vec2D sector    = new Vec2D(10, 20);
        Vec2D direction = new Vec2D(0, 1);
        String json = CommandFactory.launch("MeinSchiff", VehicleType.ship, sector, direction);

        JSONObject jo = new JSONObject(json);
        assertEquals("launch",    jo.getString("cmd"));
        assertEquals("MeinSchiff", jo.getString("name"));
        assertEquals("ship",      jo.getString("typ"));
        // sector und dir müssen als JSONObject enthalten sein
        assertTrue(jo.has("sector"));
        assertTrue(jo.has("dir"));
    }

    @Test
    @DisplayName("launch() enthält korrekte Sektor-Koordinaten")
    void testLaunchSectorCoordinates() {
        Vec2D sector    = new Vec2D(42, 77);
        Vec2D direction = new Vec2D(1, 0);
        String json = CommandFactory.launch("Schiff2", VehicleType.ship, sector, direction);

        JSONObject jo = new JSONObject(json);
        JSONObject sectorObj = jo.getJSONObject("sector");
        // Vec2D.toJson() erzeugt {"vec2":[x,y]}
        assertTrue(sectorObj.has("vec2"));
        assertEquals(42, sectorObj.getJSONArray("vec2").getInt(0));
        assertEquals(77, sectorObj.getJSONArray("vec2").getInt(1));
    }

    @Test
    @DisplayName("navigate() erzeugt korrektes JSON für alle Rudder/Course-Kombinationen")
    void testNavigate() {
        for (Rudder rudder : Rudder.values()) {
            for (Course course : Course.values()) {
                String json = CommandFactory.navigate(rudder, course);
                JSONObject jo = new JSONObject(json);
                assertEquals("navigate",    jo.getString("cmd"));
                assertEquals(rudder.name(), jo.getString("rudder"));
                assertEquals(course.name(), jo.getString("course"));
            }
        }
    }

    @Test
    @DisplayName("radar() erzeugt JSON mit cmd=radar")
    void testRadar() {
        String json = CommandFactory.radar();
        JSONObject jo = new JSONObject(json);
        assertEquals("radar", jo.getString("cmd"));
        assertEquals(1, jo.length(), "radar-JSON sollte genau 1 Feld haben");
    }

    @Test
    @DisplayName("scan() erzeugt JSON mit cmd=scan")
    void testScan() {
        String json = CommandFactory.scan();
        JSONObject jo = new JSONObject(json);
        assertEquals("scan", jo.getString("cmd"));
        assertEquals(1, jo.length(), "scan-JSON sollte genau 1 Feld haben");
    }

    @Test
    @DisplayName("exit() erzeugt JSON mit cmd=exit")
    void testExit() {
        String json = CommandFactory.exit();
        JSONObject jo = new JSONObject(json);
        assertEquals("exit", jo.getString("cmd"));
        assertEquals(1, jo.length(), "exit-JSON sollte genau 1 Feld haben");
    }

    @Test
    @DisplayName("Alle Befehle liefern gültiges JSON (nicht null, nicht leer)")
    void testAllCommandsReturnValidJson() {
        assertDoesNotThrow(() -> new JSONObject(CommandFactory.radar()));
        assertDoesNotThrow(() -> new JSONObject(CommandFactory.scan()));
        assertDoesNotThrow(() -> new JSONObject(CommandFactory.exit()));
        assertDoesNotThrow(() -> new JSONObject(
                CommandFactory.navigate(Rudder.Center, Course.Forward)));
        assertDoesNotThrow(() -> new JSONObject(
                CommandFactory.launch("X", VehicleType.ship, new Vec2D(0,0), new Vec2D(0,1))));
    }
}

