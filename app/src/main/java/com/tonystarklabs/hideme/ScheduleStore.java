package com.tonystarklabs.hideme;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

public final class ScheduleStore {

    private static final String PREFS = "quiet_hours";
    private static final String KEY_START = "start_minutes";
    private static final String KEY_END = "end_minutes";
    private static final String KEY_EVENINGS = "evenings";
    private static final int LAST_MINUTE = 23 * 60 + 59;

    private final SharedPreferences prefs;

    public ScheduleStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        migrateSharedHours();
    }

    public QuietHours.Day day(int calendarDay) {
        boolean weekday = calendarDay >= Calendar.MONDAY && calendarDay <= Calendar.FRIDAY;
        boolean enabled = prefs.getBoolean(enabledKey(calendarDay), weekday);
        int start = clamp(prefs.getInt(startKey(calendarDay), QuietHours.DEFAULT_START));
        int end = clamp(prefs.getInt(endKey(calendarDay), QuietHours.DEFAULT_END));
        return new QuietHours.Day(enabled, start, end);
    }

    public QuietHours.Day[] week() {
        QuietHours.Day[] days = new QuietHours.Day[8];
        for (int day = Calendar.SUNDAY; day <= Calendar.SATURDAY; day++) {
            days[day] = day(day);
        }
        return days;
    }

    public void setEnabled(int calendarDay, boolean enabled) {
        prefs.edit().putBoolean(enabledKey(calendarDay), enabled).apply();
    }

    public void setStart(int calendarDay, int minutes) {
        prefs.edit().putInt(startKey(calendarDay), clamp(minutes)).apply();
    }

    public void setEnd(int calendarDay, int minutes) {
        prefs.edit().putInt(endKey(calendarDay), clamp(minutes)).apply();
    }

    public boolean blocksNow() {
        Calendar now = Calendar.getInstance();
        return QuietHours.blocks(
                now.get(Calendar.DAY_OF_WEEK),
                minuteOfDay(now),
                week());
    }

    public int allowAgainMinute() {
        Calendar now = Calendar.getInstance();
        return QuietHours.allowAgainMinute(
                now.get(Calendar.DAY_OF_WEEK),
                minuteOfDay(now),
                week());
    }

    public static String formatMinutes(Context context, int minutes) {
        int safe = clamp(minutes);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, safe / 60);
        calendar.set(Calendar.MINUTE, safe % 60);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return android.text.format.DateFormat.getTimeFormat(context).format(calendar.getTime());
    }

    private void migrateSharedHours() {
        if (prefs.contains(startKey(Calendar.MONDAY))) {
            return;
        }
        int start = clamp(prefs.getInt(KEY_START, QuietHours.DEFAULT_START));
        int end = clamp(prefs.getInt(KEY_END, QuietHours.DEFAULT_END));
        int mask = prefs.getInt(KEY_EVENINGS, weekdayMask());
        SharedPreferences.Editor editor = prefs.edit();
        for (int day = Calendar.SUNDAY; day <= Calendar.SATURDAY; day++) {
            editor.putInt(startKey(day), start);
            editor.putInt(endKey(day), end);
            editor.putBoolean(enabledKey(day), (mask & (1 << day)) != 0);
        }
        editor.remove(KEY_START).remove(KEY_END).remove(KEY_EVENINGS).apply();
    }

    private static int weekdayMask() {
        int mask = 0;
        for (int day = Calendar.MONDAY; day <= Calendar.FRIDAY; day++) {
            mask |= 1 << day;
        }
        return mask;
    }

    private static int minuteOfDay(Calendar calendar) {
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    }

    private static String enabledKey(int calendarDay) {
        return "day_" + calendarDay + "_enabled";
    }

    private static String startKey(int calendarDay) {
        return "day_" + calendarDay + "_start";
    }

    private static String endKey(int calendarDay) {
        return "day_" + calendarDay + "_end";
    }

    private static int clamp(int minutes) {
        if (minutes < 0) {
            return 0;
        }
        if (minutes > LAST_MINUTE) {
            return LAST_MINUTE;
        }
        return minutes;
    }
}
