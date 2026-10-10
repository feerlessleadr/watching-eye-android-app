package com.example.watchingeye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class TimeTextAndAlertsTest {

    @Test
    public void formatsDurations() {
        assertEquals("45 s", TimeText.duration(45.9));
        assertEquals("0 s", TimeText.duration(-5));
        assertEquals("9 min 30 s", TimeText.duration(570));
        assertEquals("42 min", TimeText.duration(42 * 60 + 59));
        assertEquals("1 h 05 min", TimeText.duration(3900));
        assertEquals("2 h 00 min", TimeText.duration(7200));
    }

    @Test
    public void formatsPlaybackClock() {
        assertEquals("0:00", TimeText.clock(0));
        assertEquals("7:05", TimeText.clock((7 * 60 + 5) * 10_000_000L));
        assertEquals("1:02:03", TimeText.clock((3600 + 2 * 60 + 3) * 10_000_000L));
    }

    @Test
    public void phrasesPeriods() {
        assertEquals("today", TimeText.periodPhrase("daily"));
        assertEquals("this week", TimeText.periodPhrase("Weekly"));
        assertEquals("this month", TimeText.periodPhrase("MONTHLY"));
        assertEquals("this year", TimeText.periodPhrase("yearly"));
        assertEquals("today", TimeText.periodPhrase(null));
    }

    private static UserCardInfo card(String id, String reason, boolean enabled) {
        String json = "{\"UserId\":\"" + id + "\",\"Username\":\"" + id + "\",\"DailyLimitMinutes\":60,\"IsEnabled\":" + enabled
                + ",\"BlockReason\":\"" + reason + "\",\"SecondsRemaining\":0,\"LimitingPeriod\":\"Daily\",\"LimitingAllowedSeconds\":3600,"
                + "\"TimeOutUntil\":\"0001-01-01T00:00:00Z\"}";
        return new UserCardInfo(new Gson().fromJson(json, LimitedUserStatus.class), null);
    }

    @Test
    public void alertsOnlyWhenSomeoneGoesFromAllowedToOverTheLimit() {
        LimitAlerts alerts = new LimitAlerts();
        // First look: records the state, never alerts (even for a user who is already over).
        assertTrue(alerts.newlyBlocked(Arrays.asList(card("a", "Allowed", true), card("b", "TimeLimitExceeded", true))).isEmpty());
        // a reaches the limit: one alert. b stays over: no alert.
        List<LimitedUserStatus> now = alerts.newlyBlocked(Arrays.asList(card("a", "TimeLimitExceeded", true), card("b", "TimeLimitExceeded", true)));
        assertEquals(1, now.size());
        assertEquals("a", now.get(0).getUserId());
        // Repeated updates of the same state: quiet.
        assertTrue(alerts.newlyBlocked(Arrays.asList(card("a", "TimeLimitExceeded", true), card("b", "TimeLimitExceeded", true))).isEmpty());
        // a is allowed again (extended), then reaches the limit again: alert again.
        assertTrue(alerts.newlyBlocked(Arrays.asList(card("a", "Allowed", true), card("b", "TimeLimitExceeded", true))).isEmpty());
        assertEquals(1, alerts.newlyBlocked(Arrays.asList(card("a", "TimeLimitExceeded", true), card("b", "TimeLimitExceeded", true))).size());
    }

    @Test
    public void timeOutsAndSwitchedOffUsersDoNotAlert() {
        LimitAlerts alerts = new LimitAlerts();
        alerts.newlyBlocked(Arrays.asList(card("a", "Allowed", true), card("c", "Allowed", true)));
        // A time-out the parent started themself is not a "limit reached" event; a switched-off limit never is either.
        assertTrue(alerts.newlyBlocked(Arrays.asList(card("a", "TimedOut", true), card("c", "TimeLimitExceeded", false))).isEmpty());
    }

    @Test
    public void emptyListsAndResetAreHandled() {
        LimitAlerts alerts = new LimitAlerts();
        assertTrue(alerts.newlyBlocked(Collections.emptyList()).isEmpty());
        alerts.newlyBlocked(Collections.singletonList(card("a", "Allowed", true)));
        alerts.reset();
        assertTrue(alerts.newlyBlocked(Collections.singletonList(card("a", "TimeLimitExceeded", true))).isEmpty());
    }
}
