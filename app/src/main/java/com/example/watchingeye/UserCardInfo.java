package com.example.watchingeye;

import java.util.Objects;

/**
 * One user's card: the plugin's status row, the user's running Emby session (if any) and when each was received,
 * so the screen can keep the countdown and the playback position moving between updates without asking the server.
 */
public class UserCardInfo {
    private final LimitedUserStatus userStatus;
    private final SessionInfo sessionInfo; // may be null
    private final long statusAtMs;
    private final long sessionAtMs;

    public UserCardInfo(LimitedUserStatus userStatus, SessionInfo sessionInfo) {
        this(userStatus, sessionInfo, System.currentTimeMillis(), System.currentTimeMillis());
    }

    public UserCardInfo(LimitedUserStatus userStatus, SessionInfo sessionInfo, long statusAtMs, long sessionAtMs) {
        this.userStatus = userStatus;
        this.sessionInfo = sessionInfo;
        this.statusAtMs = statusAtMs;
        this.sessionAtMs = sessionAtMs;
    }

    public LimitedUserStatus getUserStatus() { return userStatus; }
    public SessionInfo getSessionInfo() { return sessionInfo; }

    public boolean isPlaying() {
        return sessionInfo != null
                && sessionInfo.getNowPlayingItem() != null;
    }

    public boolean isPaused() {
        return isPlaying() && sessionInfo.getPlayState() != null && sessionInfo.getPlayState().isPaused();
    }

    /** True while the plugin is counting this user's time: limited, not blocked, and actually playing (not paused). */
    public boolean isCounting() {
        return isPlaying() && !isPaused() && userStatus.isEnabled() && "Allowed".equals(userStatus.getBlockReason());
    }

    /** Seconds left before the closest limit right now (the last report minus the time that has passed while counting); -1 when unlimited. */
    public double remainingSecondsNow(long nowMs) {
        double base = userStatus.getRemainingSeconds();
        if (base < 0) return -1.0;
        if (isCounting()) {
            base -= Math.max(0L, nowMs - statusAtMs) / 1000.0;
        }
        return Math.max(0.0, base);
    }

    /** Playback position in ticks right now (the last report plus the time that has passed while playing). */
    public long positionTicksNow(long nowMs) {
        long base = positionTicks(sessionInfo);
        if (isPlaying() && !isPaused()) {
            base += Math.max(0L, nowMs - sessionAtMs) * 10_000L;
        }
        return base;
    }

    /**
     * True when the two cards would look identical on screen, so a refresh that changed nothing does not redraw
     * (and flicker). Compares what the card shows, at the precision it is shown. The ticking numbers (countdown and
     * playback position) are not compared: they are advanced locally between updates.
     */
    public static boolean sameDisplay(UserCardInfo a, UserCardInfo b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        LimitedUserStatus x = a.userStatus;
        LimitedUserStatus y = b.userStatus;
        if (!Objects.equals(x.getUsername(), y.getUsername())
                || x.isEnabled() != y.isEnabled()
                || !Objects.equals(x.getTimeOutUntil(), y.getTimeOutUntil())
                || !Objects.equals(x.getBlockReason(), y.getBlockReason())
                || !Objects.equals(x.getLimitingPeriodLabel(), y.getLimitingPeriodLabel())
                || x.getEffectiveDailyLimitMinutes() != y.getEffectiveDailyLimitMinutes()
                || (long) x.getLimitingAllowedSeconds() / 60 != (long) y.getLimitingAllowedSeconds() / 60) {
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
                && a.isPaused() == b.isPaused()
                && Objects.equals(s.getClient(), t.getClient())
                && Objects.equals(s.getDeviceName(), t.getDeviceName());
    }

    private static long positionTicks(SessionInfo s) {
        if (s == null || s.getPlayState() == null || s.getPlayState().getPositionTicks() == null) return 0L;
        return s.getPlayState().getPositionTicks();
    }
}
