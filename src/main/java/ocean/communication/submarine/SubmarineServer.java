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
 * Läuft im eigenen Daemon-Thread.
 * Für jede neue Submarine-Verbindung wird eine SubmarineSession gestartet.
 * Verwaltet alle aktiven Sessions und ermöglicht Warten bis alle fertig sind.
 *
 * @author OceanExplorer Team
 */
public class SubmarineServer extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(SubmarineServer.class);

    /** Port auf dem ShipApp auf Submarines wartet */
    public static final int DEFAULT_PORT = 9000;

    private final int port;
    private final SubmarineRepository subRepo;
    private final long shipId;
    private final int maxSessions;

    private ServerSocket serverSocket;
    private volatile boolean running = false;

    /** Zählt wie viele Verbindungen insgesamt akzeptiert wurden */
    private final AtomicInteger acceptedCount = new AtomicInteger(0);

    /** Alle aktiven SubmarineSessions (thread-safe) */
    private final Set<SubmarineSession> activeSessions =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** @param maxSessions Maximale Anzahl Submarines die akzeptiert werden (danach Server-Stop) */
    public SubmarineServer(int port, SubmarineRepository subRepo, long shipId, int maxSessions) {
        this.port = port;
        this.subRepo = subRepo;
        this.shipId = shipId;
        this.maxSessions = maxSessions;
        setName("SubmarineServer:" + port);
        setDaemon(true);
    }

    /** Konstruktor mit Standard-maxSessions (kein Limit = Integer.MAX_VALUE) */
    public SubmarineServer(int port, SubmarineRepository subRepo, long shipId) {
        this(port, subRepo, shipId, Integer.MAX_VALUE);
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            logger.info("=== SubmarineServer gestartet auf Port {} (max. {} Submarines) ===", port, maxSessions);

            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    int count = acceptedCount.incrementAndGet();
                    logger.info("Neue Submarine-Verbindung von: {} (#{} von {})",
                            client.getRemoteSocketAddress(), count, maxSessions);
                    SubmarineSession session = new SubmarineSession(client, subRepo, shipId);
                    activeSessions.add(session);
                    // Session aus der Menge entfernen wenn sie fertig ist
                    session.setOnFinished(() -> activeSessions.remove(session));
                    session.start();

                    // Nach maxSessions keine weiteren Verbindungen annehmen
                    if (count >= maxSessions) {
                        logger.info("Maximale Submarine-Anzahl ({}) erreicht – schließe Accept-Loop", maxSessions);
                        break;
                    }
                } catch (IOException e) {
                    if (running) {
                        logger.warn("Fehler beim Akzeptieren einer Submarine-Verbindung: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logger.error("SubmarineServer konnte nicht starten auf Port {}: {}", port, e.getMessage());
        } finally {
            // ServerSocket schließen, Sessions laufen weiter
            try {
                if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * Wartet bis alle aktiven Sessions beendet sind oder das Timeout abläuft.
     *
     * @param timeoutMs maximale Wartezeit in Millisekunden
     */
    public void waitForAllSessions(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!activeSessions.isEmpty() && System.currentTimeMillis() < deadline) {
            int count = activeSessions.size();
            logger.info("Warte auf {} laufende Submarine-Session(s)...", count);
            Thread.sleep(2000);
        }
        if (!activeSessions.isEmpty()) {
            logger.warn("Timeout – {} Session(s) noch aktiv, fahre fort.", activeSessions.size());
        }
    }

    /** Anzahl aktuell aktiver Sessions */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Gibt zurück ob aktuell mindestens ein Submarine taucht.
     * Solange dies true ist, darf das Schiff den Sektor NICHT wechseln (context.md Regel 2).
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

    public boolean isRunning() {
        return running;
    }
}
