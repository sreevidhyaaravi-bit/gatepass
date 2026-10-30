package com.gatepass.db;

import com.gatepass.util.AppConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.sqlite.SQLiteConfig;

/**
 * Hands out JDBC connections to the ONE shared gatepass.db file.
 *
 * <p>Both the website and the guard app call {@link #getConnection()}, and both resolve the
 * file location through {@link AppConfig#databasePath()}, so they always see the same data.</p>
 *
 * <p>Every connection is configured the same way:</p>
 * <ul>
 *   <li>foreign keys ON (SQLite ignores them by default),</li>
 *   <li>busy timeout 5 s: if another program is writing, wait instead of failing at once,</li>
 *   <li>WAL journal: readers and a writer can work together (web + guard at the same time),</li>
 *   <li>IMMEDIATE transactions: a transaction takes the write lock when it starts, which keeps
 *       "read the last pass number, then insert the next one" safe.</li>
 * </ul>
 */
public final class DBConnection {

    private static final int BUSY_TIMEOUT_MS = 5000;

    private static volatile Path databasePath = AppConfig.databasePath();

    static {
        try {
            Class.forName("org.sqlite.JDBC"); // makes sure the SQLite driver is registered (also inside Tomcat)
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("SQLite JDBC driver is missing from the classpath");
        }
    }

    private DBConnection() {
    }

    /** Changes the database file (used by tests and by an optional advanced setup). */
    public static void setDatabasePath(Path path) {
        databasePath = path.toAbsolutePath().normalize();
    }

    public static Path getDatabasePath() {
        return databasePath;
    }

    /**
     * Opens a new connection. The CALLER must close it, ideally with try-with-resources:
     * <pre>try (Connection conn = DBConnection.getConnection()) { ... }</pre>
     */
    public static Connection getConnection() throws SQLException {
        Path path = databasePath;
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            throw new SQLException("Cannot create the database folder: " + path.getParent(), e);
        }

        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(BUSY_TIMEOUT_MS);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);

        return DriverManager.getConnection("jdbc:sqlite:" + path, config.toProperties());
    }
}
