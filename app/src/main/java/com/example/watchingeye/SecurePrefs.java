package com.example.watchingeye;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The app's settings (server address and the two keys), stored encrypted with a key held in the Android Keystore.
 *
 * If the encrypted store cannot be opened (a damaged keystore after a phone restore, for example) it is reset and the
 * settings have to be entered again. The app never falls back to plain-text storage: if even a fresh encrypted store cannot
 * be created, settings are kept in memory only for this run and {@link #isUnavailable()} reports it.
 */
public final class SecurePrefs {

    public static final String PREFS_NAME = "watchingeye_secure_prefs";
    public static final String KEY_SERVER_URL = "emby_server_url";
    public static final String KEY_EMBY_API = "emby_api_key";
    public static final String KEY_WE_API = "watchingeye_api_key";
    /** Optional background alerts (a small always-on connection plus notifications). Off by default. */
    public static final String KEY_ALERTS = "alerts_enabled";
    /** Ask for the phone's screen lock before the app (and its settings) can be opened. On by default. */
    public static final String KEY_APP_LOCK = "app_lock_enabled";
    /** Show "User 1", "User 2" instead of names on the home-screen widget. Off by default. */
    public static final String KEY_WIDGET_HIDE_NAMES = "widget_hide_names";

    private static SharedPreferences cached;
    private static boolean unavailable = false;
    private static boolean wasReset = false;

    private SecurePrefs() {}

    public static synchronized SharedPreferences get(Context context) {
        if (cached != null) return cached;
        Context app = context.getApplicationContext();
        SharedPreferences encrypted = open(app);
        if (encrypted == null) {
            // The store is unreadable: delete it (the keys in it are lost) and try once more with a fresh one.
            app.deleteSharedPreferences(PREFS_NAME);
            wasReset = true;
            encrypted = open(app);
        }
        if (encrypted == null) {
            unavailable = true;
            cached = new MemoryPrefs();
            return cached;
        }
        migrateFromLegacyIfNeeded(app, encrypted);
        cached = encrypted;
        return cached;
    }

    private static SharedPreferences open(Context app) {
        try {
            MasterKey masterKey = new MasterKey.Builder(app)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    app,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            return null;
        }
    }

    /** True when settings can only be kept in memory (nothing is written to disk in plain text). */
    public static synchronized boolean isUnavailable() {
        return unavailable;
    }

    /** True once if the stored settings had to be discarded because they could not be decrypted. */
    public static synchronized boolean takeWasReset() {
        boolean was = wasReset;
        wasReset = false;
        return was;
    }

    /** One-time copy from old plaintext prefs (very old app versions), then clear them. */
    private static void migrateFromLegacyIfNeeded(Context context, SharedPreferences encrypted) {
        if (encrypted.contains(KEY_SERVER_URL) || encrypted.contains(KEY_EMBY_API)) {
            return;
        }
        SharedPreferences legacy = PreferenceManager.getDefaultSharedPreferences(context);
        String url = legacy.getString(KEY_SERVER_URL, null);
        String emby = legacy.getString(KEY_EMBY_API, null);
        String we = legacy.getString(KEY_WE_API, null);
        if (url == null && emby == null && we == null) {
            return;
        }
        encrypted.edit()
                .putString(KEY_SERVER_URL, url != null ? url : "")
                .putString(KEY_EMBY_API, emby != null ? emby : "")
                .putString(KEY_WE_API, we != null ? we : "")
                .apply();
        legacy.edit().clear().apply();
    }

    /** Settings held in memory only: used when no encrypted store can be created. Never written anywhere. */
    static final class MemoryPrefs implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();

        @Override public synchronized Map<String, ?> getAll() { return new HashMap<>(values); }
        @Override public synchronized String getString(String key, String defValue) { Object v = values.get(key); return v instanceof String ? (String) v : defValue; }
        @Override @SuppressWarnings("unchecked")
        public synchronized Set<String> getStringSet(String key, Set<String> defValues) { Object v = values.get(key); return v instanceof Set ? (Set<String>) v : defValues; }
        @Override public synchronized int getInt(String key, int defValue) { Object v = values.get(key); return v instanceof Integer ? (Integer) v : defValue; }
        @Override public synchronized long getLong(String key, long defValue) { Object v = values.get(key); return v instanceof Long ? (Long) v : defValue; }
        @Override public synchronized float getFloat(String key, float defValue) { Object v = values.get(key); return v instanceof Float ? (Float) v : defValue; }
        @Override public synchronized boolean getBoolean(String key, boolean defValue) { Object v = values.get(key); return v instanceof Boolean ? (Boolean) v : defValue; }
        @Override public synchronized boolean contains(String key) { return values.containsKey(key); }
        @Override public Editor edit() { return new MemoryEditor(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }

        private final class MemoryEditor implements Editor {
            private final Map<String, Object> pending = new HashMap<>();
            private final Set<String> removed = new HashSet<>();
            private boolean clear = false;

            @Override public Editor putString(String key, String value) { pending.put(key, value); return this; }
            @Override public Editor putStringSet(String key, Set<String> value) { pending.put(key, value); return this; }
            @Override public Editor putInt(String key, int value) { pending.put(key, value); return this; }
            @Override public Editor putLong(String key, long value) { pending.put(key, value); return this; }
            @Override public Editor putFloat(String key, float value) { pending.put(key, value); return this; }
            @Override public Editor putBoolean(String key, boolean value) { pending.put(key, value); return this; }
            @Override public Editor remove(String key) { removed.add(key); return this; }
            @Override public Editor clear() { clear = true; return this; }
            @Override public boolean commit() { apply(); return true; }
            @Override public void apply() {
                synchronized (MemoryPrefs.this) {
                    if (clear) values.clear();
                    for (String key : removed) values.remove(key);
                    for (Map.Entry<String, Object> e : pending.entrySet()) {
                        if (e.getValue() == null) values.remove(e.getKey()); else values.put(e.getKey(), e.getValue());
                    }
                }
            }
        }
    }
}
