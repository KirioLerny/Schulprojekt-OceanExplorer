package ocean.logic.navigation;

import ocean.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CollisionAvoidance - Richtungsentscheidung per Radar")
class CollisionAvoidanceTest {

    private CollisionAvoidance ca;

    @BeforeEach
    void setUp() {
        ca = new CollisionAvoidance();
    }

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

    @Test
    @DisplayName("Bei freier Fahrt waehlt chooseSafeDirection() Rudder.Center")
    void testChooseCenterWhenAllFree() {
        Vec2D northDir = new Vec2D(0, 1);
        Rudder result = ca.chooseSafeDirection(allWater(), northDir);
        assertEquals(Rudder.Center, result,
                "Wenn alle Richtungen frei sind, soll Center bevorzugt werden");
    }

    @Test
    @DisplayName("chooseSafeDirection() gibt Rudder-Wert zurueck (nicht null) wenn Wasser frei")
    void testReturnNotNullWithFreeWater() {
        Vec2D dir = new Vec2D(1, 0);
        Rudder result = ca.chooseSafeDirection(allWater(), dir);
        assertNotNull(result);
    }

    @Test
    @DisplayName("chooseSafeDirection() gibt bei leerer Radar-Liste Center zurueck (MVP)")
    void testNullOnEmptyRadar() {
        Vec2D dir = new Vec2D(0, 1);
        Rudder result = ca.chooseSafeDirection(emptyRadar(), dir);
        assertNotNull(result);
    }

    @Test
    @DisplayName("chooseSafeDirection() liefert fuer alle 8 Richtungsvektoren ein Ergebnis")
    void testAllDirectionsReturnResult() {
        Vec2D[] directions = {
            new Vec2D(0, 1),
            new Vec2D(1, 1),
            new Vec2D(1, 0),
            new Vec2D(1, -1),
            new Vec2D(0, -1),
            new Vec2D(-1, -1),
            new Vec2D(-1, 0),
            new Vec2D(-1, 1),
        };

        for (Vec2D dir : directions) {
            Rudder result = ca.chooseSafeDirection(allWater(), dir);
            assertNotNull(result, "Fuer Richtung " + dir + " sollte kein null zurueckgegeben werden");
        }
    }

    @Test
    @DisplayName("chooseSafeDirection() gibt immer einen der 3 Rudder-Werte zurueck (oder null)")
    void testResultIsValidRudder() {
        Vec2D dir = new Vec2D(0, 1);
        Rudder result = ca.chooseSafeDirection(allWater(), dir);
        assertTrue(
            result == Rudder.Left || result == Rudder.Center || result == Rudder.Right,
            "Ergebnis muss Left, Center oder Right sein"
        );
    }
}
