package com.tonystarklabs.hideme;

import java.util.Calendar;

/**
 * Each calendar day has its own hours. A start time later than the end time
 * continues past midnight and finishes the next morning.
 */
public final class QuietHours {

    public static final int DEFAULT_START = 22 * 60 + 30;
    public static final int DEFAULT_END = 6 * 60;

    private QuietHours() {
    }

    public static final class Day {
        public final boolean enabled;
        public final int startMinutes;
        public final int endMinutes;

        public Day(boolean enabled, int startMinutes, int endMinutes) {
            this.enabled = enabled;
            this.startMinutes = startMinutes;
            this.endMinutes = endMinutes;
        }
    }

    public static boolean blocks(int dayOfWeek, int minuteOfDay, Day[] days) {
        if (!valid(dayOfWeek, minuteOfDay, days)) {
            return false;
        }
        if (coversClock(days[dayOfWeek], minuteOfDay)) {
            return true;
        }
        return coversMorning(days[previousDay(dayOfWeek)], minuteOfDay);
    }

    /**
     * Clock time when YouTube is next allowed, or -1 when it is not hidden
     * or the week has no open minute.
     */
    public static int allowAgainMinute(int dayOfWeek, int minuteOfDay, Day[] days) {
        if (!blocks(dayOfWeek, minuteOfDay, days)) {
            return -1;
        }
        int day = dayOfWeek;
        int minute = minuteOfDay;
        for (int step = 0; step < 8 * 1440; step++) {
            minute++;
            if (minute >= 1440) {
                minute = 0;
                day = nextDay(day);
            }
            if (!blocks(day, minute, days)) {
                return minute;
            }
        }
        return -1;
    }

    private static boolean valid(int dayOfWeek, int minuteOfDay, Day[] days) {
        return days != null
                && days.length > Calendar.SATURDAY
                && dayOfWeek >= Calendar.SUNDAY
                && dayOfWeek <= Calendar.SATURDAY
                && minuteOfDay >= 0
                && minuteOfDay <= 1439;
    }

    private static boolean coversClock(Day day, int minute) {
        if (!usable(day)) {
            return false;
        }
        if (day.startMinutes < day.endMinutes) {
            return minute >= day.startMinutes && minute < day.endMinutes;
        }
        return minute >= day.startMinutes;
    }

    private static boolean coversMorning(Day previous, int minute) {
        if (!usable(previous) || previous.startMinutes < previous.endMinutes) {
            return false;
        }
        return minute < previous.endMinutes;
    }

    private static boolean usable(Day day) {
        return day != null
                && day.enabled
                && day.startMinutes != day.endMinutes
                && day.startMinutes >= 0
                && day.endMinutes >= 0
                && day.startMinutes <= 1439
                && day.endMinutes <= 1439;
    }

    private static int previousDay(int dayOfWeek) {
        if (dayOfWeek == Calendar.SUNDAY) {
            return Calendar.SATURDAY;
        }
        return dayOfWeek - 1;
    }

    private static int nextDay(int dayOfWeek) {
        if (dayOfWeek == Calendar.SATURDAY) {
            return Calendar.SUNDAY;
        }
        return dayOfWeek + 1;
    }
}
