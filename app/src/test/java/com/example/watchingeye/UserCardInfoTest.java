package com.example.watchingeye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

public class UserCardInfoTest {

    private static final Gson GSON = new Gson();
    private static final String NONE = "0001-01-01T00:00:00.0000000Z";

    /** A 60-minute daily limit with the given seconds used (older-plugin style row: no BlockReason / SecondsRemaining). */
    private static LimitedUserStatus status(double secondsDaily, String timeOut) {
        return GSON.fromJson("{\"UserId\":\"u1\",\"Username\":\"kid\",\"DailyLimitMinutes\":60,"
                + "\"SecondsWatchedDaily\":" + secondsDaily + ",\"TimeOutUntil\":\"" + timeOut + "\"}", LimitedUserStatus.class);
    }

    /** A row from plugin 1.8 with an explicit remaining time and block reason. */
    private static LimitedUserStatus status18(double remaining, String reason) {
        return GSON.fromJson("{\"UserId\":\"u1\",\"Username\":\"kid\",\"DailyLimitMinutes\":60,\"EffectiveDailyLimitMinutes\":60,"
                + "\"SecondsWatchedDaily\":0,\"TimeOutUntil\":\"" + NONE + "\",\"IsEnabled\":true,\"BlockReason\":\"" + reason + "\","
                + "\"SecondsRemaining\":" + remaining + ",\"LimitingPeriod\":\"Daily\",\"LimitingAllowedSeconds\":3600}", LimitedUserStatus.class);
    }

    private static SessionInfo session(long positionTicks, boolean paused) {
        return GSON.fromJson("{\"Id\":\"s1\",\"UserName\":\"kid\",\"Client\":\"Emby\",\"DeviceName\":\"TV\","
                + "\"NowPlayingItem\":{\"Id\":\"m1\",\"Name\":\"Movie\",\"RunTimeTicks\":54000000000},"
                + "\"PlayState\":{\"PositionTicks\":" + positionTicks + ",\"IsPaused\":" + paused + "}}", SessionInfo.class);
    }

    @Test
    public void identicalCardsLookTheSame() {
        assertTrue(UserCardInfo.sameDisplay(new UserCardInfo(status(600, NONE), null), new UserCardInfo(status(600, NONE), null)));
    }

    @Test
    public void subMinuteChangesDoNotRedraw() {
        assertTrue(UserCardInfo.sameDisplay(new UserCardInfo(status(600, NONE), null), new UserCardInfo(status(630, NONE), null)));
    }

    @Test
    public void extendingTheAllowedTimeRedraws() {
        LimitedUserStatus more = GSON.fromJson("{\"UserId\":\"u1\",\"Username\":\"kid\",\"DailyLimitMinutes\":60,\"EffectiveDailyLimitMinutes\":60,"
                + "\"SecondsRemaining\":4500,\"LimitingPeriod\":\"Daily\",\"LimitingAllowedSeconds\":4500,\"BlockReason\":\"Allowed\",\"TimeOutUntil\":\"" + NONE + "\"}", LimitedUserStatus.class);
        assertFalse(UserCardInfo.sameDisplay(new UserCardInfo(status18(3600, "Allowed"), null), new UserCardInfo(more, null)));
    }

    @Test
    public void aTimeOutChangeRedraws() {
        assertFalse(UserCardInfo.sameDisplay(new UserCardInfo(status(600, NONE), null),
                new UserCardInfo(status(600, "2099-01-01T00:00:00.0000000Z"), null)));
    }

    @Test
    public void startingOrStoppingPlaybackRedraws() {
        assertFalse(UserCardInfo.sameDisplay(new UserCardInfo(status(600, NONE), null),
                new UserCardInfo(status(600, NONE), session(0, false))));
    }

    @Test
    public void pausingRedrawsButMovingPositionDoesNot() {
        UserCardInfo a = new UserCardInfo(status(600, NONE), session(100_000_000L, false));
        assertTrue(UserCardInfo.sameDisplay(a, new UserCardInfo(status(600, NONE), session(120_000_000L, false))));
        assertFalse(UserCardInfo.sameDisplay(a, new UserCardInfo(status(600, NONE), session(100_000_000L, true))));
    }

    @Test
    public void aBlockReasonChangeRedraws() {
        assertFalse(UserCardInfo.sameDisplay(new UserCardInfo(status18(600, "Allowed"), null), new UserCardInfo(status18(600, "TimeLimitExceeded"), null)));
    }

    @Test
    public void countdownRunsWhileWatchingAndStopsWhenPausedOrBlocked() {
        long t0 = 1_000_000L;
        UserCardInfo playing = new UserCardInfo(status18(600, "Allowed"), session(0, false), t0, t0);
        assertEquals(570.0, playing.remainingSecondsNow(t0 + 30_000L), 0.001);
        UserCardInfo paused = new UserCardInfo(status18(600, "Allowed"), session(0, true), t0, t0);
        assertEquals(600.0, paused.remainingSecondsNow(t0 + 30_000L), 0.001);
        UserCardInfo idle = new UserCardInfo(status18(600, "Allowed"), null, t0, t0);
        assertEquals(600.0, idle.remainingSecondsNow(t0 + 30_000L), 0.001);
        UserCardInfo timedOut = new UserCardInfo(status18(600, "TimedOut"), session(0, false), t0, t0);
        assertEquals(600.0, timedOut.remainingSecondsNow(t0 + 30_000L), 0.001);
    }

    @Test
    public void countdownNeverGoesBelowZeroAndUnlimitedStaysUnlimited() {
        long t0 = 1_000_000L;
        assertEquals(0.0, new UserCardInfo(status18(10, "Allowed"), session(0, false), t0, t0).remainingSecondsNow(t0 + 60_000L), 0.001);
        assertEquals(-1.0, new UserCardInfo(status18(-1, "Allowed"), session(0, false), t0, t0).remainingSecondsNow(t0 + 60_000L), 0.001);
    }

    @Test
    public void positionAdvancesOnlyWhilePlaying() {
        long t0 = 5_000_000L;
        UserCardInfo playing = new UserCardInfo(status18(600, "Allowed"), session(100_000_000L, false), t0, t0);
        assertEquals(100_000_000L + 20_000L * 10_000L, playing.positionTicksNow(t0 + 20_000L));
        UserCardInfo paused = new UserCardInfo(status18(600, "Allowed"), session(100_000_000L, true), t0, t0);
        assertEquals(100_000_000L, paused.positionTicksNow(t0 + 20_000L));
    }

    @Test
    public void anOlderPluginStillGivesARemainingTime() {
        // 60-minute limit, 10 minutes used, no 1.8 fields: 50 minutes left.
        assertEquals(3000.0, status(600, NONE).getRemainingSeconds(), 0.001);
        assertEquals("Allowed", status(600, NONE).getBlockReason());
        assertEquals("TimedOut", status(600, "2999-01-01T00:00:00.0000000Z").getBlockReason());
    }

    @Test
    public void connectionFailuresAreExplained() {
        assertTrue(ErrorText.describeConnectionFailure(new java.net.UnknownHostException("x")).contains("find the server"));
        assertTrue(ErrorText.describeConnectionFailure(new java.net.SocketTimeoutException("x")).contains("not answering"));
        assertTrue(ErrorText.describeConnectionFailure(new java.io.IOException("x")).contains("IOException"));
        assertEquals(true, ErrorText.describeConnectionFailure(new RuntimeException("x")).endsWith(")."));
    }
}
