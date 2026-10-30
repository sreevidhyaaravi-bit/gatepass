package com.gatepass.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;

/**
 * One place for settings that BOTH the website (Tomcat) and the guard desktop app must agree on.
 *
 * <p>Most important: where {@code gatepass.db} lives. The location is resolved the same way in
 * both programs, so they always open the SAME file (never one copy under Tomcat's folder and
 * another under the Swing program's folder).</p>
 */
public final class AppConfig {

    /** Campus-local timezone used for every "now" and "today" in the project. */
    public static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Kolkata");

    /** Name of the SQLite database file. */
    public static final String DB_FILE_NAME = "gatepass.db";

    /** Name of the local SMTP settings file (never committed to Git, see .gitignore). */
    public static final String MAIL_FILE_NAME = "gatepass-mail.properties";

    private AppConfig() {
        // utility class: nobody should create objects of it
    }

    /**
     * Resolves the absolute path of gatepass.db. Order of preference:
     * <ol>
     *   <li>JVM option {@code -Dgatepass.db.path=/some/where/gatepass.db}</li>
     *   <li>environment variable {@code GATEPASS_DB_PATH}</li>
     *   <li>default: {@code <your home folder>/gatepass-data/gatepass.db}</li>
     * </ol>
     * The default works for both apps as long as both run as the same operating-system user
     * (true when you run Tomcat and the guard app yourself on one laptop).
     */
    public static Path databasePath() {
        String configured = System.getProperty("gatepass.db.path");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("GATEPASS_DB_PATH");
        }
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured.trim()).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.home"), "gatepass-data", DB_FILE_NAME)
                .toAbsolutePath().normalize();
    }

    /**
     * Resolves the SMTP settings file: the ONE place where email configuration lives.
     * Order of preference:
     * <ol>
     *   <li>JVM option {@code -Dgatepass.mail.config=/some/where/gatepass-mail.properties}</li>
     *   <li>environment variable {@code GATEPASS_MAIL_CONFIG}</li>
     *   <li>default: the same folder as gatepass.db, e.g. {@code <home>/gatepass-data/gatepass-mail.properties}</li>
     * </ol>
     * Keeping it next to the database means the website and the guard app always read the same
     * settings, and the file stays outside the project folder (so it cannot be committed by accident).
     */
    public static Path mailSettingsPath() {
        String configured = System.getProperty("gatepass.mail.config");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("GATEPASS_MAIL_CONFIG");
        }
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured.trim()).toAbsolutePath().normalize();
        }
        return databasePath().resolveSibling(MAIL_FILE_NAME);
    }
}
