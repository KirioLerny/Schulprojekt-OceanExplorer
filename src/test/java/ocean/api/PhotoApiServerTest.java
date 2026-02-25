package ocean.api;

import ocean.data.repository.PhotoRepository;
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
@DisplayName("PhotoApiServer - REST-Endpunkte")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PhotoApiServerTest {

    @Mock private PhotoRepository mockPhotoRepo;

    private PhotoApiServer server;
    private HttpClient http;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        try (var ss = new java.net.ServerSocket(0)) {
            port = ss.getLocalPort();
        }
        server = new PhotoApiServer(mockPhotoRepo, port);
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

    @Test
    @DisplayName("DEFAULT_PORT hat den Wert 8080")
    void testDefaultPort() {
        assertEquals(8080, PhotoApiServer.DEFAULT_PORT);
    }

    @Test
    @DisplayName("PhotoApiServer kann ohne Exception erstellt werden")
    void testConstructorNoException() {
        assertDoesNotThrow(() -> new PhotoApiServer(mockPhotoRepo, 0));
    }

    @Test
    @DisplayName("GET /api/photos liefert 200 und leeres JSON-Array bei leerer DB")
    void testGetAllPhotosEmpty() throws Exception {
        when(mockPhotoRepo.findAllMeta()).thenReturn(List.of());
        var resp = get("/api/photos");
        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body().trim());
    }

    @Test
    @DisplayName("GET /api/photos liefert 200 und JSON mit U-Boot-Name")
    void testGetAllPhotosWithEntry() throws Exception {
        var meta = new PhotoRepository.PhotoMeta(1L, 2L, "Sub-A", 10, 20, -50, 0, 1, 0, "2025-01-01");
        when(mockPhotoRepo.findAllMeta()).thenReturn(List.of(meta));
        var resp = get("/api/photos");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Sub-A"));
    }

    @Test
    @DisplayName("GET /api/photos/{id} liefert 404 wenn Foto nicht gefunden")
    void testGetPhotoNotFound() throws Exception {
        when(mockPhotoRepo.findPhotoData(99L)).thenReturn(null);
        var resp = get("/api/photos/99");
        assertEquals(404, resp.statusCode());
    }

    @Test
    @DisplayName("GET /api/photos/{id} liefert 200 und PNG-Content-Type wenn vorhanden")
    void testGetPhotoFound() throws Exception {
        byte[] fakePng = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47};
        when(mockPhotoRepo.findPhotoData(1L)).thenReturn(fakePng);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/photos/1"))
                .GET().build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.headers().firstValue("Content-Type")
                .orElse("").contains("image/png"));
    }

    @Test
    @DisplayName("GET /api/photos/{id} liefert 400 bei nicht-numerischer ID")
    void testGetPhotoInvalidId() throws Exception {
        var resp = get("/api/photos/abc");
        assertEquals(400, resp.statusCode());
    }

    @Test
    @DisplayName("GET /api/submarines/{id}/photos liefert 200 und leeres Array")
    void testGetSubmarinePhotosEmpty() throws Exception {
        when(mockPhotoRepo.findBySubmarine(5L)).thenReturn(List.of());
        var resp = get("/api/submarines/5/photos");
        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body().trim());
    }

    @Test
    @DisplayName("GET /api/submarines/{id}/photos liefert 400 bei ungueltiger ID")
    void testGetSubmarinePhotosInvalidId() throws Exception {
        var resp = get("/api/submarines/xyz/photos");
        assertEquals(400, resp.statusCode());
    }

    @Test
    @DisplayName("GET / liefert 200 und HTML mit Seitentitel")
    void testGalleryRoot() throws Exception {
        when(mockPhotoRepo.findAllMeta()).thenReturn(List.of());
        var resp = get("/");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("<!DOCTYPE html"));
        assertTrue(resp.body().contains("Ocean Explorer"));
    }

    @Test
    @DisplayName("GET /gallery liefert 200")
    void testGalleryPath() throws Exception {
        when(mockPhotoRepo.findAllMeta()).thenReturn(List.of());
        var resp = get("/gallery");
        assertEquals(200, resp.statusCode());
    }
}
