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
 * Repository für Ship-Datenbank-Operationen.
 *
 * Speichert und lädt Schiffsdaten aus der Datenbank.
 *
 * @author OceanExplorer Team
 */
public class ShipRepository {

    private static final Logger logger = LoggerFactory.getLogger(ShipRepository.class);

    private final DSLContext dsl;

    /**
     * Erstellt ein neues ShipRepository.
     *
     * @param dbConnection Datenbank-Verbindung
     */
    public ShipRepository(DatabaseConnection dbConnection) {
        this.dsl = dbConnection.getDSL();
    }

    /**
     * Speichert ein neues Schiff in der Datenbank.
     *
     * @param ship Zu speicherndes Schiff
     * @return ID des gespeicherten Schiffs
     */
    public long save(Ship ship) {
        logger.debug("Speichere Schiff: {}", ship.getName());

        Vec2D pos = ship.getPosition();
        Vec2D dir = ship.getDirection();

        // INSERT mit RETURNING (SQLite 3.35+)
        var result = dsl.insertInto(table("ship"))
                .columns(
                        field("name"),
                        field("vehicle_type"),
                        field("current_x"),
                        field("current_y"),
                        field("direction_x"),
                        field("direction_y")
                )
                .values(
                        ship.getName(),
                        VehicleType.ship.name(),  // Alle Schiffe haben Type 'ship'
                        pos != null ? pos.getX() : null,
                        pos != null ? pos.getY() : null,
                        dir != null ? dir.getX() : null,
                        dir != null ? dir.getY() : null
                )
                .returning(field("id"))
                .fetchOne();

        long id = result.get(field("id", Long.class));
        logger.info("✅ Schiff gespeichert: {} (ID: {})", ship.getName(), id);
        return id;
    }

    /**
     * Aktualisiert Position und Richtung eines Schiffs.
     *
     * @param shipName Name des Schiffs
     * @param position Neue Position
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
     * Lädt ein Schiff nach Name aus der Datenbank.
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
     * Lädt alle aktiven Schiffe.
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
        String name = record.get(field("name", String.class));
        Integer posX = record.get(field("current_x", Integer.class));
        Integer posY = record.get(field("current_y", Integer.class));
        Integer dirX = record.get(field("direction_x", Integer.class));
        Integer dirY = record.get(field("direction_y", Integer.class));

        Vec2D position = (posX != null && posY != null) ? new Vec2D(posX, posY) : null;
        Vec2D direction = (dirX != null && dirY != null) ? new Vec2D(dirX, dirY) : null;

        // Ship-Konstruktor: Ship(String name, Vec2D position, Vec2D direction)
        return new Ship(name, position, direction);
    }

    /**
     * Lädt die Schiffs-ID nach Name.
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
