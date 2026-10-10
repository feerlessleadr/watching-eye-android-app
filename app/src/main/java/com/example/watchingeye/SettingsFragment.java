package com.example.watchingeye;

import android.Manifest;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

public class SettingsFragment extends PreferenceFragmentCompat {

    private SwitchPreferenceCompat alertsSwitch;

    /** Android 13+ asks before an app may show notifications; the alerts setting only turns on once that is granted. */
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (alertsSwitch == null) return;
                if (granted) {
                    alertsSwitch.setChecked(true);
                } else {
                    alertsSwitch.setChecked(false);
                    Toast.makeText(requireContext(), "Notifications are blocked for this app, so alerts cannot be turned on.", Toast.LENGTH_LONG).show();
                }
            });

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

            @Override
            public void putBoolean(String key, boolean value) {
                prefs.edit().putBoolean(key, value).apply();
                if (SecurePrefs.KEY_ALERTS.equals(key)) {
                    AlertService.syncWithSetting(requireContext());
                }
            }

            @Override
            public boolean getBoolean(String key, boolean defValue) {
                return prefs.getBoolean(key, defValue);
            }
        };

        getPreferenceManager().setPreferenceDataStore(store);
        setPreferencesFromResource(R.xml.preferences, rootKey);

        // Mask API keys in the summary list
        maskSummary("emby_api_key");
        maskSummary("watchingeye_api_key");

        alertsSwitch = findPreference(SecurePrefs.KEY_ALERTS);
        if (alertsSwitch != null) {
            alertsSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean turningOn = Boolean.TRUE.equals(newValue);
                if (turningOn && Build.VERSION.SDK_INT >= 33 && !AlertService.canNotify(requireContext())) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
                    return false; // turned on by the permission result
                }
                return true;
            });
        }
    }

    private void maskSummary(String key) {
        Preference p = findPreference(key);
        if (p instanceof EditTextPreference) {
            p.setSummaryProvider(preference -> {
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
