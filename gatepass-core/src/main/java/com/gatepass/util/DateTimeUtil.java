package com.gatepass.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * The ONLY place that knows how date-times are written in the database and in forms.
 *
 * <p>Rule of the project: text is converted to {@link LocalDateTime} first, and only the Java
 * objects are compared ({@code isBefore}, {@code isAfter}). We never compare date strings.</p>
 */
public final class DateTimeUtil {

    /** Persistence format used in every SQLite date column: e.g. 2026-10-30 14:05 */
    public static final DateTimeFormatter DB_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Friendly format for people (web pages, guard window, parent emails): e.g. 30 Oct 2026, 02:05 PM.
     * English month names are fixed, so a laptop set to another language shows the same text.
     */
    public static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    private DateTimeUtil() {
    }

    /** LocalDateTime -> text for the database / screens (null stays null). */
    public static String format(LocalDateTime value) {
        return value == null ? null : value.format(DB_FORMAT);
    }

    /** LocalDateTime -> friendly text for screens and emails; "-" when there is no value yet. */
    public static String display(LocalDateTime value) {
        return value == null ? "-" : value.format(DISPLAY_FORMAT);
    }

    /** Database text -> LocalDateTime (null/blank stays null). */
    public static LocalDateTime parseDb(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(text.trim(), DB_FORMAT);
    }

    /**
     * Form text -> LocalDateTime. Accepts both "2026-10-30T14:05" (what an HTML
     * datetime-local box sends) and "2026-10-30 14:05". Seconds, if any, are dropped.
     *
     * @throws DateTimeParseException when the text is not a real date-time (e.g. month 13)
     */
    public static LocalDateTime parseInput(String text) {
        String cleaned = text.trim().replace(' ', 'T');
        return LocalDateTime.parse(cleaned).truncatedTo(ChronoUnit.MINUTES);
    }

    /** Human friendly duration, e.g. "2 h 15 min". */
    public static String formatDuration(Duration duration) {
        long totalMinutes = Math.max(0, duration.toMinutes());
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours == 0) {
            return minutes + " min";
        }
        return hours + " h " + minutes + " min";
    }
}
