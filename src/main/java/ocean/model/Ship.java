package ocean.model;

public class Ship {

    private final String name;
    private Vec2D position;
    private Vec2D direction;

    public static final int MAX_SUBMARINES = 4;

    public Ship(String name) {
        this.name = name;
        this.position  = new Vec2D(0, 0);
        this.direction = new Vec2D(0, 1);
    }

    public Ship(String name, Vec2D position, Vec2D direction) {
        this.name = name;
        this.position  = new Vec2D(position);
        this.direction = new Vec2D(direction);
    }

    public String getName() { return name; }

    public Vec2D getPosition() { return position; }
    public void setPosition(Vec2D position) { this.position = new Vec2D(position); }

    public Vec2D getDirection() { return direction; }
    public void setDirection(Vec2D direction) { this.direction = new Vec2D(direction); }

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
