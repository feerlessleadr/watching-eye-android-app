package com.example.watchingeye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

import org.junit.Test;

public class WidgetTextTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    private static String row(String json) {
        LimitedUserStatus u = new Gson().fromJson(json, LimitedUserStatus.class);
        return WidgetText.row(u, NOW, ZoneId.of("UTC"), Locale.US);
    }

    private static String user(String extra) {
        return "{\"UserId\":\"u\",\"Username\":\"Kid\",\"TimeOutUntil\":\"0001-01-01T00:00:00Z\"" + extra + "}";
    }

    @Test
    public void showsTimeLeft() {
        assertEquals("Kid — 1 h 05 min left", row(user(",\"SecondsRemaining\":3900,\"BlockReason\":\"Allowed\"")));
    }

    @Test
    public void showsEachBlockedState() {
        assertEquals("Kid — limit reached", row(user(",\"SecondsRemaining\":0,\"BlockReason\":\"TimeLimitExceeded\"")));
        assertEquals("Kid — outside allowed hours", row(user(",\"BlockReason\":\"OutsideTimeWindow\"")));
        assertTrue(row("{\"UserId\":\"u\",\"Username\":\"Kid\",\"TimeOutUntil\":\"2026-10-10T13:00:00Z\",\"BlockReason\":\"TimedOut\"}").startsWith("Kid — timed out until "));
    }

    @Test
    public void showsNoLimitAndSwitchedOff() {
        assertEquals("Kid — no limit", row(user(",\"SecondsRemaining\":-1,\"BlockReason\":\"Allowed\"")));
        assertEquals("Kid — limit off", row(user(",\"IsEnabled\":false")));
    }
}
