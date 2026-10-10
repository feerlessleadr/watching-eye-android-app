package com.example.watchingeye;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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

        SwitchPreferenceCompat lockSwitch = findPreference(SecurePrefs.KEY_APP_LOCK);
        if (lockSwitch != null && !AppLock.isDeviceSecure(requireContext())) {
            lockSwitch.setEnabled(false);
            lockSwitch.setSummary("Your phone has no screen lock, so there is nothing to ask for. Set a PIN, pattern or fingerprint in Android's settings and anyone who picks up this phone will no longer be able to use the app.");
        }

        Preference signIn = findPreference("emby_sign_in");
        if (signIn != null) {
            signIn.setOnPreferenceClickListener(p -> {
                showSignInDialog();
                return true;
            });
        }

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

    /** Asks for an Emby user name and password, signs in once, and keeps only the resulting token. */
    private void showSignInDialog() {
        final Context context = requireContext();
        final SharedPreferences prefs = SecurePrefs.get(context.getApplicationContext());
        final String serverUrl = prefs.getString(SecurePrefs.KEY_SERVER_URL, "");
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            Toast.makeText(context, "Enter the Emby server address first.", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText user = new EditText(context);
        user.setHint("Emby user name");
        user.setInputType(InputType.TYPE_CLASS_TEXT);
        final EditText password = new EditText(context);
        password.setHint("Password (used once, not stored)");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(user);
        box.addView(password);
        new MaterialAlertDialogBuilder(context)
                .setTitle("Sign in with a regular Emby account")
                .setMessage("Use an ordinary (non-administrator) user, for example one you create just for this app.")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Sign in", (d, w) -> {
                    final String name = user.getText().toString().trim();
                    final String pw = password.getText().toString();
                    password.getText().clear();
                    if (name.isEmpty() || pw.isEmpty()) {
                        Toast.makeText(context, "Enter both a user name and a password.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    final Handler main = new Handler(Looper.getMainLooper());
                    new Thread(() -> {
                        try {
                            EmbyLogin.Result result = EmbyLogin.signIn(Http.newBuilder().build(), serverUrl.trim(), name, pw);
                            main.post(() -> onSignedIn(context, prefs, result));
                        } catch (Exception e) {
                            String message = e.getMessage() != null ? e.getMessage() : ErrorText.describeConnectionFailure(e);
                            main.post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
                        }
                    }).start();
                })
                .show();
    }

    private void onSignedIn(Context context, SharedPreferences prefs, EmbyLogin.Result result) {
        if (!isAdded()) return;
        if (result.administrator) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(result.userName + " is an administrator")
                    .setMessage("The token of an administrator account has full control of your Emby server. "
                            + "Create an ordinary user for this app instead, or use this one anyway.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Use anyway", (d, w) -> saveToken(context, prefs, result))
                    .show();
        } else {
            saveToken(context, prefs, result);
        }
    }

    private void saveToken(Context context, SharedPreferences prefs, EmbyLogin.Result result) {
        prefs.edit().putString(SecurePrefs.KEY_EMBY_API, result.accessToken).apply();
        maskSummary(SecurePrefs.KEY_EMBY_API); // refresh the "key is set" text
        Toast.makeText(context, "Signed in as " + result.userName + ". The password was not stored.", Toast.LENGTH_LONG).show();
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
