package com.example.watchingeye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import retrofit2.Response;

/**
 * Proves the app works with the token of an ORDINARY (non-administrator) Emby account plus the plugin key: sign in, read the
 * status, change time, stop a stream, and receive the live push. Skipped unless these environment variables are set:
 * WE_BASE_URL, WE_PLUGIN_KEY, WE_USER_ID (a limited user), WE_LOGIN_USER and WE_LOGIN_PW (a regular, non-admin Emby user).
 * Use a TEST server and throw-away accounts only.
 */
public class LowPrivilegeIntegrationTest {

    @Test
    public void aRegularAccountTokenIsEnoughForTheWholeApp() throws Exception {
        String baseUrl = System.getenv("WE_BASE_URL");
        String pluginKey = System.getenv("WE_PLUGIN_KEY");
        String userId = System.getenv("WE_USER_ID");
        String loginUser = System.getenv("WE_LOGIN_USER");
        String loginPassword = System.getenv("WE_LOGIN_PW");
        assumeTrue("integration environment not set",
                baseUrl != null && pluginKey != null && userId != null && loginUser != null && loginPassword != null);

        EmbyLogin.Result login = EmbyLogin.signIn(Http.newBuilder().build(), baseUrl, loginUser, loginPassword);
        assertFalse("this test needs a NON-administrator account", login.administrator);
        assertTrue(login.accessToken.length() > 10);

        WatchingEyeApiService service = WatchingEyeRetrofitClient.getService(baseUrl, pluginKey, login.accessToken);
        assertEquals("admin route must refuse a regular account", 403, service.getLimitedUsersStatus().execute().code());
        Response<List<LimitedUserStatus>> status = service.getStatus().execute();
        assertTrue("public status: HTTP " + status.code(), status.isSuccessful());
        LimitedUserStatus mine = null;
        for (LimitedUserStatus s : status.body()) {
            if (userId.equalsIgnoreCase(s.getUserId())) mine = s;
        }
        assertNotNull(mine);
        assertTrue("plugin 1.9 reports now-playing itself", mine.reportsNowPlaying());

        assertEquals(204, service.extendTime(new UserMinutesRequest(userId, 5)).execute().code());
        assertEquals(204, service.reduceTime(new UserMinutesRequest(userId, 5)).execute().code());
        assertEquals(204, service.stopStream(new UserIdRequest(userId)).execute().code());
        assertEquals(204, service.timeOutUser(new UserMinutesRequest(userId, 2)).execute().code());
        assertEquals(204, service.clearTimeOut(new UserIdRequest(userId)).execute().code());

        // The live push works for a regular account too.
        final CountDownLatch gotStatus = new CountDownLatch(1);
        final AtomicReference<LimitedUserStatus> pushed = new AtomicReference<>();
        LiveConnection live = new LiveConnection(Http.newBuilder().build(), new LiveConnection.Listener() {
            @Override
            public void onStatus(List<LimitedUserStatus> users, long atMs) {
                for (LimitedUserStatus s : users) {
                    if (userId.equalsIgnoreCase(s.getUserId())) {
                        pushed.set(s);
                        gotStatus.countDown();
                    }
                }
            }

            @Override
            public void onSessions(List<SessionInfo> sessions, long atMs) { }

            @Override
            public void onState(LiveConnection.State state, String detail) { }
        });
        try {
            live.start(baseUrl, login.accessToken, pluginKey);
            assertTrue("no live status within 15 s", gotStatus.await(15, TimeUnit.SECONDS));
            assertNotNull(pushed.get());
        } finally {
            live.shutdown();
        }
    }
}
