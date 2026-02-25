package ocean.logic.navigation;

import ocean.communication.oceanserver.OceanClient;
import ocean.data.repository.ScanRepository;
import ocean.data.repository.ShipRepository;
import ocean.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NavigationController – autonome Navigation")
class NavigationControllerTest {

    @Mock private OceanClient mockClient;
    @Mock private ShipRepository mockShipRepo;
    @Mock private ScanRepository mockScanRepo;

    private Ship ship;

    @BeforeEach
    void setUp() {
        ship = new Ship("TestSchiff", new Vec2D(5, 5), new Vec2D(0, 1));
    }

    // ──────────────────────────────────────────────────────────────
    // Hilfsmethoden
    // ──────────────────────────────────────────────────────────────

    private List<RadarEcho> buildFreeRadar() {
        List<RadarEcho> echoes = new ArrayList<>();
        int[][] offsets = {{-1,0},{-1,1},{0,1},{1,1},{1,0},{1,-1},{0,-1},{-1,-1}};
        for (int[] o : offsets) {
            echoes.add(new RadarEcho(new Vec2D(o[0], o[1]), Ground.Water, 0));
        }
        return echoes;
    }

    // ──────────────────────────────────────────────────────────────
    // Konstruktor
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Konstruktor setzt Schiff korrekt")
    void testConstructorSetsShip() {
        NavigationController nc = new NavigationController(mockClient, ship);
        assertSame(ship, nc.getShip());
    }

    @Test
    @DisplayName("getVisitedCount() startet bei 0")
    void testVisitedCountStartsAtZero() {
        NavigationController nc = new NavigationController(mockClient, ship);
        assertEquals(0, nc.getVisitedCount());
    }

    // ──────────────────────────────────────────────────────────────
    // explore() – mit Mocks
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("explore(1) scannt genau 1 Sektor und bewegt sich danach")
    void testExploreOnesSector() throws IOException {
        // scan() liefert Ergebnis
        ScanResult scanResult = new ScanResult(new Vec2D(5, 5), -100, 5.0f);
        when(mockClient.scan()).thenReturn(scanResult);

        // radar() und navigate() werden nach dem letzten Sektor nicht mehr benötigt → lenient
        lenient().when(mockClient.radar()).thenReturn(buildFreeRadar());
        lenient().when(mockClient.navigate(any(Rudder.class), any(Course.class)))
                .thenReturn(new OceanClient.NavigateResult(new Vec2D(5, 6), new Vec2D(0, 1)));

        NavigationController nc = new NavigationController(mockClient, ship);
        nc.explore(1);

        assertEquals(1, nc.getVisitedCount(), "Nach explore(1) soll genau 1 Sektor besucht sein");
        verify(mockClient, times(1)).scan();
    }

    @Test
    @DisplayName("explore(0) führt keinen Scan durch")
    void testExploreZeroSectors() throws IOException {
        NavigationController nc = new NavigationController(mockClient, ship);
        nc.explore(0);

        assertEquals(0, nc.getVisitedCount());
        verify(mockClient, never()).scan();
    }

    @Test
    @DisplayName("Gleicher Sektor wird nicht doppelt gescannt")
    void testSectorNotScannedTwice() throws IOException {
        ScanResult scanResult = new ScanResult(new Vec2D(5, 5), -200, 3.0f);
        when(mockClient.scan()).thenReturn(scanResult);

        NavigationController nc = new NavigationController(mockClient, ship);

        // Erster Aufruf: Sektor (5,5) wird gescannt
        nc.explore(1);
        assertEquals(1, nc.getVisitedCount());

        // Zweiter Aufruf: Schiff ist noch auf (5,5) → Sektor bereits besucht → kein zweiter Scan
        nc.explore(1);

        // scan() soll insgesamt nur 1 Mal aufgerufen worden sein
        verify(mockClient, times(1)).scan();
    }

    @Test
    @DisplayName("explore() speichert Scan in Datenbank wenn Repositories gesetzt")
    void testExploreSavesToDatabase() throws IOException {
        when(mockShipRepo.getIdByName("TestSchiff")).thenReturn(42L);

        ScanResult scanResult = new ScanResult(new Vec2D(5, 5), -100, 5.0f);
        when(mockClient.scan()).thenReturn(scanResult);
        // lenient: nach dem letzten Sektor wird radar/navigate nicht mehr aufgerufen
        lenient().when(mockClient.radar()).thenReturn(buildFreeRadar());
        lenient().when(mockClient.navigate(any(), any())).thenReturn(
                new OceanClient.NavigateResult(new Vec2D(5, 6), new Vec2D(0, 1)));

        NavigationController nc = new NavigationController(
                mockClient, ship, mockShipRepo, mockScanRepo);
        nc.explore(1);

        verify(mockScanRepo, times(1)).saveScan(eq(42L), any(Vec2D.class), any(ScanResult.class));
    }

    @Test
    @DisplayName("explore() bricht ab wenn navigation fehlschlägt (navigate() liefert null)")
    void testExploreAbortsOnNavigationFailure() throws IOException {
        ScanResult scanResult = new ScanResult(new Vec2D(5, 5), -100, 5.0f);
        when(mockClient.scan()).thenReturn(scanResult);
        when(mockClient.radar()).thenReturn(buildFreeRadar());
        when(mockClient.navigate(any(), any())).thenReturn(null);  // Navigation schlägt fehl

        NavigationController nc = new NavigationController(mockClient, ship);
        nc.explore(3);  // Möchte 3, aber soll nach erstem Fehlschlag abbrechen

        // Nur 1 Scan (erster Sektor) wurde erfolgreich ausgeführt
        verify(mockClient, times(1)).scan();
    }

    @Test
    @DisplayName("setSubmarineServer() wird akzeptiert ohne Exception")
    void testSetSubmarineServer() {
        NavigationController nc = new NavigationController(mockClient, ship);
        // Kein SubmarineServer verfügbar ohne DB – nur testen dass kein NPE geworfen wird
        assertDoesNotThrow(() -> nc.setSubmarineServer(null));
    }
}

