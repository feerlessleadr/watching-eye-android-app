package com.example.watchingeye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LiveMessagesTest {

    @Test
    public void readsAStatusPush() {
        String json = "{\"MessageType\":\"WatchingEyeStatus\",\"Data\":{\"Version\":7,\"ServerTimeUtc\":\"2026-10-10T04:56:18Z\",\"Users\":["
                + "{\"UserId\":\"u1\",\"Username\":\"O'Brien & Co\",\"DailyLimitMinutes\":60,\"BlockReason\":\"TimedOut\",\"SecondsRemaining\":120.5,"
                + "\"LimitingPeriod\":\"Weekly\",\"LimitingAllowedSeconds\":7200,\"TimeOutUntil\":\"2099-01-01T00:00:00Z\",\"IsEnabled\":true}]}}";
        LiveMessages.Message m = LiveMessages.parse(json);
        assertEquals(LiveMessages.Kind.STATUS, m.kind);
        assertNull(m.error);
        assertEquals(1, m.users.size());
        LimitedUserStatus u = m.users.get(0);
        assertEquals("O'Brien & Co", u.getUsername());
        assertEquals("TimedOut", u.getBlockReason());
        assertEquals(120.5, u.getRemainingSeconds(), 0.0001);
        assertEquals("weekly", u.getLimitingPeriodLabel());
        assertEquals(7200.0, u.getLimitingAllowedSeconds(), 0.0001);
    }

    @Test
    public void readsAnErrorPush() {
        LiveMessages.Message m = LiveMessages.parse("{\"MessageType\":\"WatchingEyeStatus\",\"Data\":{\"Version\":2,\"Users\":[],\"Error\":\"Unauthorized: nope\"}}");
        assertEquals(LiveMessages.Kind.STATUS, m.kind);
        assertEquals("Unauthorized: nope", m.error);
        assertTrue(m.users.isEmpty());
    }

    @Test
    public void readsASessionsPush() {
        String json = "{\"MessageType\":\"Sessions\",\"Data\":[{\"Id\":\"s1\",\"UserName\":\"kid\",\"Client\":\"Emby\","
                + "\"NowPlayingItem\":{\"Id\":\"m1\",\"Name\":\"Movie\"},\"PlayState\":{\"PositionTicks\":5,\"IsPaused\":true}},{\"Id\":\"s2\"}]}";
        LiveMessages.Message m = LiveMessages.parse(json);
        assertEquals(LiveMessages.Kind.SESSIONS, m.kind);
        assertEquals(2, m.sessions.size());
        assertEquals("kid", m.sessions.get(0).getUserName());
        assertTrue(m.sessions.get(0).getPlayState().isPaused());
    }

    @Test
    public void readsAKeepAliveRequest() {
        LiveMessages.Message m = LiveMessages.parse("{\"MessageType\":\"ForceKeepAlive\",\"Data\":60}");
        assertEquals(LiveMessages.Kind.KEEP_ALIVE_REQUEST, m.kind);
        assertEquals(60, m.keepAliveSeconds);
    }

    @Test
    public void ignoresEverythingElseWithoutFailing() {
        assertEquals(LiveMessages.Kind.OTHER, LiveMessages.parse("{\"MessageType\":\"LibraryChanged\",\"Data\":{}}").kind);
        assertEquals(LiveMessages.Kind.OTHER, LiveMessages.parse("not json at all").kind);
        assertEquals(LiveMessages.Kind.OTHER, LiveMessages.parse("[1,2,3]").kind);
        assertEquals(LiveMessages.Kind.OTHER, LiveMessages.parse("").kind);
        LiveMessages.Message broken = LiveMessages.parse("{\"MessageType\":\"Sessions\",\"Data\":\"oops\"}");
        assertEquals(LiveMessages.Kind.SESSIONS, broken.kind);
        assertTrue(broken.sessions.isEmpty());
    }

    @Test
    public void subscriptionMessagesCarryTheKeyInTheBodyNotTheUrl() {
        String start = LiveMessages.subscribeStatus("abc123");
        assertTrue(start.contains("WatchingEyeStatusStart"));
        assertTrue(start.contains("0,30000,abc123"));
        assertTrue(LiveMessages.subscribeSessions().contains("SessionsStart"));
        assertEquals("{\"MessageType\":\"KeepAlive\"}", LiveMessages.keepAlive());
        String url = LiveMessages.webSocketUrl("https://emby.example.com/", "dev");
        assertEquals("wss://emby.example.com/embywebsocket?deviceId=dev", url);
        assertEquals("ws://10.0.2.2:8096/embywebsocket?deviceId=dev", LiveMessages.webSocketUrl("http://10.0.2.2:8096", "dev"));
        assertTrue(!url.contains("api_key"));
    }
}
