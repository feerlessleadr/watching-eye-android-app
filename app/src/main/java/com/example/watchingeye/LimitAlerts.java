package com.example.watchingeye;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides when to alert: a user who was allowed to watch and is now over a limit. The first update after starting only
 * records the current state (so opening the app never fires alerts for something that happened earlier), and a user
 * whose limit was already reached stays quiet until they have been allowed again. No Android classes: unit tested.
 */
public final class LimitAlerts {

    private final Map<String, String> lastReason = new HashMap<>();

    /** Returns the users who just reached a limit. */
    public synchronized List<LimitedUserStatus> newlyBlocked(List<UserCardInfo> cards) {
        List<LimitedUserStatus> result = new java.util.ArrayList<>();
        boolean firstLook = lastReason.isEmpty();
        for (UserCardInfo card : cards) {
            LimitedUserStatus user = card.getUserStatus();
            if (user.getUserId() == null) continue;
            String now = user.isEnabled() ? user.getBlockReason() : "Allowed";
            String before = lastReason.put(user.getUserId(), now);
            if (!firstLook && "Allowed".equals(before) && "TimeLimitExceeded".equals(now)) {
                result.add(user);
            }
        }
        return result;
    }

    /** Forget everything (new server or new keys): the next update is a fresh start. */
    public synchronized void reset() {
        lastReason.clear();
    }
}
