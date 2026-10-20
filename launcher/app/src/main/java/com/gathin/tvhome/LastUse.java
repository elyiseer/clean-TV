package com.gathin.tvhome;

import android.content.Context;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * When the TV was last turned off, for the greeting intro's "Last TV usage" line. Recorded on SCREEN_OFF by
 * Home and, when it is on, by the Input button service, which stays alive while other apps are in front.
 */
final class LastUse {

    private static final String FILE = "last_use";
    private static final String PREF = "last_screen_off";

    private LastUse() {
    }

    static void record(Context c) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putLong(PREF, System.currentTimeMillis()).apply();
    }

    /** "Last TV usage: 1:25 AM 20 OCT" (in the TV's language and clock format), or "" before the first record. */
    static String line(Context c) {
        long at = c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(PREF, 0);
        if (at == 0) return "";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(),
                DateFormat.is24HourFormat(c) ? "Hmm dMMM" : "hmma dMMM");
        return c.getString(R.string.last_tv_usage,
                new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(at)).toUpperCase(Locale.getDefault()));
    }
}
