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
@DisplayName("SubmarineRepository - Submarine, Tauchgang und Messpunkte")
class SubmarineRepositoryTest {

    @Mock private DatabaseConnection mockDb;
    @Mock private DSLContext mockDsl;
    @Mock private SelectSelectStep<Record> selectSelectStep;
    @Mock private SelectJoinStep<Record> selectJoinStep;
    @Mock private SelectConditionStep<Record> selectConditionStep;
    @Mock private Record idRecord;

    @BeforeEach
    void setUp() {
        lenient().when(mockDb.getDSL()).thenReturn(mockDsl);
    }

    @Test
    @DisplayName("saveSubmarine() fuehrt INSERT und SELECT aus und gibt ID zurueck")
    void testSaveSubmarine() {
        lenient().when(mockDsl.execute(anyString(), any(), any(), any())).thenReturn(1);

        when(mockDsl.select(any(Field.class))).thenReturn(selectSelectStep);
        when(selectSelectStep.from(any(Table.class))).thenReturn(selectJoinStep);
        when(selectJoinStep.where(any(Condition.class))).thenReturn(selectConditionStep);
        when(selectConditionStep.fetchOne()).thenReturn(idRecord);
        when(idRecord.get(any(Field.class))).thenReturn(5L);

        long id = new SubmarineRepository(mockDb).saveSubmarine("#1#Alpha", 10L);
        assertEquals(5L, id);
    }

    @Test
    @DisplayName("startDive() gibt Tauchgang-ID zurueck")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testStartDive() {
        InsertSetStep insertMock = mock(InsertSetStep.class, RETURNS_DEEP_STUBS);
        when(mockDsl.insertInto(any(Table.class))).thenReturn(insertMock);

        SelectConditionStep deepCond = mock(SelectConditionStep.class, RETURNS_DEEP_STUBS);
        when(mockDsl.select(any(Field.class))).thenReturn(selectSelectStep);
        when(selectSelectStep.from(any(Table.class))).thenReturn(selectJoinStep);
        when(selectJoinStep.where(any(Condition.class))).thenReturn(deepCond);
        when(deepCond.orderBy(any(OrderField.class)).limit(anyInt()).fetchOne()).thenReturn(idRecord);
        when(idRecord.get(any(Field.class))).thenReturn(7L);

        long diveId = new SubmarineRepository(mockDb).startDive(5L);
        assertEquals(7L, diveId);
    }

    @Test
    @DisplayName("endDive() fuehrt UPDATE aus mit korrektem Status")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testEndDive() {
        UpdateSetFirstStep updateStep = mock(UpdateSetFirstStep.class, RETURNS_DEEP_STUBS);
        when(mockDsl.update(any(Table.class))).thenReturn(updateStep);

        assertDoesNotThrow(() -> new SubmarineRepository(mockDb).endDive(7L, "SURFACED"));
        verify(mockDsl).update(any(Table.class));
    }

    @Test
    @DisplayName("deactivateSubmarine() fuehrt UPDATE aus")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testDeactivateSubmarine() {
        UpdateSetFirstStep updateStep = mock(UpdateSetFirstStep.class, RETURNS_DEEP_STUBS);
        when(mockDsl.update(any(Table.class))).thenReturn(updateStep);

        assertDoesNotThrow(() -> new SubmarineRepository(mockDb).deactivateSubmarine(5L));
        verify(mockDsl).update(any(Table.class));
    }

    @Test
    @DisplayName("saveMeasurementPoint() fuehrt INSERT IGNORE aus")
    void testSaveMeasurementPoint() {
        when(mockDsl.execute(anyString(), anyLong(), anyInt(), anyInt(), anyInt())).thenReturn(1);

        assertDoesNotThrow(() -> new SubmarineRepository(mockDb).saveMeasurementPoint(1L, 10, 20, -30));
        verify(mockDsl).execute(anyString(), eq(1L), eq(10), eq(20), eq(-30));
    }

