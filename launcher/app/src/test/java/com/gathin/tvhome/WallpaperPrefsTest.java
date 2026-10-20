package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WallpaperPrefsTest {

    @Test
    public void clampRotationSecondsBounds() {
        assertEquals(0, Config.clampRotationSeconds(-5));
        assertEquals(0, Config.clampRotationSeconds(0));
        assertEquals(5, Config.clampRotationSeconds(5));
        assertEquals(30 * 60, Config.clampRotationSeconds(30 * 60));
        assertEquals(Config.MAX_ROTATION_SECONDS, Config.clampRotationSeconds(Config.MAX_ROTATION_SECONDS));
        assertEquals(Config.MAX_ROTATION_SECONDS, Config.clampRotationSeconds(999_999));
    }

    @Test
    public void clampDockGlassOpacityBounds() {
        assertEquals(10, Config.clampDockGlassOpacity(0));
        assertEquals(10, Config.clampDockGlassOpacity(5));
        assertEquals(55, Config.clampDockGlassOpacity(55));
        assertEquals(90, Config.clampDockGlassOpacity(90));
        assertEquals(90, Config.clampDockGlassOpacity(200));
    }

    @Test
    public void normalizeWallpaperTransition() {
        assertEquals(Config.TRANSITION_NONE, Config.normalizeWallpaperTransition(null));
        assertEquals(Config.TRANSITION_NONE, Config.normalizeWallpaperTransition(""));
        assertEquals(Config.TRANSITION_NONE, Config.normalizeWallpaperTransition("wipe"));
        assertEquals(Config.TRANSITION_NONE, Config.normalizeWallpaperTransition("fade_black"));
        assertEquals(Config.TRANSITION_CROSSFADE, Config.normalizeWallpaperTransition("crossfade"));
    }

    @Test
    public void isCachedWallpaperChoiceRecognizesSources() {
        assertTrue(Config.isCachedWallpaperChoice("url"));
        assertTrue(Config.isCachedWallpaperChoice("bing"));
        assertFalse(Config.isCachedWallpaperChoice("google"));
        assertFalse(Config.isCachedWallpaperChoice("picsum"));
        assertFalse(Config.isCachedWallpaperChoice("none"));
        assertFalse(Config.isCachedWallpaperChoice("wallpaper.jpg"));
        assertFalse(Config.isCachedWallpaperChoice(null));
    }
}
