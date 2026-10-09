package com.example.watchingeye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

import org.junit.Test;

public class TimeOutFormatTest {

    private static final Instant NOW = Instant.parse("2026-10-09T05:00:00Z");

    @Test
    public void noTimeOutValuesAreNone() {
        assertNull(TimeOutFormat.activeEnd(null, NOW));
        assertNull(TimeOutFormat.activeEnd("", NOW));
        assertNull(TimeOutFormat.activeEnd("0001-01-01T00:00:00.0000000Z", NOW));
        assertNull(TimeOutFormat.activeEnd("0001-01-01T00:00:00Z", NOW));
    }

    @Test
    public void expiredTimeOutIsNone() {
        // An older plugin keeps reporting a time-out after it has ended.
        assertNull(TimeOutFormat.activeEnd("2026-10-09T04:59:59.9999999Z", NOW));
        assertNull(TimeOutFormat.activeEnd("2026-10-09T05:00:00Z", NOW));
    }

    @Test
    public void futureTimeOutIsParsedWithPluginFractionDigits() {
        Instant end = TimeOutFormat.activeEnd("2026-10-09T05:25:33.2318888Z", NOW);
        assertNotNull(end);
        assertEquals(Instant.parse("2026-10-09T05:25:33.2318888Z"), end);
    }

    @Test
    public void garbageIsNone() {
        assertNull(TimeOutFormat.activeEnd("not a date", NOW));
        assertNull(TimeOutFormat.activeEnd("2026-13-45T99:99:99Z", NOW));
    }

    @Test
    public void describeUsesTheGivenZone() {
        Instant end = Instant.parse("2026-10-09T19:19:00Z");
        String eastern = TimeOutFormat.describe(end, ZoneId.of("America/New_York"), Locale.US);
        String utc = TimeOutFormat.describe(end, ZoneId.of("UTC"), Locale.US);
        assertTrue(eastern, eastern.contains("3:19"));
        assertTrue(utc, utc.contains("7:19"));
    }
}
