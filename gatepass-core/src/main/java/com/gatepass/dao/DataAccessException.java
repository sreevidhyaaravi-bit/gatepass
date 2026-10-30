package com.gatepass.dao;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thrown by the DAO classes when the database fails. The message is written for ordinary
 * users (no SQL, no file paths); the technical details go to the log only.
 */
public class DataAccessException extends RuntimeException {

    private static final Logger LOG = Logger.getLogger(DataAccessException.class.getName());

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Converts a low-level SQLException into a friendly exception and logs the real cause. */
    public static DataAccessException from(String action, SQLException cause) {
        LOG.log(Level.WARNING, "Database problem while " + action, cause);
        String text = cause.getMessage() == null ? "" : cause.getMessage();
        boolean busy = text.contains("SQLITE_BUSY") || text.contains("database is locked");
        String friendly = busy
                ? "The database is busy right now. Please try again in a moment."
                : "Could not complete the database operation (" + action + ").";
        return new DataAccessException(friendly, cause);
    }
}
