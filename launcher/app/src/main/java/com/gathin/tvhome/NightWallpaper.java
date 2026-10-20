package com.gathin.tvhome;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * When a theme's wallpaper_night.jpg replaces wallpaper.jpg: from night_from to night_until, as "HH:MM" in
 * config.json, 18:00 to 06:00 by default. Pure maths on minutes after midnight, like Sky, so it is unit-tested.
 */
final class NightWallpaper {
    private NightWallpaper() { }

    static final String FILE_NAME = "wallpaper_night.jpg";
    static final int DEFAULT_FROM = 18 * 60;
    static final int DEFAULT_UNTIL = 6 * 60;

    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})");

    /** "HH:MM" as minutes after midnight; anything else (empty, 24:00, 7pm) keeps fallback. */
    static int clock(String hhmm, int fallback) {
        if (hhmm == null) return fallback;
        Matcher m = CLOCK.matcher(hhmm.trim());
        if (!m.matches()) return fallback;
        int h = Integer.parseInt(m.group(1)), min = Integer.parseInt(m.group(2));
        return h < 24 && min < 60 ? h * 60 + min : fallback;
    }

    /** Whether minute (after midnight) falls in [from, until); a window across midnight wraps. Equal ends: never. */
    static boolean isNight(int minute, int from, int until) {
        return from > until ? minute >= from || minute < until : minute >= from && minute < until;
    }
}
