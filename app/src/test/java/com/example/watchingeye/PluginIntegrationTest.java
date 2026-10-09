package com.example.watchingeye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.time.Instant;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import retrofit2.Response;

/**
 * Talks to a REAL Emby server with the Watching Eye plugin. Skipped unless these environment variables are set:
 * WE_BASE_URL (e.g. http://localhost:8096), WE_EMBY_KEY (administrator API key), WE_PLUGIN_KEY, WE_USER_ID
 * (a limited user, with the limiter and the Public API enabled on the plugin page).
 * Use a TEST server only: the test extends time, sets and clears a time-out, and resets the user's time.
 */
public class PluginIntegrationTest {

    private String baseUrl;
    private String embyKey;
    private String pluginKey;
    private String userId;
    private WatchingEyeApiService service;

    @Before
    public void setUp() {
        baseUrl = System.getenv("WE_BASE_URL");
        embyKey = System.getenv("WE_EMBY_KEY");
        pluginKey = System.getenv("WE_PLUGIN_KEY");
        userId = System.getenv("WE_USER_ID");
        assumeTrue("integration environment not set", baseUrl != null && embyKey != null && pluginKey != null && userId != null);
        service = WatchingEyeRetrofitClient.getService(baseUrl, pluginKey, embyKey);
    }

    private LimitedUserStatus find(List<LimitedUserStatus> list) {
        for (LimitedUserStatus s : list) {
            if (userId.equalsIgnoreCase(s.getUserId())) return s;
        }
        return null;
    }

    @Test
    public void statusWorksOnBothRoutesWithHeaderAuthentication() throws Exception {
        Response<List<LimitedUserStatus>> admin = service.getLimitedUsersStatus().execute();
        assertTrue("admin route: HTTP " + admin.code(), admin.isSuccessful());
        assertNotNull(find(admin.body()));
        Response<List<LimitedUserStatus>> pub = service.getStatus().execute();
        assertTrue("public route: HTTP " + pub.code(), pub.isSuccessful());
        assertNotNull(find(pub.body()));
    }

    @Test
    public void extendTimeOutClearAndResetFlow() throws Exception {
        assertEquals(204, service.extendTime(new UserMinutesRequest(userId, 5)).execute().code());
        LimitedUserStatus afterExtend = find(service.getLimitedUsersStatus().execute().body());
        assertTrue("credit should be at least 300 s", afterExtend.getCreditSecondsDaily() >= 300.0);

        assertEquals(204, service.timeOutUser(new UserMinutesRequest(userId, 2)).execute().code());
        LimitedUserStatus timedOut = find(service.getLimitedUsersStatus().execute().body());
        Instant end = TimeOutFormat.activeEnd(timedOut.getTimeOutUntil(), Instant.now());
        assertNotNull("time-out should be active", end);

        assertEquals(204, service.clearTimeOut(new UserIdRequest(userId)).execute().code());
        LimitedUserStatus cleared = find(service.getLimitedUsersStatus().execute().body());
        assertNull(TimeOutFormat.activeEnd(cleared.getTimeOutUntil(), Instant.now()));

        assertEquals(204, service.resetUserTime(new UserIdRequest(userId)).execute().code());
        assertEquals(0.0, find(service.getLimitedUsersStatus().execute().body()).getCreditSecondsDaily(), 0.001);
    }

    @Test
    public void invalidMinutesAreRefusedByThePlugin() throws Exception {
        assertEquals(400, service.extendTime(new UserMinutesRequest(userId, 43201)).execute().code());
    }

    @Test
    public void embySessionsAreReadableWithTheTokenHeader() throws Exception {
        RetrofitClient.configure(baseUrl, embyKey);
        Response<List<SessionInfo>> sessions = RetrofitClient.getInstance().getSessions("TranscodingInfo").execute();
        assertTrue("sessions: HTTP " + sessions.code(), sessions.isSuccessful());
    }
}
