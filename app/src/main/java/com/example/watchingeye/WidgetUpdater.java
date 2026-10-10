package com.example.watchingeye;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import java.text.DateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Response;

/** Draws the home-screen widget from the latest data. Does nothing when no widget has been placed. */
final class WidgetUpdater {

    private static final int[] ROWS = {R.id.row1, R.id.row2, R.id.row3, R.id.row4};
    private static String lastSignature = "";

    private WidgetUpdater() {}

    private static int[] widgetIds(Context context, AppWidgetManager manager) {
        return manager.getAppWidgetIds(new ComponentName(context, WatchingEyeWidget.class));
    }

    /** Called whenever fresh data arrives while the app or the alert service is running. */
    static void updateFromCards(Context context, List<UserCardInfo> cards) {
        List<LimitedUserStatus> users = new ArrayList<>();
        for (UserCardInfo card : cards) users.add(card.getUserStatus());
        render(context, users, "Live");
    }

    /** One quick request to the server, for when nothing else is keeping the data fresh (the widget's own 30-minute refresh). */
    static void refreshOnce(Context context) {
        SharedPreferences prefs = SecurePrefs.get(context);
        String url = trim(prefs.getString(SecurePrefs.KEY_SERVER_URL, ""));
        String embyKey = trim(prefs.getString(SecurePrefs.KEY_EMBY_API, ""));
        String weKey = trim(prefs.getString(SecurePrefs.KEY_WE_API, ""));
        if (url.isEmpty() || embyKey.isEmpty() || weKey.isEmpty()) {
            renderMessage(context, "Open the app and enter your server settings.");
            return;
        }
        try {
            WatchingEyeApiService api = WatchingEyeRetrofitClient.getService(url, weKey, embyKey);
            Response<List<LimitedUserStatus>> r = api.getLimitedUsersStatus().execute();
            if (!r.isSuccessful() || r.body() == null) {
                r = api.getStatus().execute();
            }
            if (r.isSuccessful() && r.body() != null) {
                render(context, r.body(), null);
            } else {
                renderMessage(context, ErrorText.describeFailure("Loading status", r.code()));
            }
        } catch (Exception e) {
            renderMessage(context, ErrorText.describeConnectionFailure(e));
        }
    }

    private static void render(Context context, List<LimitedUserStatus> users, String liveLabel) {
        List<String> lines = new ArrayList<>();
        Instant now = Instant.now();
        for (LimitedUserStatus user : users) {
            lines.add(WidgetText.row(user, now, ZoneId.systemDefault(), Locale.getDefault()));
        }
        if (lines.isEmpty()) {
            lines.add("No limited users yet.");
        }
        String footer = (liveLabel != null ? liveLabel + " · " : "Updated ")
                + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
        draw(context, lines, footer);
    }

    private static void renderMessage(Context context, String message) {
        List<String> lines = new ArrayList<>();
        lines.add(message);
        draw(context, lines, "Tap to open the app");
    }

    private static synchronized void draw(Context context, List<String> lines, String footer) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = widgetIds(context, manager);
        if (ids.length == 0) return;
        // The footer carries a clock; only redraw when the lines changed, so a stream of identical pushes costs nothing.
        String signature = String.join("\n", lines);
        if (signature.equals(lastSignature) && footer.startsWith("Live")) return;
        lastSignature = signature;

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_status);
        for (int i = 0; i < ROWS.length; i++) {
            if (i < lines.size()) {
                views.setViewVisibility(ROWS[i], android.view.View.VISIBLE);
                views.setTextViewText(ROWS[i], lines.get(i));
            } else {
                views.setViewVisibility(ROWS[i], android.view.View.GONE);
            }
        }
        if (lines.size() > ROWS.length) {
            views.setTextViewText(R.id.widgetFooter, "+" + (lines.size() - ROWS.length) + " more · " + footer);
        } else {
            views.setTextViewText(R.id.widgetFooter, footer);
        }
        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        views.setOnClickPendingIntent(R.id.widgetRoot,
                PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        manager.updateAppWidget(ids, views);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
