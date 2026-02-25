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
 * Zentrale Datenbank-Verbindungsklasse fuer Ocean Explorer.
 *
 * <pre>
 * Verwaltet MySQL-Verbindung mit HikariCP Connection Pool und jOOQ DSLContext.
 * Konfiguration ueber Umgebungsvariablen: DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD.
 * </pre>
 */
public class DatabaseConnection {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnection.class);

    private static DatabaseConnection instance;

    private HikariDataSource dataSource;
    private DSLContext dsl;

    private static final String DB_HOST     = System.getenv().getOrDefault("DB_HOST",     "localhost");
    private static final String DB_PORT     = System.getenv().getOrDefault("DB_PORT",     "3306");
    private static final String DB_NAME     = System.getenv().getOrDefault("DB_NAME",     "oceanexplorer");
    private static final String DB_USER     = System.getenv().getOrDefault("DB_USER",     "oceanapp");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "oceanpass123");

    private DatabaseConnection() {
    }

    /**
     * Gibt die Singleton-Instanz zurueck.
     *
     * @return DatabaseConnection-Instanz
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

        String jdbcUrl = String.format(
            "jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Europe/Berlin",
            DB_HOST, DB_PORT, DB_NAME);

        logger.info("Verbinde zu MySQL-Datenbank:");
        logger.info("  Host: {}:{}", DB_HOST, DB_PORT);
        logger.info("  Datenbank: {}", DB_NAME);
        logger.info("  Benutzer: {}", DB_USER);

        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(DB_USER);
            config.setPassword(DB_PASSWORD);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setPoolName("OceanExplorerPool");
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            dataSource = new HikariDataSource(config);
            dsl = DSL.using(dataSource, SQLDialect.MYSQL);

            logger.info("Datenbank verbunden (Connection Pool aktiv)");

            testConnection();
            migrateSchema();

        } catch (Exception e) {
            throw new SQLException("Fehler beim Verbinden zur Datenbank", e);
        }
    }

    /**
     * Fuehrt Schema-Migrationen durch.
     * Fuegt fehlende Spalten hinzu ohne bestehende Daten zu loeschen.
     */
    private void migrateSchema() {
        try (Connection conn = dataSource.getConnection()) {
            String[] photoColumns = {"x", "y", "z", "dir_x", "dir_y", "dir_z"};
            for (String col : photoColumns) {
                try (var rs = conn.getMetaData().getColumns(null, null, "submarine_photo", col)) {
                    if (!rs.next()) {
                        String sql = "ALTER TABLE submarine_photo ADD COLUMN " + col + " INT";
                        try (var stmt = conn.createStatement()) {
                            stmt.execute(sql);
                            logger.info("Schema-Migration: submarine_photo.{} hinzugefuegt", col);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Schema-Migration fehlgeschlagen (ignoriert): {}", e.getMessage());
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
                logger.info("Datenbank-Verbindung erfolgreich getestet");
            } else {
                throw new SQLException("Datenbank-Verbindung ungueltig");
            }
        }
    }

    /**
     * Gibt den jOOQ DSLContext zurueck.
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
     * Gibt die DataSource zurueck.
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
     * Gibt eine neue Connection aus dem Pool zurueck.
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
     * Schliesst die Datenbank-Verbindung und den Connection Pool.
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
     * Prueft ob Verbindung aktiv ist.
     *
     * @return true wenn verbunden
     */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Gibt Pool-Statistiken aus.
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
