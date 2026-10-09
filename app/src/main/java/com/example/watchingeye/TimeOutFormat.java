package com.example.watchingeye;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Helpers for the plugin's {@code TimeOutUntil} value (an ISO-8601 UTC timestamp).
 * The plugin reports {@code 0001-01-01T00:00:00Z} when there is no time-out, and an older plugin version keeps
 * reporting a time-out after it has expired, so the app checks the time itself instead of trusting the text.
 * No Android classes are used, so this is covered by plain JVM unit tests.
 */
public final class TimeOutFormat {

    private TimeOutFormat() {}

    /** Returns the end of an active time-out, or null when there is none, it has expired, or the text is unusable. */
    public static Instant activeEnd(String isoUtc, Instant now) {
        if (isoUtc == null || isoUtc.trim().isEmpty() || isoUtc.startsWith("0001-01-01")) {
            return null;
        }
        try {
            Instant end = Instant.parse(isoUtc.trim());
            return end.isAfter(now) ? end : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Formats an instant in the phone's local time zone, e.g. "Oct 9, 2026, 3:19 PM". */
    public static String describe(Instant end, ZoneId zone, Locale locale) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(zone)
                .format(end);
    }
}
