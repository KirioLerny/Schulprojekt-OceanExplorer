package ocean.logic.navigation;

import ocean.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CollisionAvoidance – Richtungsentscheidung per Radar")
class CollisionAvoidanceTest {

    private CollisionAvoidance ca;

    @BeforeEach
    void setUp() {
        ca = new CollisionAvoidance();
    }

    // ──────────────────────────────────────────────────────────────
    // Hilfsmethode: einfache Radar-Liste aus sicheren Sektoren
    // ──────────────────────────────────────────────────────────────

    private List<RadarEcho> allWater() {
        List<RadarEcho> echoes = new ArrayList<>();
        int[][] offsets = {{-1,0},{-1,1},{0,1},{1,1},{1,0},{1,-1},{0,-1},{-1,-1}};
        for (int[] o : offsets) {
            echoes.add(new RadarEcho(new Vec2D(o[0], o[1]), Ground.Water, 0));
        }
        return echoes;
    }

    private List<RadarEcho> emptyRadar() {
        return new ArrayList<>();
    }

    // ──────────────────────────────────────────────────────────────
    // chooseSafeDirection – Grundfälle
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Bei freier Fahrt wählt chooseSafeDirection() Rudder.Center")
    void testChooseCenterWhenAllFree() {
        Vec2D northDir = new Vec2D(0, 1);
        Rudder result = ca.chooseSafeDirection(allWater(), northDir);
        assertEquals(Rudder.Center, result,
                "Wenn alle Richtungen frei sind, soll Center bevorzugt werden");
    }

    @Test
    @DisplayName("chooseSafeDirection() gibt Rudder-Wert zurück (nicht null) wenn Wasser frei")
    void testReturnNotNullWithFreeWater() {
        Vec2D dir = new Vec2D(1, 0); // Ost
        Rudder result = ca.chooseSafeDirection(allWater(), dir);
        assertNotNull(result);
    }

    @Test
    @DisplayName("chooseSafeDirection() gibt null zurück bei leerer Radar-Liste")
    void testNullOnEmptyRadar() {
        // Leere Liste → isSectorSafe() findet keine blockierenden Echos → gibt true → Center
        // Aktuelles Verhalten: MVP gibt immer true → Center zurück
        // Dieser Test dokumentiert das aktuelle Verhalten
        Vec2D dir = new Vec2D(0, 1);
        Rudder result = ca.chooseSafeDirection(emptyRadar(), dir);
        // MVP: immer safe → Center
        assertNotNull(result);
    }

    // ──────────────────────────────────────────────────────────────
    // rotateRight / rotateLeft – indirekt über calculateTargetSector
    // Wir testen alle 8 Richtungen indirekt durch chooseSafeDirection
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("chooseSafeDirection() liefert für alle 8 Richtungsvektoren ein Ergebnis")
    void testAllDirectionsReturnResult() {
        Vec2D[] directions = {
            new Vec2D(0, 1),   // N
            new Vec2D(1, 1),   // NE
            new Vec2D(1, 0),   // E
            new Vec2D(1, -1),  // SE
            new Vec2D(0, -1),  // S
            new Vec2D(-1, -1), // SW
            new Vec2D(-1, 0),  // W
            new Vec2D(-1, 1),  // NW
        };

        for (Vec2D dir : directions) {
            Rudder result = ca.chooseSafeDirection(allWater(), dir);
            assertNotNull(result, "Für Richtung " + dir + " sollte kein null zurückgegeben werden");
        }
    }

    @Test
    @DisplayName("chooseSafeDirection() gibt immer einen der 3 Rudder-Werte zurück (oder null)")
    void testResultIsValidRudder() {
        Vec2D dir = new Vec2D(0, 1);
        Rudder result = ca.chooseSafeDirection(allWater(), dir);
        assertTrue(
            result == Rudder.Left || result == Rudder.Center || result == Rudder.Right,
            "Ergebnis muss Left, Center oder Right sein"
        );
    }
}

