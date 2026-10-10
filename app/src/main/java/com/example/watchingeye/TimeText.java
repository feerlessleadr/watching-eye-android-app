package com.example.watchingeye;

import java.util.Locale;

/** Short, readable time texts for the cards. No Android classes, so it is covered by ordinary unit tests. */
public final class TimeText {

    private TimeText() {}

    /** "1 h 05 min", "42 min", "9 min 30 s", "45 s". Negative values count as zero. */
    public static String duration(double seconds) {
        long total = (long) Math.max(0.0, Math.floor(seconds));
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long secs = total % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d h %02d min", hours, minutes);
        }
        if (minutes >= 10) {
            return minutes + " min";
        }
        if (minutes >= 1) {
            return String.format(Locale.ROOT, "%d min %02d s", minutes, secs);
        }
        return secs + " s";
    }

    /** Playback position like Emby shows it: "7:05" or "1:02:03". */
    public static String clock(long ticks) {
        long totalSeconds = Math.max(0L, ticks) / 10_000_000L;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long secs = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }

    /** "today", "this week", "this month", "this year" for the limiting period name (daily, weekly, ...). */
    public static String periodPhrase(String period) {
        switch (period == null ? "" : period.toLowerCase(Locale.ROOT)) {
            case "weekly":
                return "this week";
            case "monthly":
                return "this month";
            case "yearly":
                return "this year";
            default:
                return "today";
        }
    }
}
