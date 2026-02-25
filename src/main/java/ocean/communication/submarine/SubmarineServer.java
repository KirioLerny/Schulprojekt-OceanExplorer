package ocean.communication.submarine;

import ocean.data.repository.SubmarineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP-Server der auf eingehende Submarine-Verbindungen wartet.
 *
 * <pre>
 * Laeuft im eigenen Daemon-Thread.
 * Fuer jede neue Submarine-Verbindung wird eine SubmarineSession gestartet.
 * Verwaltet alle aktiven Sessions und ermoeglicht Warten bis alle fertig sind.
 * </pre>
 */
public class SubmarineServer extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(SubmarineServer.class);

    public static final int DEFAULT_PORT = 9000;

    private final int port;
    private final SubmarineRepository subRepo;
    private final long shipId;
    private final int maxSessions;

    private ServerSocket serverSocket;
    private volatile boolean running = false;

    private final AtomicInteger acceptedCount = new AtomicInteger(0);

    private final Set<SubmarineSession> activeSessions =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Erstellt einen SubmarineServer.
     *
     * @param port        Port auf dem auf Submarines gewartet wird
     * @param subRepo     Repository fuer Submarine-Daten
     * @param shipId      DB-ID des Mutterschiffs
     * @param maxSessions Maximale Anzahl Submarines die akzeptiert werden
     */
    public SubmarineServer(int port, SubmarineRepository subRepo, long shipId, int maxSessions) {
        this.port = port;
        this.subRepo = subRepo;
        this.shipId = shipId;
        this.maxSessions = maxSessions;
        setName("SubmarineServer:" + port);
        setDaemon(true);
    }

    /**
     * Erstellt einen SubmarineServer ohne Submarine-Limit.
     *
     * @param port    Port auf dem auf Submarines gewartet wird
     * @param subRepo Repository fuer Submarine-Daten
     * @param shipId  DB-ID des Mutterschiffs
     */
    public SubmarineServer(int port, SubmarineRepository subRepo, long shipId) {
        this(port, subRepo, shipId, Integer.MAX_VALUE);
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            logger.info("SubmarineServer gestartet auf Port {}", port);

            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    int count = acceptedCount.incrementAndGet();
                    logger.info("Neue Submarine-Verbindung #{} von: {}",
                            count, client.getRemoteSocketAddress());
                    SubmarineSession session = new SubmarineSession(client, subRepo, shipId);
                    activeSessions.add(session);
                    session.setOnFinished(() -> activeSessions.remove(session));
                    session.start();
                } catch (IOException e) {
                    if (running) {
                        logger.warn("Fehler beim Akzeptieren: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logger.error("SubmarineServer konnte nicht starten auf Port {}: {}", port, e.getMessage());
        } finally {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * Wartet bis alle aktiven Sessions beendet sind oder das Timeout ablaeuft.
     *
     * @param timeoutMs maximale Wartezeit in Millisekunden
     * @throws InterruptedException wenn der Thread unterbrochen wird
     */
    public void waitForAllSessions(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!activeSessions.isEmpty() && System.currentTimeMillis() < deadline) {
            int count = activeSessions.size();
            logger.info("Warte auf {} laufende Submarine-Session(s)...", count);
            Thread.sleep(2000);
        }
        if (!activeSessions.isEmpty()) {
            logger.warn("Timeout - {} Session(s) noch aktiv, fahre fort.", activeSessions.size());
        }
    }

    /**
     * Gibt die Anzahl aktuell aktiver Sessions zurueck.
     *
     * @return Anzahl aktiver Sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Gibt die Submarine-IDs aller aktuell verbundenen Sessions zurueck.
     * Sessions die noch kein ready gesendet haben werden als "connecting-N" bezeichnet.
     *
     * @return Set mit Submarine-IDs
     */
    public java.util.Set<String> getActiveSubmarineIds() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        int unknown = 0;
        for (SubmarineSession s : activeSessions) {
            String id = s.getSubmarineId();
            if (id != null && !id.equals("unknown")) {
                ids.add(id);
            } else {
                unknown++;
                ids.add("connecting-" + unknown);
            }
        }
        return ids;
    }

    /**
     * Gibt eine Liste mit Status-Infos (id, step) aller aktiven Sessions zurueck.
     */
    public java.util.List<java.util.Map<String, Object>> getSessionInfos() {
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        int unknown = 0;
        for (SubmarineSession s : activeSessions) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            String id = s.getSubmarineId();
            if (id == null || id.equals("unknown")) {
                unknown++;
                id = "connecting-" + unknown;
            }
            m.put("submarineId", id);
            m.put("pilotStep",   s.getPilotStep());
            list.add(m);
        }
        return list;
    }

    /**
     * Sendet einen manuellen Pilot-Befehl an ein Submarine.
     *
     * @param submarineId ID des Submarines
     * @param route       Route-Wert (C, N, NE, E, SE, S, SW, W, NW, UP, DOWN, None)
     * @param action      Aktion (None, take_photo, locate, arise, ...)
     * @return true wenn Submarine gefunden und Befehl gesendet
     */
    public boolean pilotSubmarine(String submarineId, String route, String action) {
        for (SubmarineSession s : activeSessions) {
            if (submarineId.equals(s.getSubmarineId())) {
                s.enableManualMode();
                s.sendPilotManual(route, action);
                return true;
            }
        }
        return false;
    }

    /**
     * Sendet einen arise-Befehl an ein Submarine, damit es sich sauber beendet.
     *
     * @param submarineId ID des Submarines
     * @return true wenn gefunden und arise gesendet
     */
    public boolean ariseSubmarine(String submarineId) {
        for (SubmarineSession s : activeSessions) {
            if (submarineId.equals(s.getSubmarineId())) {
                s.sendAriseAndClose();
                return true;
            }
        }
        return false;
    }

    /**
     * Trennt ein Submarine nach ID (Force-Exit, nur als Fallback).
     *
     * @param submarineId ID des Submarines
     * @return true wenn gefunden und getrennt
     */
    public boolean disconnectSubmarine(String submarineId) {
        for (SubmarineSession s : activeSessions) {
            if (submarineId.equals(s.getSubmarineId())) {
                s.disconnect();
                return true;
            }
        }
        return false;
    }

    /**
     * Gibt zurueck ob aktuell mindestens ein Submarine taucht.
     *
     * @return true wenn mindestens ein Submarine aktiv taucht
     */
    public boolean isDiving() {
        return !activeSessions.isEmpty();
    }

    /**
     * Stoppt den SubmarineServer sauber.
     */
    public void shutdown() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.warn("Fehler beim Stoppen des SubmarineServers: {}", e.getMessage());
        }
        logger.info("SubmarineServer gestoppt.");
    }

    /**
     * Gibt zurueck ob der Server laeuft.
     *
     * @return true wenn laufend
     */
    public boolean isRunning() {
        return running;
    }
}
