package ocean.data.repository;

import ocean.data.DatabaseConnection;
import org.jooq.*;
import org.jooq.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PhotoRepository - Foto-Metadaten lesen")
class PhotoRepositoryTest {

    @Mock private DatabaseConnection mockDb;
    @Mock private DSLContext mockDsl;

    @BeforeEach
    void setUp() {
        lenient().when(mockDb.getDSL()).thenReturn(mockDsl);
    }

    @Test
    @DisplayName("findAllMeta() liefert leere Liste wenn keine Fotos vorhanden")
    void testFindAllMetaEmpty() {
        Result<Record> emptyResult = mock(Result.class);
        when(emptyResult.iterator()).thenReturn(List.<Record>of().iterator());
        when(mockDsl.fetch(anyString())).thenReturn(emptyResult);

        PhotoRepository repo = new PhotoRepository(mockDb);
        List<PhotoRepository.PhotoMeta> result = repo.findAllMeta();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findAllMeta() mappt Record-Felder zu PhotoMeta korrekt")
    void testFindAllMetaMapsRecord() {
        Record r = mock(Record.class);
        when(r.get("id",             Long.class)).thenReturn(1L);
        when(r.get("dive_id",        Long.class)).thenReturn(10L);
        when(r.get("submarine_name", String.class)).thenReturn("Sub-Alpha");
        when(r.get("x",              Integer.class)).thenReturn(5);
        when(r.get("y",              Integer.class)).thenReturn(7);
        when(r.get("z",              Integer.class)).thenReturn(-30);
        when(r.get("dir_x",          Integer.class)).thenReturn(0);
        when(r.get("dir_y",          Integer.class)).thenReturn(1);
        when(r.get("dir_z",          Integer.class)).thenReturn(0);
        when(r.get("timestamp",      java.sql.Timestamp.class)).thenReturn(null);

        Result<Record> result = mock(Result.class);
        when(result.iterator()).thenReturn(List.of(r).iterator());
        when(mockDsl.fetch(anyString())).thenReturn(result);

        PhotoRepository repo = new PhotoRepository(mockDb);
        List<PhotoRepository.PhotoMeta> photos = repo.findAllMeta();

        assertEquals(1, photos.size());
        PhotoRepository.PhotoMeta meta = photos.get(0);
        assertEquals(1L,          meta.id());
        assertEquals(10L,         meta.diveId());
        assertEquals("Sub-Alpha", meta.submarineName());
        assertEquals(5,           meta.x());
        assertEquals(7,           meta.y());
        assertEquals(-30,         meta.z());
    }

    @Test
    @DisplayName("findBySubmarine() uebergibt Submarine-ID korrekt an Query")
    void testFindBySubmarine() {
        Result<Record> emptyResult = mock(Result.class);
        when(emptyResult.iterator()).thenReturn(List.<Record>of().iterator());
        when(mockDsl.fetch(anyString(), eq(42L))).thenReturn(emptyResult);

        PhotoRepository repo = new PhotoRepository(mockDb);
        List<PhotoRepository.PhotoMeta> result = repo.findBySubmarine(42L);

        assertNotNull(result);
        verify(mockDsl).fetch(anyString(), eq(42L));
    }

    @Test
    @DisplayName("findPhotoData() liefert null wenn Foto nicht gefunden")
    void testFindPhotoDataNotFound() {
        when(mockDsl.fetchOne(anyString(), anyLong())).thenReturn(null);

        PhotoRepository repo = new PhotoRepository(mockDb);
        byte[] data = repo.findPhotoData(999L);

        assertNull(data);
    }

    @Test
    @DisplayName("findPhotoData() liefert Byte-Array wenn Foto vorhanden")
    void testFindPhotoDataFound() {
        byte[] expectedBytes = new byte[]{0x01, 0x02, 0x03};
        Record r = mock(Record.class);
        when(r.get("photo_data", byte[].class)).thenReturn(expectedBytes);
        when(mockDsl.fetchOne(anyString(), anyLong())).thenReturn(r);

        PhotoRepository repo = new PhotoRepository(mockDb);
        byte[] data = repo.findPhotoData(1L);

        assertArrayEquals(expectedBytes, data);
    }

    @Test
    @DisplayName("PhotoMeta Record speichert alle Felder korrekt")
    void testPhotoMetaRecord() {
        PhotoRepository.PhotoMeta meta = new PhotoRepository.PhotoMeta(
                1L, 2L, "Sub-42", 10, 20, -50, 0, 1, 0, "2025-01-01");

        assertEquals(1L,           meta.id());
        assertEquals(2L,           meta.diveId());
        assertEquals("Sub-42",     meta.submarineName());
        assertEquals(10,           meta.x());
        assertEquals(20,           meta.y());
        assertEquals(-50,          meta.z());
        assertEquals(0,            meta.dirX());
        assertEquals(1,            meta.dirY());
        assertEquals(0,            meta.dirZ());
        assertEquals("2025-01-01", meta.timestamp());
    }
}
