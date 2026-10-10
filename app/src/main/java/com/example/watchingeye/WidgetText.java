package com.example.watchingeye;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

/** One line of text per user for the home-screen widget. No Android classes: unit tested. */
public final class WidgetText {

    private WidgetText() {}

    public static String row(LimitedUserStatus user, Instant now, ZoneId zone, Locale locale) {
        String name = user.getUsername() != null ? user.getUsername() : "Unknown";
        if (!user.isEnabled()) {
            return name + " — limit off";
        }
        Instant timeoutEnd = TimeOutFormat.activeEnd(user.getTimeOutUntil(), now);
        if (timeoutEnd != null) {
            return name + " — timed out until " + TimeOutFormat.describe(timeoutEnd, zone, locale);
        }
        switch (user.getBlockReason()) {
            case "TimeLimitExceeded":
                return name + " — limit reached";
            case "OutsideTimeWindow":
                return name + " — outside allowed hours";
            default:
                break;
        }
        double remaining = user.getRemainingSeconds();
        if (remaining < 0) {
            return name + " — no limit";
        }
        return name + " — " + TimeText.duration(remaining) + " left";
    }
}