    @Test
    @DisplayName("saveMeasurementPoints() speichert alle Punkte einer Liste")
    void testSaveMeasurementPoints() {
        when(mockDsl.execute(anyString(), anyLong(), anyInt(), anyInt(), anyInt())).thenReturn(1);

        List<int[]> points = List.of(
            new int[]{1, 2, -10},
            new int[]{3, 4, -20},
            new int[]{5, 6, -30}
        );

        assertDoesNotThrow(() -> new SubmarineRepository(mockDb).saveMeasurementPoints(2L, points));
        verify(mockDsl, times(3)).execute(anyString(), anyLong(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("saveMeasurementPoints() mit leerer Liste fuehrt keinen INSERT aus")
    void testSaveMeasurementPointsEmpty() {
        assertDoesNotThrow(() -> new SubmarineRepository(mockDb).saveMeasurementPoints(1L, List.of()));
        verify(mockDsl, never()).execute(anyString(), anyLong(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("findByShip() liefert leere Liste wenn keine Submarines gefunden")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testFindByShipEmpty() {
        org.jooq.Result emptyResult = mock(org.jooq.Result.class, RETURNS_DEEP_STUBS);
        when(emptyResult.iterator()).thenReturn(List.of().iterator());
        when(mockDsl.fetch(anyString(), anyLong())).thenReturn(emptyResult);

        List<SubmarineRepository.SubmarineInfo> result =
                new SubmarineRepository(mockDb).findByShip(99L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByShip() ruft fetch mit ship_id-Parameter auf")
    void testFindByShipCallsFetchWithShipId() {
        when(mockDsl.fetch(anyString(), anyLong())).thenReturn(mock(org.jooq.Result.class, RETURNS_DEEP_STUBS));
        new SubmarineRepository(mockDb).findByShip(42L);
        verify(mockDsl).fetch(anyString(), eq(42L));
    }

    @Test
    @DisplayName("findAll() ruft fetch ohne Parameter auf")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testFindAllCallsFetch() {
        org.jooq.Result emptyResult = mock(org.jooq.Result.class, RETURNS_DEEP_STUBS);
        when(emptyResult.iterator()).thenReturn(List.of().iterator());
        when(mockDsl.fetch(anyString())).thenReturn(emptyResult);

        List<SubmarineRepository.SubmarineInfo> result =
                new SubmarineRepository(mockDb).findAll();

        verify(mockDsl).fetch(anyString());
        assertNotNull(result);
    }

    @Test
    @DisplayName("findAllMeasurements() ruft fetch ohne Parameter auf")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testFindAllMeasurementsCallsFetch() {
        org.jooq.Result emptyResult = mock(org.jooq.Result.class, RETURNS_DEEP_STUBS);
        when(emptyResult.iterator()).thenReturn(List.of().iterator());
        when(mockDsl.fetch(anyString())).thenReturn(emptyResult);

        List<SubmarineRepository.MeasurementInfo> result =
                new SubmarineRepository(mockDb).findAllMeasurements();

        verify(mockDsl).fetch(anyString());
        assertNotNull(result);
    }

    @Test
    @DisplayName("SubmarineInfo Record speichert alle Felder korrekt")
    void testSubmarineInfoRecord() {
        SubmarineRepository.SubmarineInfo info =
                new SubmarineRepository.SubmarineInfo(7L, "#1#TestSub", 3L, true);
        assertEquals(7L, info.id());
        assertEquals("#1#TestSub", info.name());
        assertEquals(3L, info.shipId());
        assertTrue(info.active());
    }

    @Test
    @DisplayName("MeasurementInfo Record speichert x, y, z korrekt")
    void testMeasurementInfoRecord() {
        SubmarineRepository.MeasurementInfo info =
                new SubmarineRepository.MeasurementInfo(10, 20, -50);
        assertEquals(10, info.x());
        assertEquals(20, info.y());
        assertEquals(-50, info.z());
    }
}
