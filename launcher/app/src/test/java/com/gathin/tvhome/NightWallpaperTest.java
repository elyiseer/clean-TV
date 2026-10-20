package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NightWallpaperTest {
    @Test public void clockReadsHoursAndMinutes() {
        assertEquals(18 * 60, NightWallpaper.clock("18:00", 0));
        assertEquals(6 * 60 + 30, NightWallpaper.clock("6:30", 0));
        assertEquals(23 * 60 + 59, NightWallpaper.clock(" 23:59 ", 0));
    }

    @Test public void badClockKeepsTheFallback() {
        assertEquals(42, NightWallpaper.clock("24:00", 42));
        assertEquals(42, NightWallpaper.clock("12:60", 42));
        assertEquals(42, NightWallpaper.clock("7pm", 42));
        assertEquals(42, NightWallpaper.clock("", 42));
        assertEquals(42, NightWallpaper.clock(null, 42));
    }

    @Test public void defaultWindowWrapsMidnight() {
        int from = NightWallpaper.DEFAULT_FROM, until = NightWallpaper.DEFAULT_UNTIL;
        assertFalse(NightWallpaper.isNight(17 * 60 + 59, from, until));
        assertTrue(NightWallpaper.isNight(18 * 60, from, until));
        assertTrue(NightWallpaper.isNight(0, from, until));
        assertTrue(NightWallpaper.isNight(5 * 60 + 59, from, until));
        assertFalse(NightWallpaper.isNight(6 * 60, from, until));
        assertFalse(NightWallpaper.isNight(12 * 60, from, until));
    }

    @Test public void windowWithinOneDay() {
        assertTrue(NightWallpaper.isNight(13 * 60, 12 * 60, 14 * 60));
        assertFalse(NightWallpaper.isNight(14 * 60, 12 * 60, 14 * 60));
        assertFalse(NightWallpaper.isNight(11 * 60, 12 * 60, 14 * 60));
    }

    @Test public void equalEndsMeanNoNight() {
        assertFalse(NightWallpaper.isNight(20 * 60, 20 * 60, 20 * 60));
    }
}
