package com.example.watchingeye;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

import java.util.List;

/**
 * The home-screen widget: each limited user and how much time they have left. It follows live data while the app or the
 * alert service is running, and otherwise refreshes itself every 30 minutes (Android's minimum for widgets).
 */
public class WatchingEyeWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        final Context app = context.getApplicationContext();
        List<UserCardInfo> cards = StatusRepository.get(app).getCards().getValue();
        if (cards != null && !cards.isEmpty()) {
            WidgetUpdater.updateFromCards(app, cards);
            return;
        }
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                WidgetUpdater.refreshOnce(app);
            } finally {
                pending.finish();
            }
        }).start();
    }
}
