package ocean.data.repository;

import ocean.data.DatabaseConnection;
import ocean.model.ScanResult;
import ocean.model.Vec2D;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.jooq.impl.DSL.*;

/**
 * Repository für ShipScan-Datenbank-Operationen.
 *
 * Speichert Tiefen-Scan-Messungen von Schiffen.
 *
 * @author OceanExplorer Team
 */
public class ScanRepository {

    private static final Logger logger = LoggerFactory.getLogger(ScanRepository.class);

    private final DSLContext dsl;

    /**
     * Erstellt ein neues ScanRepository.
     *
     * @param dbConnection Datenbank-Verbindung
     */
    public ScanRepository(DatabaseConnection dbConnection) {
        this.dsl = dbConnection.getDSL();
    }

    /**
     * Speichert einen Tiefen-Scan in der Datenbank.
     *
     * @param shipId ID des Schiffs
     * @param position Position des Scans
     * @param scanResult Scan-Ergebnis
     */
    public void saveScan(long shipId, Vec2D position, ScanResult scanResult) {
        logger.debug("Speichere Scan an Position {}", position);

        // Prüfe ob Sektor bereits existiert, sonst erstelle ihn
        Long sectorId = getOrCreateSector(position);

        // Scan speichern
        dsl.insertInto(table("ship_scan"))
                .columns(
                        field("ship_id"),
                        field("sector_id"),
                        field("x"),
                        field("y"),
                        field("average_depth"),
                        field("std_deviation")
                )
                .values(
                        shipId,
                        sectorId,
                        position.getX(),
                        position.getY(),
                        scanResult.getAverageDepth(),
                        scanResult.getStandardDeviation()
                )
                .execute();

        logger.debug("✅ Scan gespeichert");
    }

    /**
     * Speichert eine Schiffsposition.
     *
     * @param shipId ID des Schiffs
     * @param position Position
     * @param direction Richtung
     */
    public void savePosition(long shipId, Vec2D position, Vec2D direction) {
        Long sectorId = getOrCreateSector(position);

        dsl.insertInto(table("ship_position"))
                .columns(
                        field("ship_id"),
                        field("sector_id"),
                        field("x"),
                        field("y"),
                        field("direction_x"),
                        field("direction_y")
                )
                .values(
                        shipId,
                        sectorId,
                        position.getX(),
                        position.getY(),
                        direction != null ? direction.getX() : null,
                        direction != null ? direction.getY() : null
                )
                .execute();
    }

    /**
     * Lädt alle Scans eines Schiffs.
     *
     * @param shipId ID des Schiffs
     * @return Liste von Scan-Daten
     */
    public List<ScanData> findScansByShip(long shipId) {
        var records = dsl.select()
                .from(table("ship_scan"))
                .where(field("ship_id").eq(shipId))
                .orderBy(field("timestamp").desc())
                .fetch();

        List<ScanData> scans = new ArrayList<>();
        for (Record record : records) {
            scans.add(mapToScanData(record));
        }

        return scans;
    }

    /**
     * Lädt die Positions-Historie eines Schiffs.
     *
     * @param shipId ID des Schiffs
     * @return Liste von Positionen
     */
    public List<PositionData> findPositionsByShip(long shipId) {
        var records = dsl.select()
                .from(table("ship_position"))
                .where(field("ship_id").eq(shipId))
                .orderBy(field("timestamp").asc())
                .fetch();

        List<PositionData> positions = new ArrayList<>();
        for (Record record : records) {
            positions.add(mapToPositionData(record));
        }

        return positions;
    }

    /**
     * Gibt Sektor-ID zurück oder erstellt neuen Sektor.
     *
     * @param position Position des Sektors
     * @return Sektor-ID
     */
    private Long getOrCreateSector(Vec2D position) {
        // Prüfe ob Sektor existiert
        Record existing = dsl.select(field("id"))
                .from(table("sector"))
                .where(field("x").eq(position.getX()))
                .and(field("y").eq(position.getY()))
                .fetchOne();

        if (existing != null) {
            return existing.get(field("id", Long.class));
        }

        // Erstelle neuen Sektor (mit Standard-Werten)
        dsl.insertInto(table("sector"))
                .columns(
                        field("x"),
                        field("y"),
                        field("ground_type"),
                        field("height")
                )
                .values(
                        position.getX(),
                        position.getY(),
                        "WATER",  // Standard: Wasser
                        0
                )
                .execute();

        // MySQL: generierte ID über LAST_INSERT_ID() lesen
        var idRecord = dsl.select(field("LAST_INSERT_ID()", Long.class)).fetchOne();
        if (idRecord == null || idRecord.value1() == null) {
            throw new RuntimeException("Sektor konnte nicht angelegt werden – keine ID erhalten");
        }
        return idRecord.value1();
    }

    /**
     * Mappt Record zu ScanData.
     */
    private ScanData mapToScanData(Record record) {
        return new ScanData(
                record.get(field("id", Long.class)),
                record.get(field("x", Integer.class)),
                record.get(field("y", Integer.class)),
                record.get(field("average_depth", Double.class)),
                record.get(field("std_deviation", Double.class)),
                record.get(field("timestamp", String.class))
        );
    }

    /**
     * Mappt Record zu PositionData.
     */
    private PositionData mapToPositionData(Record record) {
        return new PositionData(
                record.get(field("id", Long.class)),
                record.get(field("x", Integer.class)),
                record.get(field("y", Integer.class)),
                record.get(field("direction_x", Integer.class)),
                record.get(field("direction_y", Integer.class)),
                record.get(field("timestamp", String.class))
        );
    }

    /**
     * DTO für Scan-Daten.
     */
    public record ScanData(
            long id,
            int x,
            int y,
            double averageDepth,
            double stdDeviation,
            String timestamp
    ) {}

    /**
     * DTO für Positions-Daten.
     */
    public record PositionData(
            long id,
            int x,
            int y,
            Integer directionX,
            Integer directionY,
            String timestamp
    ) {}
}
