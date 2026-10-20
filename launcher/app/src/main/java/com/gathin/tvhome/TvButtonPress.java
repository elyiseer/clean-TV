package com.gathin.tvhome;

/** One launch for each complete, uncancelled physical press. */
final class TvButtonPress {
    private boolean armed;
    boolean update(boolean down, int repeats, boolean cancelled) {
        if (down) {
            if (repeats == 0) armed = true;
            return false;
        }
        boolean launch = armed && !cancelled;
        armed = false;
        return launch;
    }
}
