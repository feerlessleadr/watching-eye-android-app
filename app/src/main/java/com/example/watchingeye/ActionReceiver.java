package com.example.watchingeye;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.app.NotificationCompat;

import retrofit2.Response;

/** Handles the "+15 min" style buttons on a limit-reached notification: adds the time without opening the app. */
public class ActionReceiver extends BroadcastReceiver {

    private static final String ACTION_ADD = "com.example.watchingeye.ADD_TIME";
    private static final String EXTRA_USER_ID = "user_id";
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_MINUTES = "minutes";
    private static final String EXTRA_NOTIFICATION_ID = "notification_id";

    static PendingIntent addTimeIntent(Context context, String userId, String name, int minutes, int notificationId) {
        Intent intent = new Intent(context, ActionReceiver.class)
                .setAction(ACTION_ADD)
                .putExtra(EXTRA_USER_ID, userId)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_MINUTES, minutes)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        // One request code per user and amount, so the three buttons never replace each other.
        int requestCode = (notificationId * 31 + minutes) & 0x7fffffff;
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_ADD.equals(intent.getAction())) return;
        final String userId = intent.getStringExtra(EXTRA_USER_ID);
        final String name = intent.getStringExtra(EXTRA_NAME);
        final int minutes = intent.getIntExtra(EXTRA_MINUTES, 0);
        final int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
        if (userId == null || minutes < 1 || minutes > ErrorText.MAX_MINUTES) return;
        // Defence in depth: never add time while the phone is locked, whatever the notification showed.
        android.app.KeyguardManager keyguard = (android.app.KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguard != null && keyguard.isDeviceLocked()) return;

        final Context app = context.getApplicationContext();
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                // The alert service is running in this process and already holds the settings; fall back to the saved ones.
                StatusRepository.Credentials held = StatusRepository.get(app).getCredentials();
                String serverUrl;
                String embyKey;
                String weKey;
                if (held != null) {
                    serverUrl = held.serverUrl;
                    embyKey = held.embyKey;
                    weKey = held.pluginKey;
                } else {
                    SharedPreferences prefs = SecurePrefs.get(app);
                    serverUrl = prefs.getString(SecurePrefs.KEY_SERVER_URL, "");
                    embyKey = prefs.getString(SecurePrefs.KEY_EMBY_API, "");
                    weKey = prefs.getString(SecurePrefs.KEY_WE_API, "");
                }
                String result;
                boolean ok = false;
                try {
                    if (serverUrl == null || serverUrl.trim().isEmpty()) {
                        throw new IllegalStateException("No server address is saved in the app settings.");
                    }
                    Response<Void> r = WatchingEyeRetrofitClient.getService(serverUrl.trim(), weKey.trim(), embyKey.trim())
                            .extendTime(new UserMinutesRequest(userId, minutes)).execute();
                    ok = r.isSuccessful();
                    result = ok ? "Added " + minutes + " min for " + name : ErrorText.describeFailure("Add time", r.code());
                } catch (Exception e) {
                    result = e instanceof IllegalStateException ? e.getMessage() : "Add time failed. " + ErrorText.describeConnectionFailure(e);
                }
                NotificationManager nm = app.getSystemService(NotificationManager.class);
                if (ok) {
                    nm.cancel(notificationId);
                } else if (AlertService.canNotify(app)) {
                    nm.notify(notificationId, new NotificationCompat.Builder(app, AlertService.CHANNEL_ALERTS)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle(name + ": could not add time")
                            .setContentText(result)
                            .setStyle(new NotificationCompat.BigTextStyle().bigText(result))
                            .setAutoCancel(true)
                            .build());
                }
            } finally {
                pending.finish();
            }
        }).start();
    }
}
