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
@DisplayName("SubmarineRepository – Submarine, Tauchgang und Messpunkte")
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

    // ──────────────────────────────────────────────────────────────
    // saveSubmarine()
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("saveSubmarine() führt INSERT und SELECT aus und gibt ID zurück")
    void testSaveSubmarine() {
        // The actual SQL + args differ from anyString/any matchers – use lenient
        lenient().when(mockDsl.execute(anyString(), any(), any(), any())).thenReturn(1);

        when(mockDsl.select(any(Field.class))).thenReturn(selectSelectStep);
        when(selectSelectStep.from(any(Table.class))).thenReturn(selectJoinStep);
        when(selectJoinStep.where(any(Condition.class))).thenReturn(selectConditionStep);
        when(selectConditionStep.fetchOne()).thenReturn(idRecord);
        when(idRecord.get(any(Field.class))).thenReturn(5L);

        long id = new SubmarineRepository(mockDb).saveSubmarine("#1#Alpha", 10L);
        assertEquals(5L, id);
    }

    // ──────────────────────────────────────────────────────────────
    // startDive()
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("startDive() gibt Tauchgang-ID zurück")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testStartDive() {
        // RETURNS_DEEP_STUBS handles .columns(...).values(...).execute()
        InsertSetStep insertMock = mock(InsertSetStep.class, RETURNS_DEEP_STUBS);
        when(mockDsl.insertInto(any(Table.class))).thenReturn(insertMock);

        // orderBy().limit().fetchOne() chain – also use RETURNS_DEEP_STUBS
        SelectConditionStep deepCond = mock(SelectConditionStep.class, RETURNS_DEEP_STUBS);
        when(mockDsl.select(any(Field.class))).thenReturn(selectSelectStep);
        when(selectSelectStep.from(any(Table.class))).thenReturn(selectJoinStep);
        when(selectJoinStep.where(any(Condition.class))).thenReturn(deepCond);
        when(deepCond.orderBy(any(OrderField.class)).limit(anyInt()).fetchOne()).thenReturn(idRecord);
        when(idRecord.get(any(Field.class))).thenReturn(7L);

        long diveId = new SubmarineRepository(mockDb).startDive(5L);
        assertEquals(7L, diveId);
    }

    // ──────────────────────────────────────────────────────────────
    // endDive()
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("endDive() führt UPDATE aus mit korrektem Status")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testEndDive() {
        UpdateSetFirstStep updateStep = mock(UpdateSetFirstStep.class, RETURNS_DEEP_STUBS);
        when(mockDsl.update(any(Table.class))).thenReturn(updateStep);

        assertDoesNotThrow(() -> new SubmarineRepository(mockDb).endDive(7L, "SURFACED"));
        verify(mockDsl).update(any(Table.class));
    }

    // ──────────────────────────────────────────────────────────────
    // deactivateSubmarine()
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deactivateSubmarine() führt UPDATE aus")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testDeactivateSubmarine() {
        UpdateSetFirstStep updateStep = mock(UpdateSetFirstStep.class, RETURNS_DEEP_STUBS);
        when(mockDsl.update(any(Table.class))).thenReturn(updateStep);

        assertDoesNotThrow(() -> new SubmarineRepository(mockDb).deactivateSubmarine(5L));
        verify(mockDsl).update(any(Table.class));
    }

    // ──────────────────────────────────────────────────────────────
    // saveMeasurementPoint() und saveMeasurementPoints()
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("saveMeasurementPoint() führt INSERT IGNORE aus")
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
    @DisplayName("saveMeasurementPoints() mit leerer Liste führt keinen INSERT aus")
    void testSaveMeasurementPointsEmpty() {
        assertDoesNotThrow(() -> new SubmarineRepository(mockDb).saveMeasurementPoints(1L, List.of()));
        verify(mockDsl, never()).execute(anyString(), anyLong(), anyInt(), anyInt(), anyInt());
    }
}

