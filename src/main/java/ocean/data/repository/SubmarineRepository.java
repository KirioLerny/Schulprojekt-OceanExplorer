
package ocean.data.repository;

import ocean.data.DatabaseConnection;
import ocean.model.Vec2D;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.jooq.impl.DSL.*;

/**
 * Repository für Submarine-Datenbank-Operationen.
 *
 * Speichert Submarine-Metadaten, Tauchgänge, 3D-Messpunkte und Fotos.
 *
 * @author OceanExplorer Team
 */
public class SubmarineRepository {

    private static final Logger logger = LoggerFactory.getLogger(SubmarineRepository.class);

    private final DSLContext dsl;

    public SubmarineRepository(DatabaseConnection dbConnection) {
        this.dsl = dbConnection.getDSL();
    }

    // =========================================================
    // SUBMARINE
    // =========================================================

    /**
     * Speichert ein neues Submarine in der Datenbank.
     *
     * @param submarineName Name / ID des Submarines (z.B. "#1#Explorer-220556")
     * @param shipId        ID des Mutterschiffs
     * @return Datenbank-ID des Submarines
     */
    public long saveSubmarine(String submarineName, long shipId) {
        logger.debug("Speichere Submarine: {}", submarineName);

        // ON DUPLICATE KEY UPDATE active=1 → idempotent:
        // Falls das Submarine neu verbindet (ready wird nochmal gesendet),
        // schlägt kein Unique-Constraint-Fehler, sondern wir holen die vorhandene ID.
        dsl.execute(
            "INSERT INTO submarine (name, ship_id, active) VALUES (?, ?, 1) " +
            "ON DUPLICATE KEY UPDATE active = 1, ship_id = ?",
            submarineName, shipId, shipId
        );

        Record r = dsl.select(field("id"))
                .from(table("submarine"))
                .where(field("name").eq(submarineName))
                .fetchOne();

        if (r == null) {
            throw new RuntimeException("Submarine konnte nicht gespeichert werden");
        }
        long id = r.get(field("id", Long.class));
        logger.info("✅ Submarine gespeichert/reaktiviert: {} (ID: {})", submarineName, id);
        return id;
    }

    /**
     * Markiert ein Submarine als inaktiv (aufgetaucht oder gesunken).
     *
     * @param submarineId DB-ID des Submarines
     */
    public void deactivateSubmarine(long submarineId) {
        dsl.update(table("submarine"))
                .set(field("active"), 0)
                .where(field("id").eq(submarineId))
                .execute();
        logger.debug("Submarine {} deaktiviert", submarineId);
    }

    // =========================================================
    // DIVE (Tauchgang)
    // =========================================================

    /**
     * Startet einen neuen Tauchgang.
     *
     * @param submarineId DB-ID des Submarines
     * @return Datenbank-ID des Tauchgangs
     */
    public long startDive(long submarineId) {
        logger.debug("Starte Tauchgang für Submarine {}", submarineId);

        dsl.insertInto(table("submarine_dive"))
                .columns(field("submarine_id"), field("status"))
                .values(submarineId, "DIVING")
                .execute();

        Record r = dsl.select(field("id"))
                .from(table("submarine_dive"))
                .where(field("submarine_id").eq(submarineId))
                .orderBy(field("start_time").desc())
                .limit(1)
                .fetchOne();

        if (r == null) {
            throw new RuntimeException("Tauchgang konnte nicht gespeichert werden");
        }
        long id = r.get(field("id", Long.class));
        logger.info("✅ Tauchgang gestartet (ID: {})", id);
        return id;
    }

    /**
     * Beendet einen Tauchgang (Submarine aufgetaucht oder gesunken).
     *
     * @param diveId  DB-ID des Tauchgangs
     * @param status  "SURFACED" oder "CRASHED"
     */
    public void endDive(long diveId, String status) {
        dsl.update(table("submarine_dive"))
                .set(field("end_time"), now())
                .set(field("status"), status)
                .where(field("id").eq(diveId))
                .execute();
        logger.info("Tauchgang {} beendet mit Status: {}", diveId, status);
    }

    // =========================================================
    // MESSPUNKTE
    // =========================================================

    /**
     * Speichert einen 3D-Messpunkt.
     *
     * @param diveId    DB-ID des Tauchgangs
     * @param x         X-Koordinate (Sektor)
     * @param y         Y-Koordinate (Sektor)
     * @param z         Z-Koordinate (Tiefe, negativ)
     */
    public void saveMeasurementPoint(long diveId, int x, int y, int z) {
        dsl.insertInto(table("submarine_measurement_point"))
                .columns(field("dive_id"), field("x"), field("y"), field("z"))
                .values(diveId, x, y, z)
                .execute();
        logger.debug("Messpunkt gespeichert: ({},{},{})", x, y, z);
    }

    /**
     * Speichert mehrere 3D-Messpunkte auf einmal (Batch).
     *
     * @param diveId DB-ID des Tauchgangs
     * @param points Liste von int[3] Arrays: [x, y, z]
     */
    public void saveMeasurementPoints(long diveId, List<int[]> points) {
        for (int[] p : points) {
            saveMeasurementPoint(diveId, p[0], p[1], p[2]);
        }
        logger.debug("✅ {} Messpunkte gespeichert", points.size());
    }

    // =========================================================
    // FOTO
    // =========================================================

    /**
     * Speichert ein PNG-Foto als BLOB.
     *
     * @param diveId    DB-ID des Tauchgangs
     * @param photoData Foto-Daten als Byte-Array
     */
    public void savePhoto(long diveId, byte[] photoData) {
        logger.debug("Speichere Foto ({} Bytes) für Tauchgang {}", photoData.length, diveId);

        dsl.insertInto(table("submarine_photo"))
                .columns(field("dive_id"), field("photo_data"), field("photo_format"))
                .values(diveId, photoData, "PNG")
                .execute();

        logger.info("✅ Foto gespeichert ({} Bytes)", photoData.length);
    }

    // =========================================================
    // UNFALL
    // =========================================================

    /**
     * Speichert einen Unfall in der Datenbank.
     *
     * @param shipId      ID des Schiffs (nullable)
     * @param submarineId ID des Submarines (nullable)
     * @param position    Position des Unfalls
     * @param description Beschreibung
     */
    public void saveAccident(Long shipId, Long submarineId, Vec2D position, String description) {
        logger.warn("Unfall gespeichert an Position {}: {}", position, description);

        dsl.insertInto(table("accident"))
                .columns(
                        field("ship_id"),
                        field("submarine_id"),
                        field("x"),
                        field("y"),
                        field("description")
                )
                .values(shipId, submarineId, position.getX(), position.getY(), description)
                .execute();
    }
}
