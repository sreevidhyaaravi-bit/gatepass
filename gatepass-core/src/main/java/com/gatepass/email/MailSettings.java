package com.gatepass.email;

import com.gatepass.util.AppConfig;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * SMTP settings for parent emails, read from ONE file: {@code gatepass-mail.properties}
 * (location chosen by {@link AppConfig#mailSettingsPath()}).
 *
 * <p>If the file is missing or incomplete, the object is still created but
 * {@link #isConfigured()} is false and {@link #getProblem()} says why. The gate pass system then
 * keeps working and every movement honestly reports "email not sent".</p>
 *
 * <p>The password is kept private and is never printed: {@link #toString()} leaves it out.</p>
 */
public final class MailSettings {

    /** How the connection to the mail server is protected. */
    public enum Security {
        STARTTLS, // usually port 587 (Gmail, Outlook)
        SSL,      // usually port 465
        NONE      // only for local test servers such as Mailpit (port 1025)
    }

    private static final int DEFAULT_TIMEOUT_MS = 15_000;

    private final String host;
    private final int port;
    private final Security security;
    private final String username;
    private final String password;
    private final String fromAddress;
    private final String fromName;
    private final int timeoutMs;
    private final String problem; // null when the settings are complete

    private MailSettings(String host, int port, Security security, String username, String password,
                         String fromAddress, String fromName, int timeoutMs, String problem) {
        this.host = host;
        this.port = port;
        this.security = security;
        this.username = username;
        this.password = password;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.timeoutMs = timeoutMs;
        this.problem = problem;
    }

    // ---------------------------------------------------------------- loading

    /** Reads the settings file used by both applications. */
    public static MailSettings load() {
        return load(AppConfig.mailSettingsPath());
    }

    public static MailSettings load(Path file) {
        if (!Files.isRegularFile(file)) {
            return notConfigured("settings file not found: " + file);
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            return notConfigured("settings file could not be read: " + file);
        }
        return fromProperties(props);
    }

    /**
     * Builds settings from key/value pairs. Required: {@code smtp.host} and {@code mail.from}.
     * {@code smtp.username} may be empty for local test servers that need no login.
     */
    public static MailSettings fromProperties(Properties props) {
        if (!Boolean.parseBoolean(value(props, "mail.enabled", "true"))) {
            return notConfigured("emails are switched off (mail.enabled=false)");
        }
        String host = value(props, "smtp.host", "");
        String from = value(props, "mail.from", "");
        if (host.isEmpty()) {
            return notConfigured("smtp.host is empty");
        }
        if (from.isEmpty()) {
            return notConfigured("mail.from is empty");
        }

        Security security;
        try {
            security = Security.valueOf(value(props, "smtp.security", "STARTTLS").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return notConfigured("smtp.security must be STARTTLS, SSL or NONE");
        }
        int defaultPort = switch (security) {
            case STARTTLS -> 587;
            case SSL -> 465;
            case NONE -> 25;
        };
        int port;
        int timeout;
        try {
            port = Integer.parseInt(value(props, "smtp.port", String.valueOf(defaultPort)));
            timeout = Integer.parseInt(value(props, "smtp.timeout.ms", String.valueOf(DEFAULT_TIMEOUT_MS)));
        } catch (NumberFormatException e) {
            return notConfigured("smtp.port and smtp.timeout.ms must be numbers");
        }

        String username = value(props, "smtp.username", "");
        String password = props.getProperty("smtp.password", ""); // not trimmed: spaces can be part of it
        if (!username.isEmpty() && password.isEmpty()) {
            return notConfigured("smtp.password is empty (needed because smtp.username is set)");
        }
        String fromName = value(props, "mail.from.name", "Hostel Gate Office");
        return new MailSettings(host, port, security, username, password, from, fromName, timeout, null);
    }

    private static MailSettings notConfigured(String problem) {
        return new MailSettings(null, 0, Security.NONE, null, null, null, null, DEFAULT_TIMEOUT_MS, problem);
    }

    private static String value(Properties props, String key, String fallback) {
        String v = props.getProperty(key);
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    // ---------------------------------------------------------------- for EmailService

    public boolean isConfigured() {
        return problem == null;
    }

    /** Why emails cannot be sent (null when everything is set). Safe to show: contains no secrets. */
    public String getProblem() {
        return problem;
    }

    public boolean needsLogin() {
        return username != null && !username.isEmpty();
    }

    String getUsername() {
        return username;
    }

    String getPassword() {
        return password;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    /** The Jakarta Mail ("mail.smtp.*") properties for this server. */
    Properties toJakartaMailProperties() {
        Properties p = new Properties();
        String prefix = security == Security.SSL ? "mail.smtps." : "mail.smtp.";
        p.put("mail.transport.protocol", security == Security.SSL ? "smtps" : "smtp");
        p.put(prefix + "host", host);
        p.put(prefix + "port", String.valueOf(port));
        p.put(prefix + "auth", String.valueOf(needsLogin()));
        // Time limits, so a dead mail server can never keep the email thread waiting forever.
        p.put(prefix + "connectiontimeout", String.valueOf(timeoutMs));
        p.put(prefix + "timeout", String.valueOf(timeoutMs));
        p.put(prefix + "writetimeout", String.valueOf(timeoutMs));
        if (security == Security.STARTTLS) {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "true");
        }
        if (security != Security.NONE) {
            p.put(prefix + "ssl.checkserveridentity", "true");
        }
        return p;
    }

    /** Short description for logs and status lines. Never contains the password. */
    @Override
    public String toString() {
        if (!isConfigured()) {
            return "Parent emails OFF (" + problem + ")";
        }
        return "Parent emails ON via " + host + ":" + port + " (" + security + ")";
    }
}
