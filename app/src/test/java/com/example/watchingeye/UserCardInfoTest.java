package com.example.watchingeye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

public class UserCardInfoTest {

    private static final Gson GSON = new Gson();

    private static LimitedUserStatus status(double secondsDaily, String timeOut) {
        return GSON.fromJson("{\"UserId\":\"u1\",\"Username\":\"kid\",\"DailyLimitMinutes\":60,"
                + "\"SecondsWatchedDaily\":" + secondsDaily + ",\"TimeOutUntil\":\"" + timeOut + "\"}", LimitedUserStatus.class);
    }

    private static SessionInfo session(long positionTicks) {
        return GSON.fromJson("{\"Id\":\"s1\",\"UserName\":\"kid\",\"Client\":\"Emby\",\"DeviceName\":\"TV\","
                + "\"NowPlayingItem\":{\"Id\":\"m1\",\"Name\":\"Movie\",\"RunTimeTicks\":54000000000},"
                + "\"PlayState\":{\"PositionTicks\":" + positionTicks + "}}", SessionInfo.class);
    }

    private static final String NONE = "0001-01-01T00:00:00.0000000Z";

    @Test
    public void identicalCardsLookTheSame() {
        assertTrue(UserCardInfo.sameDisplay(new UserCardInfo(status(600, NONE), null), new UserCardInfo(status(600, NONE), null)));
    }

    @Test
    public void subMinuteChangesDoNotRedraw() {
        assertTrue(UserCardInfo.sameDisplay(new UserCardInfo(status(600, NONE), null), new UserCardInfo(status(630, NONE), null)));
    }

    @Test
    public void aNewMinuteRedraws() {
        assertFalse(UserCardInfo.sameDisplay(new UserCardInfo(status(600, NONE), null), new UserCardInfo(status(660, NONE), null)));
    }

    @Test
    public void aTimeOutChangeRedraws() {
        assertFalse(UserCardInfo.sameDisplay(new UserCardInfo(status(600, NONE), null),
                new UserCardInfo(status(600, "2099-01-01T00:00:00.0000000Z"), null)));
    }

    @Test
    public void startingOrStoppingPlaybackRedraws() {
        assertFalse(UserCardInfo.sameDisplay(new UserCardInfo(status(600, NONE), null),
                new UserCardInfo(status(600, NONE), session(0))));
    }

    @Test
    public void playbackProgressRedrawsOnlyWhenTheSecondChanges() {
        UserCardInfo a = new UserCardInfo(status(600, NONE), session(100_000_000L));
        assertTrue(UserCardInfo.sameDisplay(a, new UserCardInfo(status(600, NONE), session(100_000_000L))));
        assertFalse(UserCardInfo.sameDisplay(a, new UserCardInfo(status(600, NONE), session(120_000_000L))));
    }

    @Test
    public void connectionFailuresAreExplained() {
        assertTrue(MainViewModel.describeConnectionFailure(new java.net.UnknownHostException("x")).contains("find the server"));
        assertTrue(MainViewModel.describeConnectionFailure(new java.net.SocketTimeoutException("x")).contains("not answering"));
        assertTrue(MainViewModel.describeConnectionFailure(new java.io.IOException("x")).contains("IOException"));
        assertEquals(true, MainViewModel.describeConnectionFailure(new RuntimeException("x")).endsWith("Retrying..."));
    }
}
