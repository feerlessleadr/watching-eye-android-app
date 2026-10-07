package com.example.watchingeye;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        PreferenceDataStore store = new PreferenceDataStore() {
            private final SharedPreferences prefs =
                    SecurePrefs.get(requireContext().getApplicationContext());

            @Override
            public void putString(String key, @Nullable String value) {
                prefs.edit().putString(key, value != null ? value : "").apply();
            }

            @Override
            public String getString(String key, @Nullable String defValue) {
                return prefs.getString(key, defValue);
            }
        };

        getPreferenceManager().setPreferenceDataStore(store);
        setPreferencesFromResource(R.xml.preferences, rootKey);

        // Mask API keys in the summary list
        maskSummary("emby_api_key");
        maskSummary("watchingeye_api_key");
    }

    private void maskSummary(String key) {
        Preference p = findPreference(key);
        if (p instanceof EditTextPreference) {
            p.setSummaryProvider(preference -> {
                String v = preference.getSharedPreferences() != null
                        ? null
                        : null;
                // Read via data store
                String value = getPreferenceManager().getPreferenceDataStore() != null
                        ? getPreferenceManager().getPreferenceDataStore().getString(key, "")
                        : "";
                if (value == null || value.isEmpty()) {
                    return "Not set";
                }
                return "API Key is set";
            });
        }
    }
}