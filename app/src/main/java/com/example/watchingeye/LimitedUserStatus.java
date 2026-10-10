package com.example.watchingeye;

import com.google.gson.annotations.SerializedName;

public class LimitedUserStatus {

    @SerializedName("UserId")
    private String userId;

    @SerializedName("Username")
    private String username;

    @SerializedName("DailyLimitMinutes")
    private int dailyLimitMinutes;

    @SerializedName("WeeklyLimitHours")
    private int weeklyLimitHours;

    @SerializedName("MonthlyLimitHours")
    private int monthlyLimitHours;

    @SerializedName("YearlyLimitHours")
    private int yearlyLimitHours;

    @SerializedName("SecondsWatchedDaily")
    private double secondsWatchedDaily;

    @SerializedName("SecondsWatchedWeekly")
    private double secondsWatchedWeekly;

    @SerializedName("SecondsWatchedMonthly")
    private double secondsWatchedMonthly;

    @SerializedName("SecondsWatchedYearly")
    private double secondsWatchedYearly;

    @SerializedName("CreditSecondsDaily")
    private double creditSecondsDaily;

    @SerializedName("CreditSecondsWeekly")
    private double creditSecondsWeekly;

    @SerializedName("CreditSecondsMonthly")
    private double creditSecondsMonthly;

    @SerializedName("CreditSecondsYearly")
    private double creditSecondsYearly;

    @SerializedName("TimeOutUntil")
    private String timeOutUntil;

    /** Today's daily limit after per-day overrides (newer plugin versions only; null from an older plugin). */
    @SerializedName("EffectiveDailyLimitMinutes")
    private Integer effectiveDailyLimitMinutes;

    /** False when the user's limit is switched off on the plugin page (newer plugin versions only). */
    @SerializedName("IsEnabled")
    private Boolean enabled;

    /** Why playback is blocked now: Allowed, TimeLimitExceeded, OutsideTimeWindow or TimedOut (plugin 1.8+; null from an older plugin). */
    @SerializedName("BlockReason")
    private String blockReason;

    /** Seconds left before the closest enabled limit is reached; -1 when no limit applies (plugin 1.8+). */
    @SerializedName("SecondsRemaining")
    private Double secondsRemaining;

    /** Daily, Weekly, Monthly or Yearly: the limit that is closest (plugin 1.8+). */
    @SerializedName("LimitingPeriod")
    private String limitingPeriod;

    /** Total seconds allowed in that period, including extra time (plugin 1.8+). */
    @SerializedName("LimitingAllowedSeconds")
    private Double limitingAllowedSeconds;

    /** What the user is playing now (plugin 1.9+). */
    @SerializedName("NowPlaying")
    private PluginNowPlaying nowPlaying;

    /** 2 or higher means the plugin reports now-playing itself, so a missing NowPlaying really means "not playing". */
    @SerializedName("ApiLevel")
    private Integer apiLevel;

    public LimitedUserStatus() {}

    public PluginNowPlaying getNowPlaying() { return nowPlaying; }

    /** True when the plugin itself says what is playing (plugin 1.9+), so Emby's session list is not needed. */
    public boolean reportsNowPlaying() {
        return apiLevel != null && apiLevel >= 2;
    }

    /** Why playback is blocked right now; "Allowed" when it is not. An older plugin does not say, so a running time-out is detected from its end time. */
    public String getBlockReason() {
        if (blockReason != null && !blockReason.isEmpty()) {
            return blockReason;
        }
        return TimeOutFormat.activeEnd(timeOutUntil, java.time.Instant.now()) != null ? "TimedOut" : "Allowed";
    }

    /** Seconds of watch time left before the closest limit, or -1 when the user has no limit. Falls back to the daily limit for an older plugin. */
    public double getRemainingSeconds() {
        if (secondsRemaining != null) {
            return secondsRemaining < 0 ? -1.0 : secondsRemaining;
        }
        long allowed = getEffectiveAllowedSecondsDaily();
        return allowed <= 0 ? -1.0 : Math.max(0.0, allowed - secondsWatchedDaily);
    }

    /** The closest limit's period in words ("daily", "weekly", ...). */
    public String getLimitingPeriodLabel() {
        if (limitingPeriod != null && !limitingPeriod.isEmpty()) {
            return limitingPeriod.toLowerCase(java.util.Locale.ROOT);
        }
        return "daily";
    }

    /** Total seconds allowed in the closest limit's period (0 when there is no limit). */
    public double getLimitingAllowedSeconds() {
        if (limitingAllowedSeconds != null) {
            return limitingAllowedSeconds;
        }
        return getEffectiveAllowedSecondsDaily();
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public int getDailyLimitMinutes() { return dailyLimitMinutes; }
    public int getWeeklyLimitHours() { return weeklyLimitHours; }
    public int getMonthlyLimitHours() { return monthlyLimitHours; }
    public int getYearlyLimitHours() { return yearlyLimitHours; }
    public double getSecondsWatchedDaily() { return secondsWatchedDaily; }
    public double getSecondsWatchedWeekly() { return secondsWatchedWeekly; }
    public double getSecondsWatchedMonthly() { return secondsWatchedMonthly; }
    public double getSecondsWatchedYearly() { return secondsWatchedYearly; }
    public double getCreditSecondsDaily() { return creditSecondsDaily; }
    public double getCreditSecondsWeekly() { return creditSecondsWeekly; }
    public double getCreditSecondsMonthly() { return creditSecondsMonthly; }
    public double getCreditSecondsYearly() { return creditSecondsYearly; }
    public String getTimeOutUntil() { return timeOutUntil; }

    /** Today's daily limit in minutes; 0 means no daily limit applies. Falls back to the plain daily limit for an older plugin. */
    public int getEffectiveDailyLimitMinutes() {
        return effectiveDailyLimitMinutes != null ? effectiveDailyLimitMinutes : dailyLimitMinutes;
    }

    /** True unless the plugin reports that this user's limit is switched off. */
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    /** Allowed daily seconds = today's limit * 60 + extra time (0 when no daily limit applies). */
    public long getEffectiveAllowedSecondsDaily() {
        int minutes = getEffectiveDailyLimitMinutes();
        if (minutes <= 0) {
            return 0L;
        }
        return (long) minutes * 60L + (long) creditSecondsDaily;
    }
}