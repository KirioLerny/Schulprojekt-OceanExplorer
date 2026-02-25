
package ocean.data.repository;

import ocean.data.DatabaseConnection;
import ocean.model.Vec2D;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        // Versuche INSERT – bei Duplicate Key machen wir stattdessen ein UPDATE
        try {
            dsl.execute(
                "INSERT INTO submarine (name, ship_id, active) VALUES (?, ?, 1) " +
                "ON DUPLICATE KEY UPDATE active = 1, ship_id = ?",
                submarineName, shipId, shipId
            );
        } catch (Exception e) {
            // Fallback: falls ON DUPLICATE KEY nicht greift, update direkt
            logger.warn("INSERT fehlgeschlagen ({}), versuche UPDATE...", e.getMessage());
            dsl.execute(
                "UPDATE submarine SET active = 1, ship_id = ? WHERE name = ?",
                shipId, submarineName
            );
        }

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
        // INSERT IGNORE: Jeder Punkt nur einmal speichern (UNIQUE x,y,z laut context.md)
        dsl.execute(
            "INSERT IGNORE INTO submarine_measurement_point (dive_id, x, y, z) VALUES (?, ?, ?, ?)",
            diveId, x, y, z
        );
        logger.debug("Messpunkt gespeichert (oder bereits vorhanden): ({},{},{})", x, y, z);
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
     * Speichert ein PNG-Foto als BLOB inkl. Position und Richtung des Submarines.
     *
     * @param diveId    DB-ID des Tauchgangs
     * @param photoData Foto-Daten als Byte-Array
     * @param x         X-Position
     * @param y         Y-Position
     * @param z         Z-Position (Tiefe)
     * @param dirX      Richtung X
     * @param dirY      Richtung Y
     * @param dirZ      Richtung Z
     */
    public void savePhoto(long diveId, byte[] photoData, int x, int y, int z, int dirX, int dirY, int dirZ) {
        logger.debug("Speichere Foto ({} Bytes) für Tauchgang {} pos=({},{},{})", photoData.length, diveId, x, y, z);

        dsl.insertInto(table("submarine_photo"))
                .columns(
                        field("dive_id"),
                        field("photo_data"),
                        field("photo_format"),
                        field("x"), field("y"), field("z"),
                        field("dir_x"), field("dir_y"), field("dir_z")
                )
                .values(diveId, photoData, "PNG", x, y, z, dirX, dirY, dirZ)
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

    // =========================================================
    // READ METHODS (für WebApp / REST API)
    // =========================================================

    /** DTO für Submarine-Metadaten. */
    public record SubmarineInfo(
            long id,
            String name,
            long shipId,
            boolean active
    ) {}

    /** DTO für 3D-Messpunkte. */
    public record MeasurementInfo(int x, int y, int z) {}

    /**
     * Alle Submarines eines Schiffs.
     */
    public List<SubmarineInfo> findByShip(long shipId) {
        var records = dsl.fetch(
            "SELECT id, name, ship_id, active FROM submarine WHERE ship_id = ? ORDER BY id DESC",
            shipId
        );
        List<SubmarineInfo> result = new ArrayList<>();
        for (Record r : records) {
            Integer activeVal = r.get(field("active", Integer.class));
            result.add(new SubmarineInfo(
                r.get(field("id", Long.class)),
                r.get(field("name", String.class)),
                r.get(field("ship_id", Long.class)),
                activeVal != null && activeVal == 1
            ));
        }
        return result;
    }

    /**
     * Alle Submarines.
     */
    public List<SubmarineInfo> findAll() {
        var records = dsl.fetch(
            "SELECT id, name, ship_id, active FROM submarine ORDER BY id DESC"
        );
        List<SubmarineInfo> result = new ArrayList<>();
        for (Record r : records) {
            Integer activeVal = r.get(field("active", Integer.class));
            result.add(new SubmarineInfo(
                r.get(field("id", Long.class)),
                r.get(field("name", String.class)),
                r.get(field("ship_id", Long.class)),
                activeVal != null && activeVal == 1
            ));
        }
        return result;
    }

    /**
     * Alle 3D-Messpunkte (distinct x,y,z).
     */
    public List<MeasurementInfo> findAllMeasurements() {
        var records = dsl.fetch(
            "SELECT DISTINCT x, y, z FROM submarine_measurement_point ORDER BY z, x, y"
        );
        List<MeasurementInfo> result = new ArrayList<>();
        for (Record r : records) {
            result.add(new MeasurementInfo(
                r.get(field("x", Integer.class)),
                r.get(field("y", Integer.class)),
                r.get(field("z", Integer.class))
            ));
        }
        return result;
    }
}
