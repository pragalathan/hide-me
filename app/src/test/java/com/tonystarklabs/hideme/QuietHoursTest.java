package com.tonystarklabs.hideme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import org.junit.Test;

public class QuietHoursTest {

    private static final int START = QuietHours.DEFAULT_START;
    private static final int END = QuietHours.DEFAULT_END;

    @Test
    public void mondayEveningIsHidden() {
        QuietHours.Day[] days = weekdayNights();
        assertFalse(blocks(days, Calendar.MONDAY, 22 * 60 + 29));
        assertTrue(blocks(days, Calendar.MONDAY, 22 * 60 + 30));
        assertTrue(blocks(days, Calendar.MONDAY, 23 * 60 + 15));
        assertEquals(END, QuietHours.allowAgainMinute(Calendar.MONDAY, 23 * 60, days));
    }

    @Test
    public void overnightContinuesUntilSix() {
        QuietHours.Day[] days = weekdayNights();
        assertTrue(blocks(days, Calendar.TUESDAY, 0));
        assertTrue(blocks(days, Calendar.TUESDAY, 5 * 60 + 59));
        assertFalse(blocks(days, Calendar.TUESDAY, 6 * 60));
        assertFalse(blocks(days, Calendar.TUESDAY, 15 * 60));
        assertEquals(END, QuietHours.allowAgainMinute(Calendar.TUESDAY, 60, days));
    }

    @Test
    public void fridayNightIncludesSaturdayMorning() {
        QuietHours.Day[] days = weekdayNights();
        assertTrue(blocks(days, Calendar.FRIDAY, 23 * 60));
        assertTrue(blocks(days, Calendar.SATURDAY, 60));
        assertFalse(blocks(days, Calendar.SATURDAY, 6 * 60));
        assertFalse(blocks(days, Calendar.SATURDAY, START));
    }

    @Test
    public void sundayNightStaysOpen() {
        QuietHours.Day[] days = weekdayNights();
        assertFalse(blocks(days, Calendar.SUNDAY, 23 * 60));
        assertFalse(blocks(days, Calendar.MONDAY, 60));
    }

    @Test
    public void eachDayKeepsItsOwnHours() {
        QuietHours.Day[] days = blankWeek();
        days[Calendar.MONDAY] = new QuietHours.Day(true, 22 * 60, 6 * 60);
        days[Calendar.WEDNESDAY] = new QuietHours.Day(true, 9 * 60, 12 * 60);
        days[Calendar.SATURDAY] = new QuietHours.Day(true, 15 * 60, 18 * 60);

        assertTrue(blocks(days, Calendar.MONDAY, 23 * 60));
        assertTrue(blocks(days, Calendar.TUESDAY, 60));
        assertFalse(blocks(days, Calendar.TUESDAY, 10 * 60));
        assertTrue(blocks(days, Calendar.WEDNESDAY, 10 * 60));
        assertFalse(blocks(days, Calendar.WEDNESDAY, 22 * 60 + 30));
        assertTrue(blocks(days, Calendar.SATURDAY, 16 * 60));
        assertFalse(blocks(days, Calendar.SATURDAY, 22 * 60));
        assertEquals(12 * 60, QuietHours.allowAgainMinute(Calendar.WEDNESDAY, 10 * 60, days));
        assertEquals(18 * 60, QuietHours.allowAgainMinute(Calendar.SATURDAY, 16 * 60, days));
    }

    @Test
    public void laterDayExtendsTheMorning() {
        QuietHours.Day[] days = blankWeek();
        days[Calendar.MONDAY] = new QuietHours.Day(true, 22 * 60 + 30, 6 * 60);
        days[Calendar.TUESDAY] = new QuietHours.Day(true, 0, 8 * 60);

        assertEquals(8 * 60, QuietHours.allowAgainMinute(Calendar.MONDAY, 23 * 60, days));
        assertEquals(8 * 60, QuietHours.allowAgainMinute(Calendar.TUESDAY, 60, days));
    }

    @Test
    public void equalTimesNeverHide() {
        QuietHours.Day[] days = blankWeek();
        days[Calendar.MONDAY] = new QuietHours.Day(true, START, START);
        assertFalse(blocks(days, Calendar.MONDAY, 23 * 60));
        assertEquals(-1, QuietHours.allowAgainMinute(Calendar.MONDAY, 23 * 60, days));
    }

    private static boolean blocks(QuietHours.Day[] days, int day, int minute) {
        return QuietHours.blocks(day, minute, days);
    }

    private static QuietHours.Day[] weekdayNights() {
        QuietHours.Day[] days = new QuietHours.Day[8];
        for (int day = Calendar.SUNDAY; day <= Calendar.SATURDAY; day++) {
            boolean weekday = day >= Calendar.MONDAY && day <= Calendar.FRIDAY;
            days[day] = new QuietHours.Day(weekday, START, END);
        }
        return days;
    }

    private static QuietHours.Day[] blankWeek() {
        QuietHours.Day[] days = new QuietHours.Day[8];
        for (int day = Calendar.SUNDAY; day <= Calendar.SATURDAY; day++) {
            days[day] = new QuietHours.Day(false, 0, 0);
        }
        return days;
    }
}
