package com.example.watchingeye;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;

/**
 * The app asks for the phone's own screen lock (PIN, pattern, password or fingerprint) before it opens, because anyone who
 * can open it can add time or end a time-out. After a successful unlock it stays open while it is in use and for a short
 * grace period after it was last on screen, so switching away for a moment does not ask again. A new process always asks.
 */
final class AppLock {

    /** How long after leaving the app it can be reopened without asking again. */
    static final long GRACE_MS = 30_000L;

    private static long lastActiveMs = 0L; // 0 = locked

    private AppLock() {}

    static boolean isDeviceSecure(Context context) {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isDeviceSecure();
    }

    /** True when the setting is on and the phone actually has a screen lock to ask for. */
    static boolean isEnabled(Context context) {
        return SecurePrefs.get(context).getBoolean(SecurePrefs.KEY_APP_LOCK, true) && isDeviceSecure(context);
    }

    static synchronized boolean needsUnlock(Context context, long nowMs) {
        return isEnabled(context) && needsUnlockAt(lastActiveMs, nowMs);
    }

    /** The grace rule on its own (unit tested): locked when never unlocked, or when the last activity is too long ago. */
    static boolean needsUnlockAt(long lastActiveMs, long nowMs) {
        return lastActiveMs == 0L || nowMs - lastActiveMs > GRACE_MS;
    }

    static synchronized void markUnlocked(long nowMs) {
        lastActiveMs = nowMs;
    }

    /** Call whenever the app is on screen or has just left it, so the grace period counts from the last moment it was used. */
    static synchronized void markActive(long nowMs) {
        if (lastActiveMs != 0L) lastActiveMs = nowMs;
    }

    /** Locks the app right away. */
    static synchronized void lockNow() {
        lastActiveMs = 0L;
    }

    /** The system's "confirm your screen lock" screen, or null when the phone has no screen lock. */
    static Intent unlockIntent(Context context) {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null) return null;
        return km.createConfirmDeviceCredentialIntent("Watching Eye", "Unlock to continue");
    }
}
