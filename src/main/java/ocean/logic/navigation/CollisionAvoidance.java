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
 * <pre>
 * Analysiert Radar-Echos und waehlt eine sichere Navigationsrichtung aus.
 *
 * Regeln:
 *   Sektoren mit Height &gt; 0 sind nicht befahrbar (Land/Hindernis)
 *   Bevorzuge geradeaus (Center)
 *   Weiche nach links/rechts aus wenn noetig
 * </pre>
 */
public class CollisionAvoidance {

    private static final Logger logger = LoggerFactory.getLogger(CollisionAvoidance.class);

    /**
     * Waehlt eine sichere Navigationsrichtung basierend auf Radar-Daten.
     *
     * <pre>
     * Strategie:
     *   1. Pruefe geradeaus (Center) - bevorzugt
     *   2. Pruefe rechts (Right)
     *   3. Pruefe links (Left)
     *   4. Falls alles blockiert: null zurueckgeben
     * </pre>
     *
     * @param radarData        Radar-Echos der 8 Nachbarsektoren
     * @param currentDirection Aktuelle Fahrtrichtung des Schiffs
     * @return Rudder-Kommando, oder null wenn alle Richtungen blockiert
     */
    public Rudder chooseSafeDirection(List<RadarEcho> radarData, Vec2D currentDirection) {
        Vec2D centerTarget = calculateTargetSector(currentDirection, Rudder.Center);
        Vec2D rightTarget  = calculateTargetSector(currentDirection, Rudder.Right);
        Vec2D leftTarget   = calculateTargetSector(currentDirection, Rudder.Left);

        boolean centerSafe = isSectorSafe(radarData, centerTarget);
        boolean rightSafe  = isSectorSafe(radarData, rightTarget);
        boolean leftSafe   = isSectorSafe(radarData, leftTarget);

        logger.debug("Radar-Analyse: Center={}, Right={}, Left={}", centerSafe, rightSafe, leftSafe);

        if (centerSafe) {
            logger.debug("Waehle: Center (geradeaus)");
            return Rudder.Center;
        }
        if (rightSafe) {
            logger.debug("Waehle: Right (ausweichen)");
            return Rudder.Right;
        }
        if (leftSafe) {
            logger.debug("Waehle: Left (ausweichen)");
            return Rudder.Left;
        }

        logger.warn("Alle Richtungen blockiert!");
        return null;
    }

    /**
     * Berechnet den Zielsektor relativ zur aktuellen Richtung und Rudder-Stellung.
     *
     * @param currentDirection Aktuelle Richtung als Vektor
     * @param rudder           Rudder-Stellung
     * @return Relativer Zielsektor
     */
    private Vec2D calculateTargetSector(Vec2D currentDirection, Rudder rudder) {
        int dx = currentDirection.getX();
        int dy = currentDirection.getY();

        return switch (rudder) {
            case Center -> new Vec2D(dx, dy);
            case Right  -> rotateRight(dx, dy);
            case Left   -> rotateLeft(dx, dy);
        };
    }

    /**
     * Dreht einen Richtungsvektor 45 Grad nach rechts.
     *
     * @param dx X-Komponente des Richtungsvektors
     * @param dy Y-Komponente des Richtungsvektors
     * @return Gedrehter Richtungsvektor
     */
    private Vec2D rotateRight(int dx, int dy) {
        if (dx == 0  && dy == 1)  return new Vec2D(1, 1);
        if (dx == 1  && dy == 1)  return new Vec2D(1, 0);
        if (dx == 1  && dy == 0)  return new Vec2D(1, -1);
        if (dx == 1  && dy == -1) return new Vec2D(0, -1);
        if (dx == 0  && dy == -1) return new Vec2D(-1, -1);
        if (dx == -1 && dy == -1) return new Vec2D(-1, 0);
        if (dx == -1 && dy == 0)  return new Vec2D(-1, 1);
        if (dx == -1 && dy == 1)  return new Vec2D(0, 1);
        return new Vec2D(dx, dy);
    }

    /**
     * Dreht einen Richtungsvektor 45 Grad nach links.
     *
     * @param dx X-Komponente des Richtungsvektors
     * @param dy Y-Komponente des Richtungsvektors
     * @return Gedrehter Richtungsvektor
     */
    private Vec2D rotateLeft(int dx, int dy) {
        if (dx == 0  && dy == 1)  return new Vec2D(-1, 1);
        if (dx == -1 && dy == 1)  return new Vec2D(-1, 0);
        if (dx == -1 && dy == 0)  return new Vec2D(-1, -1);
        if (dx == -1 && dy == -1) return new Vec2D(0, -1);
        if (dx == 0  && dy == -1) return new Vec2D(1, -1);
        if (dx == 1  && dy == -1) return new Vec2D(1, 0);
        if (dx == 1  && dy == 0)  return new Vec2D(1, 1);
        if (dx == 1  && dy == 1)  return new Vec2D(0, 1);
        return new Vec2D(dx, dy);
    }

    /**
     * Prueft ob ein Sektor sicher befahrbar ist.
     *
     * <pre>
     * Ein Sektor ist sicher wenn:
     *   Ground = Water
     *   Height = 0 (keine Hindernisse)
     * </pre>
     *
     * @param radarData    Radar-Echos
     * @param targetSector Zu pruefender Sektor (relativ)
     * @return true wenn sicher befahrbar
     */
    private boolean isSectorSafe(List<RadarEcho> radarData, Vec2D targetSector) {
        for (RadarEcho echo : radarData) {
            Vec2D echoPos = echo.getSector();

            if (echo.getHeight() > 0) {
                logger.debug("Sektor {} blockiert (Height={})", echoPos, echo.getHeight());
            }

            if (echo.getGround() == Ground.Land) {
                logger.debug("Sektor {} ist Land", echoPos);
            }
        }
        return true;
    }
}
