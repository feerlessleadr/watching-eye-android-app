package com.example.watchingeye;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.MaterialColors;

/**
 * Base class for the app's screens: while the app is locked it shows an opaque cover (nothing underneath is visible or
 * touchable), asks for the phone's screen lock, and only calls {@link #onUnlocked()} once that succeeded. With the lock on,
 * the window is also hidden from screenshots and the recent-apps view.
 */
public abstract class LockedActivity extends AppCompatActivity {

    private View cover;
    private boolean unlockInFlight = false;
    private boolean started = false;

    private final ActivityResultLauncher<Intent> unlockLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                unlockInFlight = false;
                if (result.getResultCode() == Activity.RESULT_OK) {
                    AppLock.markUnlocked(System.currentTimeMillis());
                    hideCover();
                    if (started) onUnlocked();
                } else {
                    showCover(true);
                }
            });

    /** Called when the screen is visible and unlocked (immediately when there is no lock). Start network work here. */
    protected abstract void onUnlocked();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (AppLock.isEnabled(this)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        long now = System.currentTimeMillis();
        if (AppLock.needsUnlock(this, now)) {
            showCover(false);
            if (!unlockInFlight) requestUnlock();
        } else {
            AppLock.markActive(now);
            hideCover();
            onUnlocked();
        }
    }

    @Override
    protected void onStop() {
        started = false;
        AppLock.markActive(System.currentTimeMillis());
        super.onStop();
    }

    private void requestUnlock() {
        Intent intent = AppLock.unlockIntent(this);
        if (intent == null) {
            // The phone lost its screen lock meanwhile: there is nothing to ask for.
            AppLock.markUnlocked(System.currentTimeMillis());
            hideCover();
            onUnlocked();
            return;
        }
        unlockInFlight = true;
        try {
            unlockLauncher.launch(intent);
        } catch (RuntimeException e) {
            unlockInFlight = false;
            showCover(true);
        }
    }

    private void showCover(boolean withButtons) {
        if (cover == null) {
            FrameLayout frame = new FrameLayout(this);
            frame.setClickable(true);
            frame.setFocusable(true);
            frame.setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0xFF000000));
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView title = new TextView(this);
            title.setText("Watching Eye is locked");
            title.setTextSize(20f);
            title.setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF));
            title.setGravity(Gravity.CENTER);
            box.addView(title);
            Button unlock = new Button(this);
            unlock.setText("Unlock");
            unlock.setTag("unlock");
            unlock.setOnClickListener(v -> {
                if (!unlockInFlight) requestUnlock();
            });
            Button close = new Button(this);
            close.setText("Close");
            close.setTag("close");
            close.setOnClickListener(v -> finish());
            box.addView(unlock);
            box.addView(close);
            FrameLayout.LayoutParams boxParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            frame.addView(box, boxParams);
            cover = frame;
            ((ViewGroup) getWindow().getDecorView()).addView(cover,
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        cover.setVisibility(View.VISIBLE);
        cover.bringToFront();
        // Also take the real screen out of the view tree, so screen readers and other accessibility tools cannot read it either.
        View content = findViewById(android.R.id.content);
        if (content != null) content.setVisibility(View.INVISIBLE);
        View unlock = cover.findViewWithTag("unlock");
        View close = cover.findViewWithTag("close");
        if (unlock != null) unlock.setVisibility(withButtons ? View.VISIBLE : View.GONE);
        if (close != null) close.setVisibility(withButtons ? View.VISIBLE : View.GONE);
    }

    private void hideCover() {
        if (cover != null) cover.setVisibility(View.GONE);
        View content = findViewById(android.R.id.content);
        if (content != null) content.setVisibility(View.VISIBLE);
    }
}
