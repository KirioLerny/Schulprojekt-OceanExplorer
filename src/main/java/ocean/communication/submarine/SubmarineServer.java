package ocean.communication.submarine;

import ocean.data.repository.SubmarineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * TCP-Server der auf eingehende Submarine-Verbindungen wartet.
 *
 * Laeuft im eigenen Daemon-Thread.
 * Fuer jede neue Submarine-Verbindung wird eine SubmarineSession gestartet.
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

    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public SubmarineServer(int port, SubmarineRepository subRepo, long shipId) {
        this.port = port;
        this.subRepo = subRepo;
        this.shipId = shipId;
        setName("SubmarineServer:" + port);
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            logger.info("=== SubmarineServer gestartet auf Port {} ===", port);

            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    logger.info("Neue Submarine-Verbindung von: {}", client.getRemoteSocketAddress());
                    SubmarineSession session = new SubmarineSession(client, subRepo, shipId);
                    session.start();
                } catch (IOException e) {
                    if (running) {
                        logger.warn("Fehler beim Akzeptieren einer Submarine-Verbindung: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logger.error("SubmarineServer konnte nicht starten auf Port {}: {}", port, e.getMessage());
        }
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
