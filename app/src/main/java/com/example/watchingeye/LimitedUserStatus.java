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

    /** WebUI-style allowed daily seconds = limit minutes * 60 + credit */
    public long getEffectiveAllowedSecondsDaily() {
        return (long) dailyLimitMinutes * 60L + (long) creditSecondsDaily;
    }
}