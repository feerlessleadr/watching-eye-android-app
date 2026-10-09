package com.example.watchingeye;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

import org.junit.Test;

public class LimitedUserStatusTest {

    private static final Type LIST = new TypeToken<List<LimitedUserStatus>>() {}.getType();

    /** Response of the plugin before the new fields were added. */
    private static final String OLD_PLUGIN = "[{\"UserId\":\"abc\",\"Username\":\"O'Brien & Co\",\"DailyLimitMinutes\":60,"
            + "\"WeeklyLimitHours\":20,\"MonthlyLimitHours\":80,\"YearlyLimitHours\":0,\"SecondsWatchedDaily\":1800.5,"
            + "\"SecondsWatchedWeekly\":1800.5,\"SecondsWatchedMonthly\":1800.5,\"SecondsWatchedYearly\":1800.5,"
            + "\"CreditSecondsDaily\":900,\"CreditSecondsWeekly\":900,\"CreditSecondsMonthly\":900,\"CreditSecondsYearly\":900,"
            + "\"TimeOutUntil\":\"0001-01-01T00:00:00.0000000Z\"}]";

    private static LimitedUserStatus parse(String json) {
        List<LimitedUserStatus> list = new Gson().fromJson(json, LIST);
        return list.get(0);
    }

    @Test
    public void oldPluginResponseStillWorks() {
        LimitedUserStatus user = parse(OLD_PLUGIN);
        assertEquals("O'Brien & Co", user.getUsername());
        assertTrue(user.isEnabled());
        assertEquals(60, user.getEffectiveDailyLimitMinutes());
        assertEquals(60L * 60L + 900L, user.getEffectiveAllowedSecondsDaily());
    }

    @Test
    public void newFieldsAreUsedWhenPresent() {
        LimitedUserStatus user = parse(OLD_PLUGIN.replace("\"TimeOutUntil\"", "\"EffectiveDailyLimitMinutes\":30,\"IsEnabled\":false,\"TimeOutUntil\""));
        assertEquals(30, user.getEffectiveDailyLimitMinutes());
        assertFalse(user.isEnabled());
        assertEquals(30L * 60L + 900L, user.getEffectiveAllowedSecondsDaily());
    }

    @Test
    public void noDailyLimitMeansZeroAllowedSeconds() {
        LimitedUserStatus user = parse(OLD_PLUGIN.replace("\"TimeOutUntil\"", "\"EffectiveDailyLimitMinutes\":0,\"TimeOutUntil\""));
        assertEquals(0, user.getEffectiveDailyLimitMinutes());
        assertEquals(0L, user.getEffectiveAllowedSecondsDaily());
    }
}
