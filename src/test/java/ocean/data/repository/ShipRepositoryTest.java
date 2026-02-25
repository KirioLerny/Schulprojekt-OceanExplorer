package ocean.data.repository;

import ocean.data.DatabaseConnection;
import ocean.model.Ship;
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
@DisplayName("ShipRepository - Schiffsdaten speichern und laden")
class ShipRepositoryTest {

    @Mock private DatabaseConnection mockDb;
    @Mock private DSLContext mockDsl;
    @Mock private SelectSelectStep<Record> selectSelectStep;
    @Mock private SelectJoinStep<Record> selectJoinStep;
    @Mock private SelectConditionStep<Record> selectConditionStep;
    @Mock private Record idRecord;
    @Mock private Record shipRecord;

    @BeforeEach
    void setUp() {
        lenient().when(mockDb.getDSL()).thenReturn(mockDsl);
    }

    @Test
    @DisplayName("save() fuehrt INSERT und anschliessendes SELECT aus")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testSaveExecutesInsertAndSelect() {
        Ship ship = new Ship("Explorer", new Vec2D(10, 20), new Vec2D(0, 1));

        InsertSetStep insertMock = mock(InsertSetStep.class, RETURNS_DEEP_STUBS);
        when(mockDsl.insertInto(any(Table.class))).thenReturn(insertMock);

        when(mockDsl.select(any(Field.class))).thenReturn(selectSelectStep);
        when(selectSelectStep.from(any(Table.class))).thenReturn(selectJoinStep);
        when(selectJoinStep.where(any(Condition.class))).thenReturn(selectConditionStep);
        when(selectConditionStep.fetchOne()).thenReturn(idRecord);
        when(idRecord.get(any(Field.class))).thenReturn(99L);

        long id = new ShipRepository(mockDb).save(ship);

        assertEquals(99L, id);
        verify(mockDsl).insertInto(any(Table.class));
    }

    @Test
    @DisplayName("findByName() liefert null wenn kein Record gefunden")
    void testFindByNameReturnsNullWhenNotFound() {
        when(mockDsl.select()).thenReturn(selectSelectStep);
        when(selectSelectStep.from(any(Table.class))).thenReturn(selectJoinStep);
        when(selectJoinStep.where(any(Condition.class))).thenReturn(selectConditionStep);
        when(selectConditionStep.fetchOne()).thenReturn(null);

        assertNull(new ShipRepository(mockDb).findByName("NichtVorhanden"));
    }

    @Test
    @DisplayName("findByName() mappt Record korrekt zu Ship-Objekt")
    void testFindByNameMapsRecord() {
        when(mockDsl.select()).thenReturn(selectSelectStep);
        when(selectSelectStep.from(any(Table.class))).thenReturn(selectJoinStep);
        when(selectJoinStep.where(any(Condition.class))).thenReturn(selectConditionStep);
        when(selectConditionStep.fetchOne()).thenReturn(shipRecord);

        doAnswer(inv -> {
            Field<?> f = inv.getArgument(0);
            String name = f.getName();
            return switch (name) {
                case "name" -> "Explorer";
                case "current_x", "current_y", "direction_x", "direction_y" -> 5;
                default -> null;
            };
        }).when(shipRecord).get(any(Field.class));

        Ship result = new ShipRepository(mockDb).findByName("Explorer");

        assertNotNull(result);
        assertEquals("Explorer", result.getName());
        assertNotNull(result.getPosition());
        assertNotNull(result.getDirection());
    }

    @Test
    @DisplayName("getIdByName() liefert null wenn Schiff nicht gefunden")
    void testGetIdByNameReturnsNull() {
        when(mockDsl.select(any(Field.class))).thenReturn(selectSelectStep);
        when(selectSelectStep.from(any(Table.class))).thenReturn(selectJoinStep);
        when(selectJoinStep.where(any(Condition.class))).thenReturn(selectConditionStep);
        when(selectConditionStep.fetchOne()).thenReturn(null);

        assertNull(new ShipRepository(mockDb).getIdByName("Unbekannt"));
    }

    @Test
    @DisplayName("getIdByName() liefert die korrekte ID")
    void testGetIdByNameReturnsId() {
        when(mockDsl.select(any(Field.class))).thenReturn(selectSelectStep);
        when(selectSelectStep.from(any(Table.class))).thenReturn(selectJoinStep);
        when(selectJoinStep.where(any(Condition.class))).thenReturn(selectConditionStep);
        when(selectConditionStep.fetchOne()).thenReturn(idRecord);
        when(idRecord.get(any(Field.class))).thenReturn(7L);

        assertEquals(7L, new ShipRepository(mockDb).getIdByName("Explorer"));
    }

    @Test
    @DisplayName("updatePosition() fuehrt UPDATE-Query aus")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testUpdatePosition() {
        UpdateSetFirstStep updateStep = mock(UpdateSetFirstStep.class, RETURNS_DEEP_STUBS);
        when(mockDsl.update(any(Table.class))).thenReturn(updateStep);

        assertDoesNotThrow(() ->
            new ShipRepository(mockDb).updatePosition("Explorer", new Vec2D(15, 25), new Vec2D(1, 0))
        );
        verify(mockDsl).update(any(Table.class));
    }

    @Test
    @DisplayName("findAll() liefert leere Liste wenn keine aktiven Schiffe vorhanden")
    void testFindAllEmpty() {
        Result<Record> emptyResult = mock(Result.class);
        when(emptyResult.iterator()).thenReturn(List.<Record>of().iterator());

        when(mockDsl.select()).thenReturn(selectSelectStep);
        when(selectSelectStep.from(any(Table.class))).thenReturn(selectJoinStep);
        when(selectJoinStep.where(any(Condition.class))).thenReturn(selectConditionStep);
        when(selectConditionStep.fetch()).thenReturn(emptyResult);

        List<Ship> ships = new ShipRepository(mockDb).findAll();
        assertNotNull(ships);
        assertTrue(ships.isEmpty());
    }
}
