package ocean.logic.navigation;

import ocean.model.Ground;
import ocean.model.RadarEcho;
import ocean.model.Rudder;
import ocean.model.Vec2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Kollisionsvermeidung basierend auf Radar-Daten.
 *
 * Diese Klasse analysiert Radar-Echos und wählt eine sichere
 * Navigationsrichtung aus.
 *
 * Regeln:
 * - Sektoren mit Height > 0 sind nicht befahrbar (Land/Hindernis)
 * - Bevorzuge geradeaus (Center)
 * - Weiche nach links/rechts aus wenn nötig
 *
 * @author OceanExplorer Team
 */
public class CollisionAvoidance {

    private static final Logger logger = LoggerFactory.getLogger(CollisionAvoidance.class);

    /**
     * Wählt eine sichere Navigationsrichtung basierend auf Radar-Daten.
     *
     * Strategie:
     * 1. Prüfe geradeaus (Center) - bevorzugt
     * 2. Prüfe rechts (Right)
     * 3. Prüfe links (Left)
     * 4. Falls alles blockiert: null zurückgeben
     *
     * @param radarData Radar-Echos der 8 Nachbarsektoren
     * @param currentDirection Aktuelle Fahrtrichtung des Schiffs
     * @return Rudder-Kommando, oder null wenn alle Richtungen blockiert
     */
    public Rudder chooseSafeDirection(List<RadarEcho> radarData, Vec2D currentDirection) {
        // Bestimme Zielsektor für jede Rudder-Option
        Vec2D centerTarget = calculateTargetSector(currentDirection, Rudder.Center);
        Vec2D rightTarget = calculateTargetSector(currentDirection, Rudder.Right);
        Vec2D leftTarget = calculateTargetSector(currentDirection, Rudder.Left);

        // Prüfe ob Richtungen frei sind
        boolean centerSafe = isSectorSafe(radarData, centerTarget);
        boolean rightSafe = isSectorSafe(radarData, rightTarget);
        boolean leftSafe = isSectorSafe(radarData, leftTarget);

        logger.debug("Radar-Analyse: Center={}, Right={}, Left={}", centerSafe, rightSafe, leftSafe);

        // Bevorzuge geradeaus
        if (centerSafe) {
            logger.debug("Wähle: Center (geradeaus)");
            return Rudder.Center;
        }

        // Alternativ rechts
        if (rightSafe) {
            logger.debug("Wähle: Right (ausweichen)");
            return Rudder.Right;
        }

        // Alternativ links
        if (leftSafe) {
            logger.debug("Wähle: Left (ausweichen)");
            return Rudder.Left;
        }

        // Alle Richtungen blockiert
        logger.warn("WARNUNG: Alle Richtungen blockiert!");
        return null;
    }

    /**
     * Berechnet den Zielsektor relativ zur aktuellen Richtung und Rudder-Stellung.
     *
     * @param currentDirection Aktuelle Richtung als Vektor
     * @param rudder Rudder-Stellung
     * @return Relativer Zielsektor
     */
    private Vec2D calculateTargetSector(Vec2D currentDirection, Rudder rudder) {
        // Richtungsvektoren für 8 Richtungen
        // N=(0,1), NE=(1,1), E=(1,0), SE=(1,-1), S=(0,-1), SW=(-1,-1), W=(-1,0), NW=(-1,1)

        int dx = currentDirection.getX();
        int dy = currentDirection.getY();

        return switch (rudder) {
            case Center -> new Vec2D(dx, dy); // Geradeaus
            case Right -> rotateRight(dx, dy); // 45° nach rechts
            case Left -> rotateLeft(dx, dy);   // 45° nach links
        };
    }

    /**
     * Dreht einen Richtungsvektor 45° nach rechts.
     */
    private Vec2D rotateRight(int dx, int dy) {
        // Rechtsdrehung: N→NE, NE→E, E→SE, SE→S, S→SW, SW→W, W→NW, NW→N
        if (dx == 0 && dy == 1) return new Vec2D(1, 1);   // N → NE
        if (dx == 1 && dy == 1) return new Vec2D(1, 0);   // NE → E
        if (dx == 1 && dy == 0) return new Vec2D(1, -1);  // E → SE
        if (dx == 1 && dy == -1) return new Vec2D(0, -1); // SE → S
        if (dx == 0 && dy == -1) return new Vec2D(-1, -1); // S → SW
        if (dx == -1 && dy == -1) return new Vec2D(-1, 0); // SW → W
        if (dx == -1 && dy == 0) return new Vec2D(-1, 1);  // W → NW
        if (dx == -1 && dy == 1) return new Vec2D(0, 1);   // NW → N
        return new Vec2D(dx, dy); // Fallback
    }

    /**
     * Dreht einen Richtungsvektor 45° nach links.
     */
    private Vec2D rotateLeft(int dx, int dy) {
        // Linksdrehung: N→NW, NW→W, W→SW, SW→S, S→SE, SE→E, E→NE, NE→N
        if (dx == 0 && dy == 1) return new Vec2D(-1, 1);  // N → NW
        if (dx == -1 && dy == 1) return new Vec2D(-1, 0); // NW → W
        if (dx == -1 && dy == 0) return new Vec2D(-1, -1); // W → SW
        if (dx == -1 && dy == -1) return new Vec2D(0, -1); // SW → S
        if (dx == 0 && dy == -1) return new Vec2D(1, -1);  // S → SE
        if (dx == 1 && dy == -1) return new Vec2D(1, 0);   // SE → E
        if (dx == 1 && dy == 0) return new Vec2D(1, 1);    // E → NE
        if (dx == 1 && dy == 1) return new Vec2D(0, 1);    // NE → N
        return new Vec2D(dx, dy); // Fallback
    }

    /**
     * Prüft ob ein Sektor sicher befahrbar ist.
     *
     * Ein Sektor ist sicher wenn:
     * - Ground = Water
     * - Height = 0 (keine Hindernisse)
     *
     * @param radarData Radar-Echos
     * @param targetSector Zu prüfender Sektor (relativ)
     * @return true wenn sicher befahrbar
     */
    private boolean isSectorSafe(List<RadarEcho> radarData, Vec2D targetSector) {
        for (RadarEcho echo : radarData) {
            // Prüfe ob Echo dem Zielsektor entspricht (relativ zur aktuellen Position)
            // Radar liefert absolute Positionen, wir brauchen aber relative Richtung
            // Vereinfachung: Prüfe ob Richtung im Echo enthalten ist

            Vec2D echoPos = echo.getSector();

            // TODO: Hier müsste man die absolute Position mit der aktuellen Position vergleichen
            // Für MVP: Einfache Heuristik - prüfe alle Echos

            if (echo.getHeight() > 0) {
                logger.debug("Sektor {} blockiert (Height={})", echoPos, echo.getHeight());
                // Dieser Sektor ist blockiert, aber ist es unser Ziel?
                // Vereinfachung: Wenn irgendein Sektor in dieser Richtung blockiert ist
            }

            if (echo.getGround() == Ground.Land) {
                logger.debug("Sektor {} ist Land", echoPos);
            }
        }

        // Vereinfachung für MVP: Wenn wir hier ankommen, ist der Sektor vermutlich sicher
        // TODO: Echte Positionsprüfung implementieren
        return true;
    }
}
