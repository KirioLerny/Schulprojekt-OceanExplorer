package ocean.api;

import ocean.data.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ControlApiServer - REST-Endpunkte")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ControlApiServerTest {

    @Mock private ShipRepository      mockShipRepo;
    @Mock private ScanRepository      mockScanRepo;
    @Mock private SubmarineRepository mockSubRepo;
    @Mock private PhotoRepository     mockPhotoRepo;

    private ControlApiServer server;
    private HttpClient http;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        try (var ss = new java.net.ServerSocket(0)) {
            port = ss.getLocalPort();
        }
        server = new ControlApiServer(mockShipRepo, mockScanRepo, mockSubRepo, mockPhotoRepo,
                "localhost", port);
        server.start();
        http = HttpClient.newHttpClient();
        Thread.sleep(300);
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @Order(1)
    @DisplayName("GET /api/status liefert 200 und serverRunning=true")
    void testGetStatus() throws Exception {
        var resp = get("/api/status");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"serverRunning\":true"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/status enthaelt activeShip=null wenn kein Schiff aktiv")
    void testGetStatusNoActiveShip() throws Exception {
        var resp = get("/api/status");
        assertTrue(resp.body().contains("\"activeShip\":null"));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/ships liefert 200 und leeres Array bei leerer DB")
    void testGetShipsEmpty() throws Exception {
        when(mockShipRepo.findAll()).thenReturn(List.of());
        var resp = get("/api/ships");
        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body().trim());
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/scans liefert 200 und leeres Array bei leerer DB")
    void testGetAllScansEmpty() throws Exception {
        when(mockScanRepo.findAllScans()).thenReturn(List.of());
        var resp = get("/api/scans");
        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body().trim());
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/submarines liefert 200 und leeres Array bei leerer DB")
    void testGetAllSubmarinesEmpty() throws Exception {
        when(mockSubRepo.findAll()).thenReturn(List.of());
        var resp = get("/api/submarines");
        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body().trim());
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/measurements liefert 200 und leeres Array bei leerer DB")
    void testGetMeasurementsEmpty() throws Exception {
        when(mockSubRepo.findAllMeasurements()).thenReturn(List.of());
        var resp = get("/api/measurements");
        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body().trim());
    }

    @Test
    @Order(7)
    @DisplayName("GET /api/photos liefert 200 und leeres Array bei leerer DB")
    void testGetPhotosEmpty() throws Exception {
        when(mockPhotoRepo.findAllMeta()).thenReturn(List.of());
        var resp = get("/api/photos");
        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body().trim());
    }

    @Test
    @Order(8)
    @DisplayName("GET /api/photos/{id} liefert 404 wenn Foto nicht vorhanden")
    void testGetPhotoNotFound() throws Exception {
        when(mockPhotoRepo.findPhotoData(999L)).thenReturn(null);
        var resp = get("/api/photos/999");
        assertEquals(404, resp.statusCode());
    }

    @Test
    @Order(9)
    @DisplayName("GET /api/photos/{id} liefert 400 bei ungültiger ID")
    void testGetPhotoInvalidId() throws Exception {
        var resp = get("/api/photos/abc");
        assertEquals(400, resp.statusCode());
    }

    @Test
    @Order(10)
    @DisplayName("GET /api/photos/{id} liefert PNG-Bild wenn vorhanden")
    void testGetPhotoFound() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
        when(mockPhotoRepo.findPhotoData(1L)).thenReturn(png);
        var resp = http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/photos/1"))
                .GET().build(),
            HttpResponse.BodyHandlers.ofByteArray()
        );
        assertEquals(200, resp.statusCode());
        assertEquals("image/png", resp.headers().firstValue("content-type").orElse(""));
        assertArrayEquals(png, resp.body());
    }

    @Test
    @Order(11)
    @DisplayName("POST /api/ship/launch liefert Fehler wenn OceanServer nicht erreichbar")
    void testLaunchShipNoServer() throws Exception {
        var resp = post("/api/ship/launch",
                "{\"name\":\"TestShip\",\"sectorX\":50,\"sectorY\":50,\"dirX\":0,\"dirY\":1}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"success\":false"));
    }

    @Test
    @Order(12)
    @DisplayName("POST /api/ship/launch mit ungültigem JSON liefert 400")
    void testLaunchShipInvalidJson() throws Exception {
        var resp = post("/api/ship/launch", "nicht-json");
        assertEquals(400, resp.statusCode());
    }

    @Test
    @Order(13)
    @DisplayName("POST /api/ship/navigate liefert Fehler wenn kein Schiff aktiv")
    void testNavigateNoShip() throws Exception {
        var resp = post("/api/ship/navigate",
                "{\"shipName\":\"TestShip\",\"rudder\":\"Center\",\"course\":\"Forward\"}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"success\":false"));
        assertTrue(resp.body().contains("Kein Schiff aktiv"));
    }

    @Test
    @Order(14)
    @DisplayName("POST /api/ship/scan liefert Fehler wenn kein Schiff aktiv")
    void testScanNoShip() throws Exception {
        var resp = post("/api/ship/scan", "{\"shipName\":\"TestShip\"}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"success\":false"));
    }

    @Test
    @Order(15)
    @DisplayName("POST /api/ship/exit liefert Fehler wenn kein Schiff aktiv")
    void testExitShipNoShip() throws Exception {
        var resp = post("/api/ship/exit", "{\"shipName\":\"TestShip\"}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"success\":false"));
    }

    @Test
    @Order(16)
    @DisplayName("POST /api/submarine/launch liefert Fehler wenn kein Schiff aktiv")
    void testLaunchSubmarineNoShip() throws Exception {
        var resp = post("/api/submarine/launch", "{\"shipName\":\"TestShip\"}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"success\":false"));
        assertTrue(resp.body().contains("Kein Schiff aktiv"));
    }

    @Test
    @Order(17)
    @DisplayName("POST /api/submarine/abc/exit liefert 400 bei ungültiger ID")
    void testExitSubmarineInvalidId() throws Exception {
        var resp = post("/api/submarine/abc/exit", "{}");
        assertEquals(400, resp.statusCode());
    }

    @Test
    @Order(18)
    @DisplayName("POST /api/submarine/5/exit deaktiviert Submarine in der DB")
    void testExitSubmarine() throws Exception {
        doNothing().when(mockSubRepo).deactivateSubmarine(5L);
        var resp = post("/api/submarine/5/exit", "{}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"success\":true"));
        verify(mockSubRepo).deactivateSubmarine(5L);
    }

    @Test
    @Order(19)
    @DisplayName("GET /api/ships/unknown/positions liefert 404")
    void testGetPositionsShipNotFound() throws Exception {
        when(mockShipRepo.getIdByName("unknown")).thenReturn(null);
        var resp = get("/api/ships/unknown/positions");
        assertEquals(404, resp.statusCode());
    }

    @Test
    @Order(20)
    @DisplayName("GET /api/ships/{name}/scans liefert 404 wenn Schiff unbekannt")
    void testGetShipScansNotFound() throws Exception {
        when(mockShipRepo.getIdByName("unknown")).thenReturn(null);
        var resp = get("/api/ships/unknown/scans");
        assertEquals(404, resp.statusCode());
    }

    @Test
    @Order(21)
    @DisplayName("GET /api/ships/{name}/submarines liefert 404 wenn Schiff unbekannt")
    void testGetShipSubmarinesNotFound() throws Exception {
        when(mockShipRepo.getIdByName("unknown")).thenReturn(null);
        var resp = get("/api/ships/unknown/submarines");
        assertEquals(404, resp.statusCode());
    }

    @Test
    @Order(22)
    @DisplayName("GET /gallery liefert 200 und text/plain")
    void testGalleryRedirect() throws Exception {
        var resp = get("/gallery");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.headers().firstValue("content-type").orElse("").contains("text/plain"));
    }

    @Test
    @Order(23)
    @DisplayName("getApp() gibt die Javalin-Instanz zurueck")
    void testGetApp() {
        assertNotNull(server.getApp());
    }
}

