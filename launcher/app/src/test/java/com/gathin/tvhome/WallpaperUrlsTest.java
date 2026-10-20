package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class WallpaperUrlsTest {

    @Test
    public void parseKeepsHttpsAndHttpOnePerLineDropsJunk() {
        assertEquals(Arrays.asList(
                        "https://example.com/a.jpg",
                        "http://192.168.1.10/wall.png"),
                WallpaperUrls.parseForTest(
                        "https://example.com/a.jpg\n"
                                + "not-a-url\n"
                                + "http://192.168.1.10/wall.png\n"
                                + "ftp://nope/x.jpg\n"
                                + "https://example.com/a.jpg\n"));
        assertTrue(WallpaperUrls.parseForTest("").isEmpty());
        assertTrue(WallpaperUrls.parseForTest(null).isEmpty());
    }

    @Test
    public void parseCapsAtSixUrls() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append("https://example.com/").append(i).append(".jpg\n");
        assertEquals(6, WallpaperUrls.parseForTest(sb.toString()).size());
    }

    @Test
    public void validRejectsSpacesAndNonHttp() {
        assertTrue(WallpaperUrls.valid("https://a/b.jpg"));
        assertFalse(WallpaperUrls.valid("https://a /b.jpg"));
        assertFalse(WallpaperUrls.valid("file:///tmp/a.jpg"));
        assertFalse(WallpaperUrls.valid(null));
    }

    @Test
    public void parseEmptyLinesIgnored() {
        assertEquals(Collections.singletonList("https://x/y.webp"),
                WallpaperUrls.parseForTest("\n  \nhttps://x/y.webp\n"));
    }
}
