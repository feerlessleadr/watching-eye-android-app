package com.example.watchingeye;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public final class SecurePrefs {

    public static final String PREFS_NAME = "watchingeye_secure_prefs";
    public static final String KEY_SERVER_URL = "emby_server_url";
    public static final String KEY_EMBY_API = "emby_api_key";
    public static final String KEY_WE_API = "watchingeye_api_key";

    private SecurePrefs() {}

    public static SharedPreferences get(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            SharedPreferences encrypted = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            migrateFromLegacyIfNeeded(context, encrypted);
            return encrypted;
        } catch (Exception e) {
            // Extremely rare; fall back so app still works
            return PreferenceManager.getDefaultSharedPreferences(context);
        }
    }

    /** One-time copy from old plaintext prefs, then clear them. */
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
}