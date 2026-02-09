package ocean.model;

/**
 * Repräsentiert ein Forschungsschiff im Ocean Explorer System.
 *
 * Ein Schiff hat eine Position (Sektor), eine Fahrtrichtung und kann
 * bis zu 4 Submarines kontrollieren.
 *
 * @author OceanExplorer Team
 */
public class Ship {

    /** Eindeutiger Name des Schiffs */
    private final String name;

    /** Aktuelle Position als Sektor-Koordinaten (0-99, 0-99) */
    private Vec2D position;

    /** Aktuelle Fahrtrichtung als Richtungsvektor */
    private Vec2D direction;

    /** Maximale Anzahl Submarines pro Schiff */
    public static final int MAX_SUBMARINES = 4;

    /**
     * Erstellt ein neues Schiff mit Namen.
     * Position und Richtung werden beim Launch vom OceanServer gesetzt.
     *
     * @param name Eindeutiger Schiffsname
     */
    public Ship(String name) {
        this.name = name;
        this.position = new Vec2D(0, 0);
        this.direction = new Vec2D(0, 1); // Standardrichtung: Nord
    }

    /**
     * Erstellt ein Schiff mit allen Parametern.
     *
     * @param name Schiffsname
     * @param position Startposition
     * @param direction Startrichtung
     */
    public Ship(String name, Vec2D position, Vec2D direction) {
        this.name = name;
        this.position = new Vec2D(position);
        this.direction = new Vec2D(direction);
    }

    // === Getter und Setter ===

    public String getName() {
        return name;
    }

    public Vec2D getPosition() {
        return position;
    }

    public void setPosition(Vec2D position) {
        this.position = new Vec2D(position);
    }

    public Vec2D getDirection() {
        return direction;
    }

    public void setDirection(Vec2D direction) {
        this.direction = new Vec2D(direction);
    }

    /**
     * Prüft ob die Position innerhalb des gültigen Bereichs liegt (0-99).
     *
     * @return true wenn Position gültig
     */
    public boolean isPositionValid() {
        int x = position.getX();
        int y = position.getY();
        return x >= 0 && x <= 99 && y >= 0 && y <= 99;
    }

    @Override
    public String toString() {
        return "Ship[" + name + " at " + position + " dir=" + direction + "]";
    }
}

