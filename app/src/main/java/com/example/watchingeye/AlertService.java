package com.example.watchingeye;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import java.util.List;

/**
 * Optional background alerts. While the setting is on, this foreground service keeps the live connection open so a
 * notification can appear the moment someone reaches their limit, with buttons to add time without opening the app.
 */
public class AlertService extends Service {

    static final String CHANNEL_MONITOR = "monitor";
    static final String CHANNEL_ALERTS = "alerts";
    private static final int MONITOR_ID = 1;

    private final LimitAlerts alerts = new LimitAlerts();
    private StatusRepository repository;
    private StatusRepository.Credentials held;
    private final Observer<List<UserCardInfo>> observer = this::onCards;

    /** Starts or stops the service to match the setting (and the notification permission). Call from the screen and at boot. */
    public static void syncWithSetting(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = SecurePrefs.get(app);
        boolean wanted = prefs.getBoolean(SecurePrefs.KEY_ALERTS, false) && canNotify(app);
        Intent intent = new Intent(app, AlertService.class);
        if (wanted) {
            try {
                ContextCompat.startForegroundService(app, intent);
            } catch (RuntimeException e) {
                // Android refused to start it from the background; it will start the next time the screen opens.
            }
        } else {
            app.stopService(intent);
        }
    }

    static boolean canNotify(Context context) {
        return Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel monitor = new NotificationChannel(CHANNEL_MONITOR, "Background connection", NotificationManager.IMPORTANCE_MIN);
        monitor.setDescription("Shown while the app watches for limits in the background.");
        nm.createNotificationChannel(monitor);
        NotificationChannel alert = new NotificationChannel(CHANNEL_ALERTS, "Limit reached", NotificationManager.IMPORTANCE_HIGH);
        alert.setDescription("When someone reaches their watch-time limit.");
        nm.createNotificationChannel(alert);
        repository = StatusRepository.get(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification ongoing = new NotificationCompat.Builder(this, CHANNEL_MONITOR)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Watching Eye is watching for limits")
                .setContentText("You will be alerted when someone reaches their limit.")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(openAppIntent())
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(this, MONITOR_ID, ongoing, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(MONITOR_ID, ongoing);
            }
        } catch (RuntimeException e) {
            stopSelf();
            return START_NOT_STICKY;
        }

        SharedPreferences prefs = SecurePrefs.get(this);
        String serverUrl = trim(prefs.getString(SecurePrefs.KEY_SERVER_URL, ""));
        String embyKey = trim(prefs.getString(SecurePrefs.KEY_EMBY_API, ""));
        String weKey = trim(prefs.getString(SecurePrefs.KEY_WE_API, ""));
        if (serverUrl.isEmpty() || embyKey.isEmpty() || weKey.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        StatusRepository.Credentials creds = new StatusRepository.Credentials(serverUrl, embyKey, weKey);
        if (held == null) {
            repository.acquire(creds);
            repository.getCards().observeForever(observer);
            held = creds;
        } else if (!creds.equals(held)) {
            repository.release();
            alerts.reset();
            repository.acquire(creds);
            held = creds;
        }
        return START_STICKY;
    }

    private void onCards(List<UserCardInfo> cards) {
        if (cards == null) return;
        for (LimitedUserStatus user : alerts.newlyBlocked(cards)) {
            notifyLimitReached(user);
        }
    }

    private void notifyLimitReached(LimitedUserStatus user) {
        if (!canNotify(this)) return;
        String name = user.getUsername() != null ? user.getUsername() : "A user";
        int id = (user.getUserId() != null ? user.getUserId().hashCode() : 0) & 0x7fffffff;
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(name + " reached their " + user.getLimitingPeriodLabel() + " limit")
                .setContentText("Playback was stopped. Add time to let them continue.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent());
        for (int minutes : UserCardAdapter.QUICK_MINUTES) {
            b.addAction(0, "+" + minutes + " min", ActionReceiver.addTimeIntent(this, user.getUserId(), name, minutes, id));
        }
        getSystemService(NotificationManager.class).notify(id, b.build());
    }

    private PendingIntent openAppIntent() {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    public void onDestroy() {
        if (held != null) {
            repository.getCards().removeObserver(observer);
            repository.release();
            held = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
