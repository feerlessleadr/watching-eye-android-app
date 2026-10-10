package com.example.watchingeye;

import java.util.Objects;

public class UserCardInfo {
    private final LimitedUserStatus userStatus;
    private final SessionInfo sessionInfo; // may be null

    public UserCardInfo(LimitedUserStatus userStatus, SessionInfo sessionInfo) {
        this.userStatus = userStatus;
        this.sessionInfo = sessionInfo;
    }

    public LimitedUserStatus getUserStatus() { return userStatus; }
    public SessionInfo getSessionInfo() { return sessionInfo; }

    public boolean isPlaying() {
        return sessionInfo != null
                && sessionInfo.getNowPlayingItem() != null;
    }

    /**
     * True when the two cards would look identical on screen, so a refresh that changed nothing does not redraw
     * (and flicker). Compares exactly what the card shows, at the precision it is shown (whole seconds / minutes).
     */
    public static boolean sameDisplay(UserCardInfo a, UserCardInfo b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        LimitedUserStatus x = a.userStatus;
        LimitedUserStatus y = b.userStatus;
        if (!Objects.equals(x.getUsername(), y.getUsername())
                || x.isEnabled() != y.isEnabled()
                || !Objects.equals(x.getTimeOutUntil(), y.getTimeOutUntil())
                || x.getEffectiveDailyLimitMinutes() != y.getEffectiveDailyLimitMinutes()
                || (long) x.getSecondsWatchedDaily() / 60 != (long) y.getSecondsWatchedDaily() / 60
                || x.getEffectiveAllowedSecondsDaily() / 60 != y.getEffectiveAllowedSecondsDaily() / 60) {
            return false;
        }
        if (a.isPlaying() != b.isPlaying()) return false;
        if (!a.isPlaying()) return true;
        SessionInfo s = a.sessionInfo;
        SessionInfo t = b.sessionInfo;
        return Objects.equals(s.getId(), t.getId())
                && Objects.equals(s.getNowPlayingItem().getId(), t.getNowPlayingItem().getId())
                && Objects.equals(s.getNowPlayingItem().getName(), t.getNowPlayingItem().getName())
                && Objects.equals(s.getNowPlayingItem().getRunTimeTicks(), t.getNowPlayingItem().getRunTimeTicks())
                && positionSeconds(s) == positionSeconds(t)
                && Objects.equals(s.getClient(), t.getClient())
                && Objects.equals(s.getDeviceName(), t.getDeviceName());
    }

    private static long positionSeconds(SessionInfo s) {
        if (s.getPlayState() == null || s.getPlayState().getPositionTicks() == null) return 0L;
        return s.getPlayState().getPositionTicks() / 10_000_000L;
    }
}
