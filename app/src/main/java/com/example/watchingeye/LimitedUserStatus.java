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

    public LimitedUserStatus() {}

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