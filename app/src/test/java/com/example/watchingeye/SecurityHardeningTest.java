package com.example.watchingeye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.Gson;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

import org.junit.Test;

public class SecurityHardeningTest {

    // ---- screen lock grace rule ----------------------------------------------------------------------------

    @Test
    public void theAppIsLockedUntilItWasUnlockedAndAfterTheGracePeriod() {
        long now = 1_000_000L;
        assertTrue("never unlocked", AppLock.needsUnlockAt(0L, now));
        assertFalse("just used", AppLock.needsUnlockAt(now - 5_000L, now));
        assertFalse("right at the limit", AppLock.needsUnlockAt(now - AppLock.GRACE_MS, now));
        assertTrue("away for too long", AppLock.needsUnlockAt(now - AppLock.GRACE_MS - 1L, now));
    }

    // ---- sign-in with a regular Emby account -----------------------------------------------------------------

    @Test
    public void readsASignInAnswerAndFlagsAdministrators() {
        EmbyLogin.Result user = EmbyLogin.parse("{\"AccessToken\":\"tok123\",\"User\":{\"Name\":\"Watcher\",\"Policy\":{\"IsAdministrator\":false}}}");
        assertEquals("tok123", user.accessToken);
        assertEquals("Watcher", user.userName);
        assertFalse(user.administrator);
        EmbyLogin.Result admin = EmbyLogin.parse("{\"AccessToken\":\"t\",\"User\":{\"Name\":\"Boss\",\"Policy\":{\"IsAdministrator\":true}}}");
        assertTrue(admin.administrator);
        assertFalse(EmbyLogin.parse("{\"AccessToken\":\"t\"}").administrator);
    }

    @Test
    public void rejectsAnswersWithoutAToken() {
        for (String bad : new String[]{"{}", "{\"AccessToken\":\"\"}", "{\"AccessToken\":null}", "[1]"}) {
            try {
                EmbyLogin.parse(bad);
                fail("accepted: " + bad);
            } catch (IllegalArgumentException expected) {
                // fine
            }
        }
    }

    @Test
    public void theSignInRequestEscapesAwkwardPasswords() {
        String body = EmbyLogin.requestBody("a\"b", "pw\\\"{}");
        com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
        assertEquals("a\"b", o.get("Username").getAsString());
        assertEquals("pw\\\"{}", o.get("Pw").getAsString());
        assertTrue(EmbyLogin.describeFailure(401).contains("did not accept"));
        assertTrue(EmbyLogin.describeFailure(500).contains("HTTP 500"));
    }

    // ---- storage never falls back to plain text -----------------------------------------------------------------

    @Test
    public void memoryOnlySettingsBehaveLikePreferencesAndAreNeverPersisted() {
        SecurePrefs.MemoryPrefs prefs = new SecurePrefs.MemoryPrefs();
        assertEquals("fallback", prefs.getString("k", "fallback"));
        prefs.edit().putString("k", "v").putBoolean("b", true).putInt("i", 3).apply();
        assertEquals("v", prefs.getString("k", null));
        assertTrue(prefs.getBoolean("b", false));
        assertEquals(3, prefs.getInt("i", 0));
        assertTrue(prefs.contains("k"));
        prefs.edit().remove("k").apply();
        assertFalse(prefs.contains("k"));
        prefs.edit().clear().apply();
        assertTrue(prefs.getAll().isEmpty());
    }

    // ---- what the plugin says is playing -----------------------------------------------------------------------

    private static LimitedUserStatus row(String extra) {
        return new Gson().fromJson("{\"UserId\":\"u1\",\"Username\":\"Kid\",\"BlockReason\":\"Allowed\",\"TimeOutUntil\":\"0001-01-01T00:00:00Z\"" + extra + "}", LimitedUserStatus.class);
    }

    @Test
    public void usesWhatAPlugin19RowSaysIsPlaying() {
        LimitedUserStatus u = row(",\"ApiLevel\":2,\"NowPlaying\":{\"SessionId\":\"s9\",\"ItemId\":\"77\",\"ItemName\":\"Cartoon\",\"ItemType\":\"Episode\","
                + "\"RunTimeTicks\":18000000000,\"PositionTicks\":6000000000,\"IsPaused\":true,\"Client\":\"Emby for TV\",\"DeviceName\":\"Living room\"}");
        assertTrue(u.reportsNowPlaying());
        assertNotNull(u.getNowPlaying());
        SessionInfo s = SessionInfo.fromPlugin(u.getUserId(), u.getUsername(), u.getNowPlaying());
        assertEquals("s9", s.getId());
        assertEquals("Cartoon", s.getNowPlayingItem().getName());
        assertEquals(Long.valueOf(18000000000L), s.getNowPlayingItem().getRunTimeTicks());
        assertEquals(Long.valueOf(6000000000L), s.getPlayState().getPositionTicks());
        assertTrue(s.getPlayState().isPaused());
        UserCardInfo card = new UserCardInfo(u, s, 1000L, 1000L);
        assertTrue(card.isPlaying());
        assertTrue(card.isPaused());
    }

    @Test
    public void aMissingNowPlayingMeansNothingIsPlayingOnlyForANewPlugin() {
        assertTrue(row(",\"ApiLevel\":2").reportsNowPlaying());
        assertNull(row(",\"ApiLevel\":2").getNowPlaying());
        assertFalse("an older plugin does not say", row("").reportsNowPlaying());
    }

    @Test
    public void thePushCarriesNowPlayingRows() {
        String json = "{\"MessageType\":\"WatchingEyeStatus\",\"Data\":{\"Version\":3,\"Users\":[{\"UserId\":\"u1\",\"Username\":\"Kid\",\"ApiLevel\":2,"
                + "\"NowPlaying\":{\"SessionId\":\"s1\",\"ItemName\":\"Movie\",\"PositionTicks\":5}}]}}";
        LiveMessages.Message m = LiveMessages.parse(json);
        assertEquals(1, m.users.size());
        assertEquals("Movie", m.users.get(0).getNowPlaying().getItemName());
    }

    // ---- widget privacy -------------------------------------------------------------------------------------------

    @Test
    public void theWidgetCanHideNames() {
        LimitedUserStatus u = row(",\"SecondsRemaining\":600,\"ApiLevel\":2");
        Instant now = Instant.parse("2026-10-10T12:00:00Z");
        assertEquals("Kid — 10 min left", WidgetText.row(u, now, ZoneId.of("UTC"), Locale.US));
        assertEquals("User 2 — 10 min left", WidgetText.row(u, now, ZoneId.of("UTC"), Locale.US, "User 2"));
    }

    // ---- a typo in the server address must not crash the app ---------------------------------------------------

    @Test
    public void onlyRealServerAddressesAreAccepted() {
        assertTrue(ServerUrl.isValid("https://emby.example.com"));
        assertTrue(ServerUrl.isValid("https://emby.example.com/"));
        assertTrue(ServerUrl.isValid("http://10.0.2.2:8096"));
        assertTrue(ServerUrl.isValid("  https://emby.example.com:8920/base  "));
        assertFalse("the typo that crashed the app", ServerUrl.isValid("http://10.0.2.2:8096http:"));
        assertFalse(ServerUrl.isValid("emby.example.com"));
        assertFalse(ServerUrl.isValid("ftp://emby.example.com"));
        assertFalse(ServerUrl.isValid("https://"));
        assertFalse(ServerUrl.isValid(""));
        assertFalse(ServerUrl.isValid(null));
    }
}
