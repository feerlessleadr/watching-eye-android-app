package com.example.watchingeye;

import android.os.Bundle;

/** The settings screen. Behind the same screen-lock check as the main screen, because it shows and changes the keys. */
public class SettingsActivity extends LockedActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new SettingsFragment())
                    .commit();
        }
    }

    @Override
    protected void onUnlocked() {
        // Nothing to start: the settings screen only shows and edits saved values.
    }
}
