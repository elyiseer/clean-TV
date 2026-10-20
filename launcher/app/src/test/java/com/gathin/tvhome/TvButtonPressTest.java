package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class TvButtonPressTest {
    @Test public void launchesOnlyOnceAtRelease() {
        TvButtonPress press = new TvButtonPress();
        assertFalse(press.update(true, 0, false));
        assertFalse(press.update(true, 1, false));
        assertTrue(press.update(false, 0, false));
        assertFalse(press.update(false, 0, false));
    }
    @Test public void cancelledOrOrphanReleaseDoesNotLaunch() {
        TvButtonPress press = new TvButtonPress();
        assertFalse(press.update(false, 0, false));
        press.update(true, 0, false);
        assertFalse(press.update(false, 0, true));
        assertFalse(press.update(false, 0, false));
    }
}
