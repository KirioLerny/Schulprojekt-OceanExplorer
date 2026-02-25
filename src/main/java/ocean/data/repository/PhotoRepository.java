package ocean.data.repository;

import ocean.data.DatabaseConnection;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository fuer Submarine-Fotos.
 *
 * <pre>
 * Liest Fotos aus der Datenbank. Wird vom REST-Webservice genutzt,
 * damit die Fotos im Browser angesehen werden koennen.
 * </pre>
 */
public class PhotoRepository {

    private static final Logger logger = LoggerFactory.getLogger(PhotoRepository.class);

    private final DSLContext dsl;

    public PhotoRepository(DatabaseConnection dbConnection) {
        this.dsl = dbConnection.getDSL();
    }

    /**
     * Metadaten eines Fotos (ohne Blob-Inhalt) fuer Listen-Endpunkte.
     */
    public record PhotoMeta(
            long id,
            long diveId,
            String submarineName,
            int x, int y, int z,
            int dirX, int dirY, int dirZ,
            String timestamp
    ) {}

    /**
     * Gibt alle Foto-Metadaten zurueck (ohne Bild-Blob).
     *
     * @return Liste aller Fotos
     */
    public List<PhotoMeta> findAllMeta() {
        var records = dsl.fetch(
            "SELECT p.id, p.dive_id, s.name AS submarine_name, " +
            "p.x, p.y, p.z, p.dir_x, p.dir_y, p.dir_z, p.timestamp " +
            "FROM submarine_photo p " +
            "JOIN submarine_dive d ON d.id = p.dive_id " +
            "JOIN submarine s ON s.id = d.submarine_id " +
            "ORDER BY p.timestamp DESC"
        );

        List<PhotoMeta> result = new ArrayList<>();
        for (Record r : records) {
            result.add(mapMeta(r));
        }
        return result;
    }

    /**
     * Gibt alle Foto-Metadaten eines bestimmten Submarines zurueck.
     *
     * @param submarineId DB-ID des Submarines
     * @return Liste der Fotos des Submarines
     */
    public List<PhotoMeta> findBySubmarine(long submarineId) {
        var records = dsl.fetch(
            "SELECT p.id, p.dive_id, s.name AS submarine_name, " +
            "p.x, p.y, p.z, p.dir_x, p.dir_y, p.dir_z, p.timestamp " +
            "FROM submarine_photo p " +
            "JOIN submarine_dive d ON d.id = p.dive_id " +
            "JOIN submarine s ON s.id = d.submarine_id " +
            "WHERE s.id = ? " +
            "ORDER BY p.timestamp DESC",
            submarineId
        );

        List<PhotoMeta> result = new ArrayList<>();
        for (Record r : records) {
            result.add(mapMeta(r));
        }
        return result;
    }

    /**
     * Gibt die rohen PNG-Bytes eines Fotos zurueck.
     *
     * @param photoId DB-ID des Fotos
     * @return PNG-Bytes oder null wenn nicht gefunden
     */
    public byte[] findPhotoData(long photoId) {
        Record r = dsl.fetchOne(
            "SELECT photo_data FROM submarine_photo WHERE id = ?",
            photoId
        );

        if (r == null) {
            logger.warn("Foto {} nicht gefunden", photoId);
            return null;
        }
        return r.get("photo_data", byte[].class);
    }

    private PhotoMeta mapMeta(Record r) {
        Timestamp ts = r.get("timestamp", Timestamp.class);
        return new PhotoMeta(
                r.get("id",             Long.class),
                r.get("dive_id",        Long.class),
                r.get("submarine_name", String.class),
                r.get("x",              Integer.class),
                r.get("y",              Integer.class),
                r.get("z",              Integer.class),
                r.get("dir_x",          Integer.class),
                r.get("dir_y",          Integer.class),
                r.get("dir_z",          Integer.class),
                ts != null ? ts.toString() : null
        );
    }
}
