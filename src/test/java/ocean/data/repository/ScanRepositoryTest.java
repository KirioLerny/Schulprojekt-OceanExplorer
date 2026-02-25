package ocean.data.repository;

import ocean.data.DatabaseConnection;
import ocean.model.ScanResult;
import ocean.model.Vec2D;
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
@DisplayName("ScanRepository - Scan- und Positions-Persistierung")
class ScanRepositoryTest {

    @Mock private DatabaseConnection mockDb;
    @Mock private DSLContext mockDsl;
    @Mock private SelectSelectStep<Record> selectSelectStep;
    @Mock private SelectJoinStep<Record> selectJoinStep;
    @Mock private SelectConditionStep<Record> selectConditionStep;
    @Mock private Record sectorIdRecord;

    @BeforeEach
    void setUp() {
        lenient().when(mockDb.getDSL()).thenReturn(mockDsl);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubSectorLookup(long sectorId) {
        when(mockDsl.select(any(Field.class))).thenReturn(selectSelectStep);
        when(selectSelectStep.from(any(Table.class))).thenReturn(selectJoinStep);
        when(selectJoinStep.where(any(Condition.class))).thenReturn(selectConditionStep);
        when(selectConditionStep.and(any(Condition.class))).thenReturn(selectConditionStep);
        when(selectConditionStep.fetchOne()).thenReturn(sectorIdRecord);
        when(sectorIdRecord.get(any(Field.class))).thenReturn(sectorId);
    }

    @Test
    @DisplayName("saveScan() fuehrt INSERT aus wenn Sektor bereits vorhanden")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testSaveScanWithExistingSector() {
        stubSectorLookup(3L);
        InsertSetStep insertMock = mock(InsertSetStep.class, RETURNS_DEEP_STUBS);
        when(mockDsl.insertInto(any(Table.class))).thenReturn(insertMock);

        ScanResult scan = new ScanResult(new Vec2D(5, 5), -120, 8.5f);
        assertDoesNotThrow(() -> new ScanRepository(mockDb).saveScan(1L, new Vec2D(5, 5), scan));
        verify(mockDsl).insertInto(any(Table.class));
    }

    @Test
    @DisplayName("savePosition() fuehrt INSERT aus wenn Sektor vorhanden")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testSavePosition() {
        stubSectorLookup(5L);
        InsertSetStep insertMock = mock(InsertSetStep.class, RETURNS_DEEP_STUBS);
        when(mockDsl.insertInto(any(Table.class))).thenReturn(insertMock);

        assertDoesNotThrow(() ->
            new ScanRepository(mockDb).savePosition(1L, new Vec2D(10, 20), new Vec2D(0, 1))
        );
    }

    @Test
    @DisplayName("findScansByShip() liefert leere Liste wenn keine Ergebnisse")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testFindScansByShipEmpty() {
        Result<Record> emptyResult = mock(Result.class);
        when(emptyResult.iterator()).thenReturn(List.<Record>of().iterator());

        SelectConditionStep deepCond = mock(SelectConditionStep.class, RETURNS_DEEP_STUBS);
        when(mockDsl.select()).thenReturn(selectSelectStep);
        when(selectSelectStep.from(any(Table.class))).thenReturn(selectJoinStep);
        when(selectJoinStep.where(any(Condition.class))).thenReturn(deepCond);
        when(deepCond.orderBy(any(OrderField.class)).fetch()).thenReturn(emptyResult);

        assertTrue(new ScanRepository(mockDb).findScansByShip(1L).isEmpty());
    }

    @Test
    @DisplayName("findAllScans() liefert leere Liste wenn keine Ergebnisse")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testFindAllScansEmpty() {
        Result<Record> emptyResult = mock(Result.class);
        when(emptyResult.iterator()).thenReturn(List.<Record>of().iterator());

        SelectJoinStep deepJoin = mock(SelectJoinStep.class, RETURNS_DEEP_STUBS);
        when(mockDsl.select()).thenReturn(selectSelectStep);
        when(selectSelectStep.from(any(Table.class))).thenReturn(deepJoin);
        when(deepJoin.orderBy(any(OrderField.class)).fetch()).thenReturn(emptyResult);

        assertTrue(new ScanRepository(mockDb).findAllScans().isEmpty());
    }

    @Test
    @DisplayName("findAllScans() verwendet select() auf DSL")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testFindAllScansCallsOrderBy() {
        Result<Record> emptyResult = mock(Result.class);
        when(emptyResult.iterator()).thenReturn(List.<Record>of().iterator());

        SelectJoinStep deepJoin = mock(SelectJoinStep.class, RETURNS_DEEP_STUBS);
        when(mockDsl.select()).thenReturn(selectSelectStep);
        when(selectSelectStep.from(any(Table.class))).thenReturn(deepJoin);
        when(deepJoin.orderBy(any(OrderField.class)).fetch()).thenReturn(emptyResult);

        new ScanRepository(mockDb).findAllScans();

        verify(mockDsl).select();
    }

    @Test
    @DisplayName("ScanData Record speichert alle Felder korrekt")
    void testScanDataRecord() {
        ScanRepository.ScanData data = new ScanRepository.ScanData(1L, 10, 20, -150.0, 5.5, "2025-01-01 12:00:00");
        assertEquals(1L, data.id());
        assertEquals(10, data.x());
        assertEquals(20, data.y());
        assertEquals(-150.0, data.averageDepth(), 0.001);
        assertEquals(5.5, data.stdDeviation(), 0.001);
        assertEquals("2025-01-01 12:00:00", data.timestamp());
    }

    @Test
    @DisplayName("PositionData Record speichert alle Felder korrekt")
    void testPositionDataRecord() {
        ScanRepository.PositionData data = new ScanRepository.PositionData(2L, 30, 40, 0, 1, "2025-06-01 08:00:00");
        assertEquals(2L, data.id());
        assertEquals(30, data.x());
        assertEquals(40, data.y());
        assertEquals(0, data.directionX());
        assertEquals(1, data.directionY());
        assertEquals("2025-06-01 08:00:00", data.timestamp());
    }
}
