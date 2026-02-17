package ocean.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Zentrale Datenbank-Verbindungsklasse für Ocean Explorer.
 *
 * Verwaltet MySQL-Verbindung mit HikariCP Connection Pool und jOOQ DSLContext.
 *
 * @author OceanExplorer Team
 */
public class DatabaseConnection {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnection.class);

    /** Singleton-Instance */
    private static DatabaseConnection instance;

    /** HikariCP DataSource (Connection Pool) */
    private HikariDataSource dataSource;

    /** jOOQ DSL Context */
    private DSLContext dsl;

    // Datenbank-Konfiguration (kann über Umgebungsvariablen überschrieben werden)
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "oceanexplorer");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "oceanapp");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "oceanpass123");

    /**
     * Privater Konstruktor (Singleton).
     */
    private DatabaseConnection() {
        // Wird über getInstance() aufgerufen
    }

    /**
     * Gibt die Singleton-Instance zurück.
     *
     * @return DatabaseConnection-Instance
     */
    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    /**
     * Initialisiert die Datenbank-Verbindung mit Connection Pooling.
     *
     * @throws SQLException bei Verbindungsfehlern
     */
    public void connect() throws SQLException {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.debug("Datenbank bereits verbunden");
            return;
        }

        String jdbcUrl = String.format("jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Europe/Berlin",
                DB_HOST, DB_PORT, DB_NAME);

        logger.info("Verbinde zu MySQL-Datenbank:");
        logger.info("  Host: {}:{}", DB_HOST, DB_PORT);
        logger.info("  Datenbank: {}", DB_NAME);
        logger.info("  Benutzer: {}", DB_USER);

        try {
            // HikariCP konfigurieren
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(DB_USER);
            config.setPassword(DB_PASSWORD);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Connection Pool Einstellungen
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setPoolName("OceanExplorerPool");

            // Performance-Optimierungen
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            // DataSource erstellen
            dataSource = new HikariDataSource(config);

            // jOOQ DSLContext erstellen
            dsl = DSL.using(dataSource, SQLDialect.MYSQL);

            logger.info("✅ Datenbank verbunden (Connection Pool aktiv)");

            // Verbindung testen
            testConnection();

        } catch (Exception e) {
            throw new SQLException("Fehler beim Verbinden zur Datenbank", e);
        }
    }

    /**
     * Testet die Datenbank-Verbindung.
     *
     * @throws SQLException bei Verbindungsfehlern
     */
    private void testConnection() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(5)) {
                logger.info("✅ Datenbank-Verbindung erfolgreich getestet");
            } else {
                throw new SQLException("Datenbank-Verbindung ungültig");
            }
        }
    }

    /**
     * Gibt den jOOQ DSLContext zurück.
     *
     * @return jOOQ DSLContext
     */
    public DSLContext getDSL() {
        if (dsl == null) {
            throw new IllegalStateException("Datenbank nicht verbunden! Rufe connect() auf.");
        }
        return dsl;
    }

    /**
     * Gibt die DataSource zurück (für Transaktionen).
     *
     * @return HikariCP DataSource
     */
    public DataSource getDataSource() {
        if (dataSource == null) {
            throw new IllegalStateException("Datenbank nicht verbunden! Rufe connect() auf.");
        }
        return dataSource;
    }

    /**
     * Gibt eine neue Connection aus dem Pool zurück.
     *
     * @return JDBC Connection
     * @throws SQLException bei Fehlern
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("Datenbank nicht verbunden! Rufe connect() auf.");
        }
        return dataSource.getConnection();
    }

    /**
     * Schließt die Datenbank-Verbindung und den Connection Pool.
     */
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Datenbank-Verbindung geschlossen (Connection Pool beendet)");
            dataSource = null;
            dsl = null;
        }
    }

    /**
     * Prüft ob Verbindung aktiv ist.
     *
     * @return true wenn verbunden
     */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Gibt Pool-Statistiken aus (für Monitoring).
     */
    public void logPoolStats() {
        if (dataSource != null) {
            logger.info("Connection Pool Stats:");
            logger.info("  Aktive Verbindungen: {}", dataSource.getHikariPoolMXBean().getActiveConnections());
            logger.info("  Idle Verbindungen: {}", dataSource.getHikariPoolMXBean().getIdleConnections());
            logger.info("  Total Verbindungen: {}", dataSource.getHikariPoolMXBean().getTotalConnections());
            logger.info("  Wartende Threads: {}", dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
        }
    }
}
