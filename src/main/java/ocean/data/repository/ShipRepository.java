package ocean.data.repository;

import ocean.data.DatabaseConnection;
import ocean.model.Ship;
import ocean.model.Vec2D;
import ocean.model.VehicleType;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.jooq.impl.DSL.*;

/**
 * Repository fuer Ship-Datenbank-Operationen.
 *
 * <pre>
 * Speichert und laedt Schiffsdaten aus der Datenbank.
 * </pre>
 */
public class ShipRepository {

    private static final Logger logger = LoggerFactory.getLogger(ShipRepository.class);

    private final DSLContext dsl;

    public ShipRepository(DatabaseConnection dbConnection) {
        this.dsl = dbConnection.getDSL();
    }

    /**
     * Speichert ein neues Schiff in der Datenbank.
     * Existiert bereits ein Schiff mit diesem Namen (z.B. aus einem frueheren Lauf),
     * wird es reaktiviert und die Position aktualisiert.
     *
     * @param ship Zu speicherndes Schiff
     * @return ID des gespeicherten Schiffs
     */
    public long save(Ship ship) {
        logger.debug("Speichere Schiff: {}", ship.getName());

        Vec2D pos = ship.getPosition();
        Vec2D dir = ship.getDirection();

        int posX = pos != null ? pos.getX() : 0;
        int posY = pos != null ? pos.getY() : 0;
        int dirX = dir != null ? dir.getX() : 0;
        int dirY = dir != null ? dir.getY() : 1;

        dsl.execute(
            "INSERT INTO ship (name, vehicle_type, current_x, current_y, direction_x, direction_y, active) " +
            "VALUES (?, ?, ?, ?, ?, ?, 1) " +
            "ON DUPLICATE KEY UPDATE " +
            "  current_x = VALUES(current_x), current_y = VALUES(current_y), " +
            "  direction_x = VALUES(direction_x), direction_y = VALUES(direction_y), " +
            "  active = 1",
            ship.getName(), VehicleType.ship.name(), posX, posY, dirX, dirY
        );

        Record idRecord = dsl.select(field("id"))
                .from(table("ship"))
                .where(field("name").eq(ship.getName()))
                .fetchOne();

        if (idRecord == null) {
            throw new RuntimeException("Schiff konnte nicht gespeichert werden");
        }
        long id = idRecord.get(field("id", Long.class));
        logger.info("Schiff gespeichert/reaktiviert: {} (ID: {})", ship.getName(), id);
        return id;
    }

    /**
     * Aktualisiert Position und Richtung eines Schiffs.
     *
     * @param shipName  Name des Schiffs
     * @param position  Neue Position
     * @param direction Neue Richtung
     */
    public void updatePosition(String shipName, Vec2D position, Vec2D direction) {
        logger.debug("Aktualisiere Position von {}: {}", shipName, position);

        int updated = dsl.update(table("ship"))
                .set(field("current_x"), position.getX())
                .set(field("current_y"), position.getY())
                .set(field("direction_x"), direction.getX())
                .set(field("direction_y"), direction.getY())
                .where(field("name").eq(shipName))
                .execute();

        if (updated > 0) {
            logger.debug("Position aktualisiert");
        } else {
            logger.warn("Schiff {} nicht gefunden", shipName);
        }
    }

    /**
     * Laedt ein Schiff nach Name aus der Datenbank.
     *
     * @param name Name des Schiffs
     * @return Ship oder null wenn nicht gefunden
     */
    public Ship findByName(String name) {
        Record record = dsl.select()
                .from(table("ship"))
                .where(field("name").eq(name))
                .fetchOne();

        if (record == null) {
            return null;
        }

        return mapToShip(record);
    }

    /**
     * Markiert ein Schiff als inaktiv.
     *
     * @param shipName Name des Schiffs
     */
    public void deactivate(String shipName) {
        // Erst alle offenen Submarines dieses Schiffs deaktivieren
        dsl.execute(
            "UPDATE submarine SET active = 0 WHERE ship_id = (SELECT id FROM ship WHERE name = ?) AND active = 1",
            shipName
        );
        dsl.update(table("ship"))
                .set(field("active"), 0)
                .where(field("name").eq(shipName))
                .execute();
        logger.debug("Schiff {} und alle zugehoerigen Submarines deaktiviert", shipName);
    }

    /**
     * Laedt alle aktiven Schiffe.
     *
     * @return Liste aller Schiffe
     */
    public List<Ship> findAll() {
        var records = dsl.select()
                .from(table("ship"))
                .where(field("active").eq(1))
                .fetch();

        List<Ship> ships = new ArrayList<>();
        for (Record record : records) {
            ships.add(mapToShip(record));
        }

        return ships;
    }

    /**
     * Mappt einen Datenbank-Record zu einem Ship-Objekt.
     *
     * @param record Datenbank-Record
     * @return Ship-Objekt
     */
    private Ship mapToShip(Record record) {
        String  name = record.get(field("name",        String.class));
        Integer posX = record.get(field("current_x",   Integer.class));
        Integer posY = record.get(field("current_y",   Integer.class));
        Integer dirX = record.get(field("direction_x", Integer.class));
        Integer dirY = record.get(field("direction_y", Integer.class));

        Vec2D position  = (posX != null && posY != null) ? new Vec2D(posX, posY) : null;
        Vec2D direction = (dirX != null && dirY != null) ? new Vec2D(dirX, dirY) : null;

        return new Ship(name, position, direction);
    }

    /**
     * Laedt die Schiffs-ID nach Name.
     *
     * @param name Schiffsname
     * @return ID oder null wenn nicht gefunden
     */
    public Long getIdByName(String name) {
        Record record = dsl.select(field("id"))
                .from(table("ship"))
                .where(field("name").eq(name))
                .fetchOne();

        return record != null ? record.get(field("id", Long.class)) : null;
    }
}
