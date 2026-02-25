package ocean.communication.oceanserver;

import ocean.model.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für OceanClient.
 *
 * Da OceanClient einen echten TCP-Server benötigt, starten wir
 * einen minimalen In-Process-Mock-Server in einem separaten Thread.
 */
@DisplayName("OceanClient – TCP-Kommunikation")
class OceanClientTest {

    private static final int TEST_PORT = 13001;

    // ──────────────────────────────────────────────────────────────
    // Hilfsmethode: einen einfachen Mock-Server starten
    // ──────────────────────────────────────────────────────────────

    /** Startet einen einmaligen Mock-Server; schreibt eine Antwort nach der ersten Zeile. */
    private Thread startMockServer(int port, Runnable handler) throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(port)) {
                ready.countDown();
                try (Socket client = ss.accept()) {
                    handler.run();
                    // Streams via Closure nutzen – Lambda kann nicht, deshalb Field
                    // → Wir übergeben über einen shared array-trick
                }
            } catch (Exception ignored) {}
        });
        t.setDaemon(true);
        t.start();
        ready.await(3, TimeUnit.SECONDS);
        return t;
    }

    // ──────────────────────────────────────────────────────────────
    // isConnected() – ohne echten Server
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isConnected() liefert false vor connect()")
    void testNotConnectedInitially() {
        OceanClient client = new OceanClient("localhost", TEST_PORT);
        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("getShipServerId() liefert null vor launch()")
    void testShipServerIdNullInitially() {
        OceanClient client = new OceanClient("localhost", TEST_PORT);
        assertNull(client.getShipServerId());
    }

    // ──────────────────────────────────────────────────────────────
    // connect() + disconnect() gegen einen Mini-Server
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connect() stellt Verbindung her; isConnected() danach true")
    void testConnect() throws Exception {
        int port = TEST_PORT + 1;
        // Einfacher Server der sofort die Verbindung akzeptiert und hält
        ServerSocket ss = new ServerSocket(port);
        Thread serverThread = new Thread(() -> {
            try {
                Socket s = ss.accept();
                Thread.sleep(3000); // Verbindung offen halten
                s.close();
            } catch (Exception ignored) {}
        });
        serverThread.setDaemon(true);
        serverThread.start();

        OceanClient client = new OceanClient("localhost", port);
        client.connect();
        assertTrue(client.isConnected());
        client.disconnect();
        ss.close();
    }

    @Test
    @DisplayName("disconnect() setzt isConnected() auf false")
    void testDisconnect() throws Exception {
        int port = TEST_PORT + 2;
        ServerSocket ss = new ServerSocket(port);
        Thread serverThread = new Thread(() -> {
            try {
                Socket s = ss.accept();
                // Liest eine Zeile (exit-Befehl) und schließt dann
                new BufferedReader(new InputStreamReader(s.getInputStream())).readLine();
                s.close();
            } catch (Exception ignored) {}
        });
        serverThread.setDaemon(true);
        serverThread.start();

        OceanClient client = new OceanClient("localhost", port);
        client.connect();
        client.disconnect();
        assertFalse(client.isConnected());
        ss.close();
    }

    // ──────────────────────────────────────────────────────────────
    // navigate() – Mock-Server liefert move2d-Antwort
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("navigate() parst move2d-Antwort korrekt")
    void testNavigate() throws Exception {
        int port = TEST_PORT + 3;

        // Erwartete Antwort vom Server
        JSONObject move2d = new JSONObject();
        move2d.put("cmd", "move2d");
        move2d.put("sector", new Vec2D(5, 6).toJson());
        move2d.put("dir",    new Vec2D(0, 1).toJson());

        ServerSocket ss = new ServerSocket(port);
        Thread serverThread = new Thread(() -> {
            try {
                Socket s = ss.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                PrintWriter out  = new PrintWriter(s.getOutputStream(), true);
                in.readLine();           // navigate-Befehl lesen
                out.println(move2d);     // move2d senden
                Thread.sleep(500);
                s.close();
            } catch (Exception ignored) {}
        });
        serverThread.setDaemon(true);
        serverThread.start();

        OceanClient client = new OceanClient("localhost", port);
        client.connect();
        OceanClient.NavigateResult result = client.navigate(Rudder.Center, Course.Forward);
        client.disconnect();
        ss.close();

        assertNotNull(result);
        assertEquals(5, result.position().getX());
        assertEquals(6, result.position().getY());
        assertEquals(0, result.direction().getX());
        assertEquals(1, result.direction().getY());
    }

    // ──────────────────────────────────────────────────────────────
    // radar() – Mock-Server liefert radarresponse
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("radar() parst radarresponse mit Echos korrekt")
    void testRadar() throws Exception {
        int port = TEST_PORT + 4;

        // 2 RadarEchos aufbauen
        JSONObject echo1 = new JSONObject();
        echo1.put("sector", new Vec2D(1, 2).toJson());
        echo1.put("ground", "Water");
        echo1.put("height", 0);

        JSONObject echo2 = new JSONObject();
        echo2.put("sector", new Vec2D(3, 4).toJson());
        echo2.put("ground", "Land");
        echo2.put("height", 5);

        JSONArray echos = new JSONArray();
        echos.put(echo1);
        echos.put(echo2);

        JSONObject response = new JSONObject();
        response.put("cmd",   "radarresponse");
        response.put("echos", echos);

        ServerSocket ss = new ServerSocket(port);
        Thread serverThread = new Thread(() -> {
            try {
                Socket s = ss.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                PrintWriter out  = new PrintWriter(s.getOutputStream(), true);
                in.readLine();
                out.println(response);
                Thread.sleep(500);
                s.close();
            } catch (Exception ignored) {}
        });
        serverThread.setDaemon(true);
        serverThread.start();

        OceanClient client = new OceanClient("localhost", port);
        client.connect();
        List<RadarEcho> result = client.radar();
        client.disconnect();
        ss.close();

        assertEquals(2, result.size());
        assertEquals(Ground.Water, result.get(0).getGround());
        assertEquals(0,            result.get(0).getHeight());
        assertEquals(Ground.Land,  result.get(1).getGround());
        assertEquals(5,            result.get(1).getHeight());
    }

    // ──────────────────────────────────────────────────────────────
    // scan() – Mock-Server liefert scanned-Antwort
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("scan() parst scanned-Antwort korrekt")
    void testScan() throws Exception {
        int port = TEST_PORT + 5;

        JSONObject response = new JSONObject();
        response.put("cmd",    "scanned");
        response.put("depth",  -150);
        response.put("stddev", 12.5);

        ServerSocket ss = new ServerSocket(port);
        Thread serverThread = new Thread(() -> {
            try {
                Socket s = ss.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                PrintWriter out  = new PrintWriter(s.getOutputStream(), true);
                in.readLine();
                out.println(response);
                Thread.sleep(500);
                s.close();
            } catch (Exception ignored) {}
        });
        serverThread.setDaemon(true);
        serverThread.start();

        OceanClient client = new OceanClient("localhost", port);
        client.connect();
        ScanResult result = client.scan();
        client.disconnect();
        ss.close();

        assertNotNull(result);
        assertEquals(-150, result.getAverageDepth());
        assertEquals(12.5f, result.getStandardDeviation(), 0.01f);
    }

    // ──────────────────────────────────────────────────────────────
    // Fehlerfälle
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connect() wirft IOException wenn kein Server läuft")
    void testConnectNoServer() {
        OceanClient client = new OceanClient("localhost", 19999);
        assertThrows(IOException.class, client::connect);
    }

    @Test
    @DisplayName("navigate() liefert null bei Fehler-Antwort vom Server")
    void testNavigateErrorResponse() throws Exception {
        int port = TEST_PORT + 6;

        JSONObject errorResponse = new JSONObject();
        errorResponse.put("cmd",   "error");
        errorResponse.put("error", "Ungültiger Befehl");

        ServerSocket ss = new ServerSocket(port);
        Thread serverThread = new Thread(() -> {
            try {
                Socket s = ss.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                PrintWriter out  = new PrintWriter(s.getOutputStream(), true);
                in.readLine();
                out.println(errorResponse);
                Thread.sleep(500);
                s.close();
            } catch (Exception ignored) {}
        });
        serverThread.setDaemon(true);
        serverThread.start();

        OceanClient client = new OceanClient("localhost", port);
        client.connect();
        OceanClient.NavigateResult result = client.navigate(Rudder.Center, Course.Forward);
        client.disconnect();
        ss.close();

        assertNull(result, "Bei Fehler-Antwort soll navigate() null zurückgeben");
    }
}

