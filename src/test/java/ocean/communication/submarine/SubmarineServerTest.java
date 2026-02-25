package ocean.communication.submarine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SubmarineServer - Lifecycle und Status")
class SubmarineServerTest {

    @Test
    @DisplayName("isDiving() liefert false wenn keine Session aktiv")
    void testIsDivingFalseInitially() {
        SubmarineServer server = new SubmarineServer(19500, null, 1L);
        assertFalse(server.isDiving(), "Ohne aktive Sessions soll isDiving() false sein");
    }

    @Test
    @DisplayName("getActiveSessionCount() liefert 0 initial")
    void testActiveSessionCountZeroInitially() {
        SubmarineServer server = new SubmarineServer(19501, null, 1L);
        assertEquals(0, server.getActiveSessionCount());
    }

    @Test
    @DisplayName("isRunning() liefert false vor start()")
    void testIsRunningFalseBeforeStart() {
        SubmarineServer server = new SubmarineServer(19502, null, 1L);
        assertFalse(server.isRunning(), "isRunning() soll false sein bevor start() aufgerufen wird");
    }

    @Test
    @DisplayName("shutdown() laeuft ohne Exception wenn Server nie gestartet")
    void testShutdownWithoutStart() {
        SubmarineServer server = new SubmarineServer(19503, null, 1L);
        assertDoesNotThrow(server::shutdown);
    }

    @Test
    @DisplayName("isRunning() liefert true nach start() und false nach shutdown()")
    void testStartAndShutdown() throws InterruptedException {
        SubmarineServer server = new SubmarineServer(19504, null, 1L);
        server.start();
        Thread.sleep(300);
        assertTrue(server.isRunning(), "Nach start() soll isRunning() true sein");

        server.shutdown();
        Thread.sleep(300);
        assertFalse(server.isRunning(), "Nach shutdown() soll isRunning() false sein");
    }

    @Test
    @DisplayName("waitForAllSessions() kehrt sofort zurueck wenn keine Sessions aktiv")
    void testWaitForAllSessionsNoSessions() throws InterruptedException {
        SubmarineServer server = new SubmarineServer(19505, null, 1L);
        long start = System.currentTimeMillis();
        server.waitForAllSessions(2000);
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 1000, "Ohne aktive Sessions soll waitForAllSessions() sofort zurueckkehren");
    }

    @Test
    @DisplayName("DEFAULT_PORT hat den Wert 9000")
    void testDefaultPort() {
        assertEquals(9000, SubmarineServer.DEFAULT_PORT);
    }
}
